package org.jsl.concurrentfileupload.threadpool;


import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/09/01
 */
public class ChannelEventRunnable implements Runnable {

    private Channel channel;
    private ByteBuf byteBuf;
    private int byteSize;

    public ChannelEventRunnable(Channel channel, ByteBuf byteBuf) {
        this.channel = channel;
        this.byteBuf = byteBuf;
        this.byteSize = byteBuf.readableBytes();
    }

    public Channel getChannel() {
        return channel;
    }

    public int getByteSize() {
        return byteSize;
    }

    @Override
    public void run() {

    }
}
