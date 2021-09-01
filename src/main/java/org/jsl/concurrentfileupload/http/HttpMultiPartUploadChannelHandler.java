package org.jsl.concurrentfileupload.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import org.jsl.concurrentfileupload.flowcontrol.BackPressure;
import org.jsl.concurrentfileupload.flowcontrol.FlowControlAssistant;
import org.jsl.concurrentfileupload.threadpool.ChannelEventRunnable;
import org.jsl.concurrentfileupload.threadpool.ChannelSerializaionThreadPoolExecutor;
import org.jsl.concurrentfileupload.threadpool.ChannelSerializationWorkQueue;
import org.jsl.concurrentfileupload.threadpool.NamedThreadFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * achieve read data flow control by back pressure,the theory is by netty's autoRead and ChannelOutboundHandler's read() method.
 *
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class HttpMultiPartUploadChannelHandler extends ChannelDuplexHandler implements BackPressure {

    private ChannelSerializaionThreadPoolExecutor executor;

    private NioSocketChannel clientChannel;

    private int readBufferLowWaterMark;

    private int readBufferHighWaterMark;

    private AtomicBoolean readFlowControlOpen = new AtomicBoolean(false);


    public HttpMultiPartUploadChannelHandler(NioSocketChannel clientChannel) {
        this(clientChannel, 0, 0);
    }

    public HttpMultiPartUploadChannelHandler(NioSocketChannel clientChannel, int readBufferLowWaterMark, int readBufferHighWaterMark) {
        ChannelSerializationWorkQueue workQueue = new ChannelSerializationWorkQueue();
        // TODO: 2021/08/31 1、thread pool parameters can be get from java -D parameters
        executor = new ChannelSerializaionThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 200, 10, TimeUnit.SECONDS, workQueue, new NamedThreadFactory("channelserialization"),
                new ThreadPoolExecutor.DiscardPolicy());
        //bind executor to work queue.
        workQueue.setBindExecutor(executor);
        this.clientChannel = clientChannel;
        readFlowControlOpen.set(clientChannel.config().isAutoRead());
        this.readBufferLowWaterMark = readBufferLowWaterMark;
        this.readBufferHighWaterMark = readBufferHighWaterMark;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            Attribute<NettyHttpServer> attr = ctx.channel().attr(AttributeConstants.SERVER);
            NettyHttpServer nettyServer = attr.get();

        } else if (msg instanceof LastHttpContent) {
            //handle last request body
            //DefaultHttpContent implements HttpContent,so handle LastHttpContent before HttpContent
            LastHttpContent lastHttpContent = (LastHttpContent) msg;
            executor.execute(createChannelEventRunnable(clientChannel, lastHttpContent.content()));
            openBackPressure();
        } else if (msg instanceof HttpContent) {
            //handle non last request body
            HttpContent httpContent = (HttpContent) msg;
            executor.execute(createChannelEventRunnable(clientChannel, httpContent.content()));
            openBackPressure();
        }
        //continue the flow in the channelpipeline
        super.channelRead(ctx, msg);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        //setAutoRead(true)，will not remove current channel's OP_READ event from Selector object
        ctx.channel().config().setAutoRead(true);
        //HeadContext invoke unsafe.read() will  register current channel's OP_READ event to Selector object
        ctx.read();
    }

    public ChannelEventRunnable createChannelEventRunnable(Channel channel, ByteBuf byteBuf) {
        ChannelEventRunnable channelEventRunnable = new ChannelEventRunnable(channel, byteBuf);
        return channelEventRunnable;
    }

    @Override
    public boolean isReadFlowControlOpen() {
        return readFlowControlOpen.get();
    }

    @Override
    public boolean isWriteFlowControlOpen() {
        return false;
    }

    @Override
    public boolean shouldCloseReadFlowControl() {
        return shouldBackPressureOpen();
    }

    @Override
    public boolean shouldCloseWriteFlowControl() {
        return true;
    }

    /**
     * {@link HttpMultiPartUploadChannelHandler#read}
     *
     * @return
     */
    @Override
    public boolean closeReadFlowControl() {
        if (readFlowControlOpen.compareAndSet(true, false)) {
            clientChannel.pipeline().read();
        }
        return !readFlowControlOpen.get();
    }

    @Override
    public boolean closeWriteFlowControl() {
        throw new RuntimeException("not support closeWriteFlowControl!");
    }

    @Override
    public int getReadBufferLowWaterMark() {
        return readBufferLowWaterMark;
    }

    @Override
    public int getReadBufferHighWaterMark() {
        return readBufferHighWaterMark;
    }

    @Override
    public boolean shouldBackPressureOpen() {
        int channelPendingByteSize = executor.getChannelPendingByteSize(clientChannel);
        if (readBufferHighWaterMark > 0 && channelPendingByteSize >= readBufferHighWaterMark) {
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldBackPressureClose() {
        int channelPendingByteSize = executor.getChannelPendingByteSize(clientChannel);
        if (readBufferLowWaterMark > 0 && channelPendingByteSize <= readBufferLowWaterMark) {
            return true;
        }
        return false;
    }

    @Override
    public void openBackPressure() {
        if (shouldBackPressureOpen()) {
            //read flow control is opened
            if (readFlowControlOpen.compareAndSet(false, true)) {
                FlowControlAssistant flowControlAssistant = FlowControlAssistant.createFlowControlAssistant();
                if (flowControlAssistant == null) {
                    readFlowControlOpen.set(false);
                    throw new RuntimeException("Get FlowControlAssistant Error");
                }
                clientChannel.config().setAutoRead(false);
                flowControlAssistant.closeFlowControl(this);
            }
        }
    }

    @Override
    public void closeBackPressure() {

    }

    public static class A implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

        }
    }


}
