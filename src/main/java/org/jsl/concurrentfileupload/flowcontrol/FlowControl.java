package org.jsl.concurrentfileupload.flowcontrol;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/09/01
 */
public interface FlowControl {

    boolean isReadFlowControlOpen();

    boolean isWriteFlowControlOpen();

    boolean shouldCloseReadFlowControl();

    boolean shouldCloseWriteFlowControl();

    boolean closeReadFlowControl();

    boolean closeWriteFlowControl();
}
