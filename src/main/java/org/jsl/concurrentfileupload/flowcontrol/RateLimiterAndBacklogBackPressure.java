package org.jsl.concurrentfileupload.flowcontrol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.jsl.concurrentfileupload.threadpool.ChannelSerializaionThreadPoolExecutor;
import org.jsl.concurrentfileupload.threadpool.NamedThreadFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * not thread safe
 *
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/09/09
 */
public class RateLimiterAndBacklogBackPressure implements BackPressure {

    //目前正在上传的所有连接的总字节数(目前实现单机版，可以利用redis实现集群版)
    private static final LongAdder UPLOADING_TOTAL_BYTES = new LongAdder();
    //目前正在上传的总连接数(目前实现单机版，可以利用redis实现集群版)
    private static final LongAdder UPLOADING_TOTAL_CHANNEL_COUNT = new LongAdder();
    //机房最大带宽，即允许的总带宽(目前实现单机版，可以利用redis实现集群版)
    private static long MAX_BPS = 10 * 1024 * 1024;

    //当前连接channel
    private NioSocketChannel clientChannel;
    //缓冲所在的线程池
    private ChannelSerializaionThreadPoolExecutor executor;
    //读缓冲的低水位
    private long readBufferLowWaterMark;
    //读缓冲的写水位
    private long readBufferHighWaterMark;
    //上一秒的读取时间
    private long lastSecondReadTime;
    //上一秒读取的字节数
    private long lastSecondReadBytes;
    //当上传文件字节数超过这个数值，才判断是否需要开启回压,小于该字节数，一直不开启回压,默认10MB
    private long enableBackPressureContentLength;
    //上传速率的限制策略
    private InboundRateLimitStrategy inboundRateLimitStrategy;
    //是否开启了回压
    private AtomicBoolean openBackPressure = new AtomicBoolean(false);
    //时间轮
    private HashedWheelTimer timer = new HashedWheelTimer(new NamedThreadFactory("backpressure", true), 20, TimeUnit.MILLISECONDS, 512);
    private long backPressureTimeMilis;


    public RateLimiterAndBacklogBackPressure(NioSocketChannel clientChannel, ChannelSerializaionThreadPoolExecutor executor) {
        this(clientChannel, executor, 10 * 1024 * 1024, InboundRateLimitStrategy.AVERAGE, 32 * 1024, 64 * 1024);
    }

    public RateLimiterAndBacklogBackPressure(NioSocketChannel clientChannel, ChannelSerializaionThreadPoolExecutor executor, long enableBackPressureContentLength,
                                             InboundRateLimitStrategy inboundRateLimitStrategy,
                                             long
                                                     readBufferLowWaterMark, long
                                                     readBufferHighWaterMark) {
        this.clientChannel = clientChannel;
        this.executor = executor;
        this.inboundRateLimitStrategy = inboundRateLimitStrategy;
        this.readBufferLowWaterMark = readBufferLowWaterMark;
        this.readBufferHighWaterMark = readBufferHighWaterMark;
        this.enableBackPressureContentLength = enableBackPressureContentLength;
    }


    @Override
    public long getReadBufferLowWaterMark() {
        return this.readBufferLowWaterMark;
    }

    @Override
    public long getReadBufferHighWaterMark() {
        return this.readBufferHighWaterMark;
    }

