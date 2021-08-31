package org.jsl;

import org.jsl.concurrentfileupload.http.NettyServer;

import java.io.IOException;

/**
 * start class
 */
public class ConcurrentFileUploadApp {

    public static void main(String[] args) {
        NettyServer nettyServer = new NettyServer(18888);
        try {
            nettyServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (nettyServer.isStarted()) {
            System.out.println("add hook");
            Runtime.getRuntime().addShutdownHook(new ShutdownThread(nettyServer));
        }
    }

    private static class ShutdownThread extends Thread {

        private NettyServer nettyServer;

        public ShutdownThread(NettyServer nettyServer) {
            this.nettyServer = nettyServer;
        }

        @Override
        public void run() {
            System.out.println("shutdownhook stop the server");
            if (nettyServer.isStarted()) {
                nettyServer.stop();
            }
        }
    }
}
