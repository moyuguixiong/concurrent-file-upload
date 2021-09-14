package org.jsl.concurrentfileupload.threadpool;


import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/09/01
 */
public class ChannelEventRunnable implements Runnable {
    private static ConcurrentHashMap<String, RandomAccessFile> FILE_TO_RAF = new ConcurrentHashMap<>();
    private Channel channel;
    private ByteBuf byteBuf;
    private byte[] bytes;
    private int channelTotalByteSize;
    private int processedByteSize;
    private String fileName;

    public ChannelEventRunnable(Channel channel, byte[] bytes, int channelTotalByteSize, String fileName) {
        this.channel = channel;
        this.bytes = bytes;
        this.channelTotalByteSize = channelTotalByteSize;
        this.fileName = fileName;
    }

    public void setProcessedByteSize(int processedByteSize) {
        this.processedByteSize = processedByteSize;
    }

    public int getChannelTotalByteSize() {
        return channelTotalByteSize;
    }

    public Channel getChannel() {
        return channel;
    }

    public int getByteSize() {
        return bytes.length;
    }

    @Override
    public void run() {
        // TODO: 2021/09/02 hanele ByteBuf object's release in different thread
        RandomAccessFile raf = FILE_TO_RAF.get(fileName);
        if (raf == null) {
            File file = new File("e:\\" + fileName);
            if (!file.exists()) {
                try {
                    file.createNewFile();
                    raf = new RandomAccessFile(file, "rw");
                    FILE_TO_RAF.put(fileName, raf);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            long start = System.currentTimeMillis();
            byte[] bytes = new byte[byteBuf.readableBytes()];
            System.out.println(byteBuf.readableBytes());
            byteBuf.readBytes(bytes);
            byteBuf.release();
            raf.seek(raf.length());
            raf.write(bytes);
            long end = System.currentTimeMillis();
            System.out.println("write time:" + (end - start));
            bytes = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (processedByteSize + getByteSize() >= channelTotalByteSize) {
            //lat ByteBuf object
            try {
                raf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        try {
//            TimeUnit.SECONDS.sleep(50);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }
}
