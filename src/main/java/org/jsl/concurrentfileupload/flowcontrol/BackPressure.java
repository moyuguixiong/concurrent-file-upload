package org.jsl.concurrentfileupload.flowcontrol;

import io.netty.buffer.ByteBuf;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/09/01
 */
public interface BackPressure {

    long getReadBufferLowWaterMark();

    long getReadBufferHighWaterMark();

    boolean shouldBackPressureOpen();

    boolean shouldBackPressureClose();

    boolean closeBackPressure();

    boolean isBackPressureOpen();

    void openBackPressureIfNecessary(long contentLength, ByteBuf message);

    void addChannelCumulatorContentLength(long contentLength);

    void removeChannelCumulatorContentLength(long contentLength);
}