package org.jsl.concurrentfileupload.http;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */

@ChannelHandler.Sharable
public class DynamicChannelHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpObject)) {
            //not http request,close the connection
            ctx.channel().close();
        } else {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                boolean multipartContent = HttpUtil.isMultipartContent(request);
                if (multipartContent) {
                    //add business channelhandler
                    ctx.channel().pipeline().addLast(new HttpMultiPartUploadChannelHandler((NioSocketChannel) ctx.channel(), 64 * 1024, 1024 * 1024));
                    //(1).client upload speed
                    //(2). file totalsize
                    //update channel's AdaptiveRecvByteBufAllocator's maxmum of ByteBuf,this decide the byte size of ByteBuf in ChannelEventRunnable,
                    //and decide every run's byte size upload to tencent in the thread pool.
                } else {
                    ctx.channel().pipeline().addLast(new HttpObjectAggregator(8 * 1024));
                    ctx.channel().pipeline().addLast(new CommonHttpRequestChannelHandler());
                }
            }
            //continue the flow in the channelpipeline
            super.channelRead(ctx, msg);
        }
    }
}
