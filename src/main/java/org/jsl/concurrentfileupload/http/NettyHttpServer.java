package org.jsl.concurrentfileupload.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.Attribute;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class NettyHttpServer implements LifeCycle {

    /**
     * server state controll
     */
    private AtomicBoolean startCtl = new AtomicBoolean(false);

    /**
     * server start state
     */
    private volatile boolean started;

    /**
     * local address
     */
    private InetSocketAddress localAddress;

    private NioEventLoopGroup bossGroup;

    private NioEventLoopGroup workerGroup;

    private ServerBootstrap serverBootstrap;

    private int receiveBufferSize;

    private DynamicChannelHandler dynamicChannelHandler = new DynamicChannelHandler();


    public NettyHttpServer(int port) {
        this(null, port);
    }

    public NettyHttpServer(String host, int port) {
        if (host == null || "".equals(host)) {
            localAddress = new InetSocketAddress(port);
        } else {
            localAddress = new InetSocketAddress(host, port);
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup);
        serverBootstrap.channel(NioServerSocketChannel.class);
        serverBootstrap.localAddress(localAddress);
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 128);
        serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        serverBootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024, 64 * 1024));
        serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {

            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                Attribute<NettyHttpServer> attr = ch.attr(AttributeConstants.SERVER);
                if (attr.get() == null) {
                    attr.set(NettyHttpServer.this);
                }
                //获取socket的默认的receive buffer size。
                if (receiveBufferSize <= 0) {
                    SelectableChannel selectableChannel = ch.unsafe().ch();
                    if (selectableChannel instanceof SocketChannel) {
                        SocketChannel socketChannel = (SocketChannel) selectableChannel;
                        receiveBufferSize = socketChannel.socket().getReceiveBufferSize();
                    }
                }
                //because support big file upload,so maxChunkSize set 1MB
                ch.pipeline().addLast(new HttpServerCodec(HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH, HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE, receiveBufferSize));
                ch.pipeline().addLast(dynamicChannelHandler);
                //ch.pipeline().addLast(new HttpObjectAggregator());
            }
        });
    }

    @Override
    public void start() throws IOException {
        if (startCtl.compareAndSet(false, true)) {
            if (localAddress == null || localAddress.isUnresolved()) {
                throw new SocketException("Unresolved address");
            }
            ChannelFuture bindFuture = null;
            try {
                bindFuture = serverBootstrap.bind().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            bindFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        started = true;
                        System.out.println("server started:" + localAddress.toString());
                    } else {
                        System.out.println("server start failed!");
                        if (future.cause() != null) {
                            future.cause().printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @Override
    public void stop() {
        if (startCtl.compareAndSet(true, false)) {
            // TODO: 2021/08/31 stop the server
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        }
    }

    @Override
    public void pause() {
        // TODO: 2021/09/14 achieve pause
    }

    @Override
    public boolean isStarted() {
        return started;
    }
}
