package org.jsl.concurrentfileupload.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import org.jsl.concurrentfileupload.threadpool.ChannelSerializationThreadPoolExecutor;
import org.jsl.concurrentfileupload.threadpool.ChannelSerializationWorkQueue;
import org.jsl.concurrentfileupload.threadpool.NamedThreadFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class HttpMultiPartUploadChannelHandler extends ChannelInboundHandlerAdapter {

    private ThreadPoolExecutor executor;


    public HttpMultiPartUploadChannelHandler() {
        ChannelSerializationWorkQueue workQueue = new ChannelSerializationWorkQueue();
        // TODO: 2021/08/31 1、thread pool parameters can be get from java -D parameters 2、
        executor = new ChannelSerializationThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 200, 10, TimeUnit.SECONDS, workQueue, new NamedThreadFactory("channelserialization"),
                new ThreadPoolExecutor.DiscardPolicy());
        workQueue.setBindExecutor(executor);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            Attribute<NettyServer> attr = ctx.channel().attr(AttributeConstants.SERVER);
            NettyServer nettyServer = attr.get();

        } else if (msg instanceof LastHttpContent) {
            //handle last request body
            //DefaultHttpContent implements HttpContent,so handle LastHttpContent before HttpContent

        } else if (msg instanceof HttpContent) {
            //hanele non last request body
        }
        //continue the flow in the channelpipeline
        super.channelRead(ctx, msg);
    }


    public static class A implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

        }
    }
}
