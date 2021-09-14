package org.jsl.concurrentfileupload.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import org.jsl.concurrentfileupload.flowcontrol.BackPressure;
import org.jsl.concurrentfileupload.flowcontrol.RateLimiterAndBacklogBackPressure;
import org.jsl.concurrentfileupload.threadpool.ChannelEventRunnable;
import org.jsl.concurrentfileupload.threadpool.ChannelSerializaionThreadPoolExecutor;
import org.jsl.concurrentfileupload.threadpool.ChannelSerializationWorkQueue;
import org.jsl.concurrentfileupload.threadpool.NamedThreadFactory;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * achieve read data flow control by back pressure,the theory is by netty's autoRead and ChannelOutboundHandler's read() method.
 *
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class HttpMultiPartUploadChannelHandler extends ChannelDuplexHandler {

    private NioSocketChannel clientChannel;

    private ChannelSerializaionThreadPoolExecutor executor;

    private BackPressure backPressure;

    private boolean removeCumulatorContenLength;

    private boolean beginRead;

    public HttpMultiPartUploadChannelHandler(NioSocketChannel clientChannel) {
        this(clientChannel, 0, 0);
    }

    public HttpMultiPartUploadChannelHandler(NioSocketChannel clientChannel, int readBufferLowWaterMark, int readBufferHighWaterMark) {
        ChannelSerializationWorkQueue workQueue = new ChannelSerializationWorkQueue();
        // TODO: 2021/08/31 1、thread pool parameters can be get from java -D parameters
        executor = new ChannelSerializaionThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 200, 10, TimeUnit.SECONDS, workQueue, new NamedThreadFactory("channelserialize"),
                new ThreadPoolExecutor.DiscardPolicy());
        //bind executor to work queue.
        workQueue.setBindExecutor(executor);
        this.clientChannel = clientChannel;
        backPressure = new RateLimiterAndBacklogBackPressure(clientChannel, executor);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            //1.validate http header content-length
            String contentLengthStr = HttpUtil.getRequestHeader(request, "content-length");
            int contentLength = 0;
            try {
                contentLength = Integer.parseInt(contentLengthStr);
            } catch (NumberFormatException e) {
            }
            if (contentLength <= 0) {
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                ctx.writeAndFlush(response);
                ctx.channel().close();
            }
            //2.save the content-length to the channel
            AttributeConstants.setAttrValue(ctx.channel(), AttributeConstants.UPLOAD_CONTENT_LENGTH, contentLength);
            //adjust AdaptiveRecvByteBufAllocator's maxmum byte size of ByteBuf in every channelRead.
            //new AdaptiveRecvByteBufAllocator();
            beginRead = true;
            backPressure.addChannelCumulatorContentLength(contentLength);
            String filename = HttpUtil.getRequestHeader(request, "filename");
            String generateName = null;
            if (filename == null || "".equals(filename)) {
                generateName = UUID.randomUUID().toString() + ".pdf";
            }
            AttributeConstants.setAttrValue(ctx.channel(), AttributeConstants.GENERATE_FILE_NAME, generateName);
        } else {
            byte[] bytes = null;
            HttpContent httpContent = null;
            if (msg instanceof LastHttpContent) {
                //handle last request body
                //DefaultHttpContent implements HttpContent,so handle LastHttpContent before HttpContent
                httpContent = (LastHttpContent) msg;
                //读取完成所有请求后，恢复200响应，并关闭连接
                ByteBufAllocator allocator = ctx.channel().config().getAllocator();
                ByteBuf buffer = allocator.buffer();
                buffer.writeBytes("upload success".getBytes());
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                ctx.channel().writeAndFlush(response);
                ctx.channel().close();
            } else if (msg instanceof HttpContent) {
                //handle non last request body
                httpContent = (HttpContent) msg;
                //2021/09/03 should achieve the flow control by upload rate(上传速率) and message backlog(消息积压) and  back pressure(回压).
                backPressure.openBackPressureIfNecessary(AttributeConstants.getAttrValue(clientChannel, AttributeConstants.UPLOAD_CONTENT_LENGTH), httpContent.content());
            }
            int byteLen = httpContent.content().readableBytes();
            if (byteLen > 0) {
                bytes = new byte[byteLen];
                httpContent.content().readBytes(bytes);
                ChannelEventRunnable task = createChannelEventRunnable(clientChannel, bytes, AttributeConstants.getAttrValue(ctx.channel(), AttributeConstants.UPLOAD_CONTENT_LENGTH),
                        AttributeConstants.getAttrValue(ctx.channel(), AttributeConstants.GENERATE_FILE_NAME));
                executor.execute(task);
            }
        }
        //continue the flow in the channelpipeline
        ctx.fireChannelRead(msg);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        //客户端主动关闭连接、OutOfMemoryError、IOException不会触发ChannelOutboundHandler的close()方法回调。但是会触发inactive和unregistered
        //io.netty.channel.nio.AbstractNioByteChannel.NioByteUnsafe.read()
        //io.netty.channel.nio.AbstractNioByteChannel.NioByteUnsafe.handleReadException()
        if (!removeCumulatorContenLength && beginRead) {
            backPressure.removeChannelCumulatorContentLength(AttributeConstants.getAttrValue(ctx.channel(), AttributeConstants.UPLOAD_CONTENT_LENGTH));
            removeCumulatorContenLength = true;
        }
        ctx.close();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        //客户端主动关闭连接、OutOfMemoryError、IOException不会触发ChannelOutboundHandler的close()方法回调。但是会触发inactive和unregistered
        //为了快速清除回压的连接数和上传总字节数，在Unregistered中清除一次。
        if (!removeCumulatorContenLength && beginRead) {
            backPressure.removeChannelCumulatorContentLength(AttributeConstants.getAttrValue(ctx.channel(), AttributeConstants.UPLOAD_CONTENT_LENGTH));
            removeCumulatorContenLength = true;
        }
    }

    private ChannelEventRunnable createChannelEventRunnable(Channel channel, byte[] bytes, int totalByteSize, String fileName) {
        ChannelEventRunnable channelEventRunnable = new ChannelEventRunnable(channel, bytes, totalByteSize, fileName);
        return channelEventRunnable;
    }

    public static class A implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

        }
    }


}
