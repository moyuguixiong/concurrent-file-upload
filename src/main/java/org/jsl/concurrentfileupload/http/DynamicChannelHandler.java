package org.jsl.concurrentfileupload.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
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
                boolean multipartContent = HttpUtil.isMultipartContent((HttpRequest) msg);
                if (multipartContent) {
                    ctx.channel().pipeline().addLast(new HttpMultiPartUploadChannelHandler((NioSocketChannel) ctx.channel()));
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
