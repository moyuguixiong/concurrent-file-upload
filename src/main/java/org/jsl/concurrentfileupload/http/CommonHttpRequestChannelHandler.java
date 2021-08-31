package org.jsl.concurrentfileupload.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;

/**
 * 通用的http请求处理器
 *
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class CommonHttpRequestChannelHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        DefaultFullHttpRequest request = (DefaultFullHttpRequest) msg;
        // TODO: 2021/08/31 complement the logic to handle orinary http request.
        super.channelRead(ctx, msg);
    }
}