    @Override
    public boolean shouldBackPressureOpen() {
        if (executor != null) {
            int channelPendingByteSize = executor.getChannelPendingByteSize(clientChannel);
            if (readBufferHighWaterMark > 0 && channelPendingByteSize >= readBufferHighWaterMark) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldBackPressureClose() {
        if (executor != null) {
            int channelPendingByteSize = executor.getChannelPendingByteSize(clientChannel);
            if (readBufferLowWaterMark > 0 && channelPendingByteSize <= readBufferLowWaterMark) {
                return true;
            }
        }
        return false;
    }


    @Override
    public boolean closeBackPressure() {
        if (openBackPressure.compareAndSet(true, false)) {
            //setAutoRead(true)，will not remove current channel's OP_READ event from Selector object
            clientChannel.config().setAutoRead(true);
            //HeadContext invoke unsafe.read() will  register current channel's OP_READ event to Selector object
            //重新注册读事件监听
            clientChannel.read();
        }
        return !openBackPressure.get();
    }

    @Override
    public boolean isBackPressureOpen() {
        return openBackPressure.get();
    }

    @Override
    public void openBackPressureIfNecessary(long contentLength, ByteBuf message) {
        long stopTimeMills = shouldRateLimited(contentLength, message);
        if (stopTimeMills > 0) {
            doOpenBackPressure(stopTimeMills);
            return;
        }
        if (shouldBackPressureOpen()) {
            doOpenBackPressure(100);
        }
    }

    @Override
    public void addChannelCumulatorContentLength(long contentLength) {
        UPLOADING_TOTAL_BYTES.add(contentLength);
        UPLOADING_TOTAL_CHANNEL_COUNT.increment();
    }

    @Override
    public void removeChannelCumulatorContentLength(long contentLength) {
        UPLOADING_TOTAL_BYTES.add(-contentLength);
        UPLOADING_TOTAL_CHANNEL_COUNT.decrement();
    }

    protected void doOpenBackPressure(long backPressureTimeMills) {
        if (openBackPressure.compareAndSet(false, true)) {
            clientChannel.config().setAutoRead(false);
            this.backPressureTimeMilis = backPressureTimeMills;
            closeFlowControlInFuture(this, backPressureTimeMills);
        }
    }

    private long shouldRateLimited(long contentLength, ByteBuf message) {
        if (message != null) {
            lastSecondReadBytes += message.readableBytes();
            long now = System.currentTimeMillis();
            if (lastSecondReadTime <= 0) {
                lastSecondReadTime = now;
            } else if (now - lastSecondReadTime >= 1000) {
                long currentBPS = lastSecondReadBytes / (now - lastSecondReadTime) * 1000;
                lastSecondReadTime = now;
                lastSecondReadBytes = 0;
                long currentChannelMaxBPS = getCurrentChannelMaxBPS(contentLength);
                if (contentLength >= enableBackPressureContentLength && currentBPS > currentChannelMaxBPS) {
                    BigDecimal c1 = new BigDecimal(currentBPS);
                    BigDecimal c2 = new BigDecimal(currentChannelMaxBPS);
                    BigDecimal divide = c1.divide(c2, 3, RoundingMode.HALF_UP);
                    if (divide.doubleValue() > 1.1d) {
                        clientChannel.config().setAutoRead(false);
                        return (long) (1000 * (divide.doubleValue() - 1.0));
                    }
                }
            }
        }
        return 0;
    }

    private long getCurrentChannelMaxBPS(long contentLength) {
        if (inboundRateLimitStrategy.equals(InboundRateLimitStrategy.AVERAGE)) {
            //所有连接平分最大网速(默认)
            return MAX_BPS / UPLOADING_TOTAL_CHANNEL_COUNT.longValue();
        } else if (inboundRateLimitStrategy.equals(InboundRateLimitStrategy.UPLOADFILELENGTHRATE)) {
            //根据当前连接上传文件的大小占当前所有连接总上传文件大小的比例瓜分最大网速
            return MAX_BPS * contentLength / UPLOADING_TOTAL_BYTES.longValue();
        }
        throw new RuntimeException("not supported InboundRateLimitStrategy");
    }

    public void closeFlowControlInFuture(RateLimiterAndBacklogBackPressure rateLimiterAndBacklogBackPressure, long delay) {
        timer.newTimeout(new BackPressureControlTask(rateLimiterAndBacklogBackPressure), delay, TimeUnit.MILLISECONDS);
    }

    private class BackPressureControlTask implements TimerTask {

        private RateLimiterAndBacklogBackPressure rateLimiterAndBacklogBackPressure;

        public BackPressureControlTask(RateLimiterAndBacklogBackPressure rateLimiterAndBacklogBackPressure) {
            this.rateLimiterAndBacklogBackPressure = rateLimiterAndBacklogBackPressure;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            if (rateLimiterAndBacklogBackPressure == null) {
                timeout.cancel();
                return;
            }
            if (timeout.isCancelled()) {
                return;
            }
            boolean closeReadFlowControl = false;
            if (!rateLimiterAndBacklogBackPressure.isBackPressureOpen()) {
                closeReadFlowControl = true;
            } else {
                if (rateLimiterAndBacklogBackPressure.shouldBackPressureClose()) {
                    closeReadFlowControl = rateLimiterAndBacklogBackPressure.closeBackPressure();
                }
            }
            if (!closeReadFlowControl) {
                timer.newTimeout(this, rateLimiterAndBacklogBackPressure.backPressureTimeMilis, TimeUnit.MILLISECONDS);
            }
        }
    }
}
