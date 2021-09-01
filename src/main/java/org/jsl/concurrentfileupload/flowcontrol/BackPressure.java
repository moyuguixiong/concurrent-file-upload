package org.jsl.concurrentfileupload.flowcontrol;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/09/01
 */
public interface BackPressure extends FlowControl {

    int getReadBufferLowWaterMark();

    int getReadBufferHighWaterMark();

    boolean shouldBackPressureOpen();

    boolean shouldBackPressureClose();

    void openBackPressure();

    void closeBackPressure();
}