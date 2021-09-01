package org.jsl.concurrentfileupload.flowcontrol;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.jsl.concurrentfileupload.threadpool.NamedThreadFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/09/01
 */
public class FlowControlAssistant {

    private static FlowControlAssistant flowControlAssistant = new FlowControlAssistant();
    private HashedWheelTimer timer = new HashedWheelTimer(new NamedThreadFactory("flowcontrolassistant", true), 1, TimeUnit.SECONDS, 60);

    private FlowControlAssistant() {

    }

    public static FlowControlAssistant createFlowControlAssistant() {
        return flowControlAssistant;
    }

    public void closeFlowControl(FlowControl flowControl) {
        timer.newTimeout(new CloseFlowControlTask(flowControl), 3, TimeUnit.SECONDS);
    }

    private static class CloseFlowControlTask implements TimerTask {

        private FlowControl flowControl;

        public CloseFlowControlTask(FlowControl flowControl) {
            this.flowControl = flowControl;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            if (flowControl == null) {
                timeout.cancel();
                return;
            }
            if (timeout.isCancelled()) {
                return;
            }
            boolean closeReadFlowControl = false;
            boolean closeWriteFlowControl = false;
            if (!flowControl.isReadFlowControlOpen()) {
                closeReadFlowControl = true;
            } else {
                if (flowControl.shouldCloseReadFlowControl()) {
                    closeReadFlowControl = flowControl.closeReadFlowControl();
                }
            }
            if (!flowControl.isWriteFlowControlOpen()) {
                closeWriteFlowControl = true;
            } else {
                if (flowControl.shouldCloseWriteFlowControl()) {
                    closeWriteFlowControl = flowControl.closeWriteFlowControl();
                }
            }
            if (closeReadFlowControl && closeWriteFlowControl) {
                timeout.cancel();
            }
        }
    }
}
