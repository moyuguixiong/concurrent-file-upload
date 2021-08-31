package org.jsl.concurrentfileupload.threadpool;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class ChannelSerializationWorkQueue extends LinkedBlockingQueue<Runnable> {

    private ThreadPoolExecutor bindExecutor;

    public ChannelSerializationWorkQueue() {
    }

    public ThreadPoolExecutor getBindExecutor() {
        return bindExecutor;
    }

    public void setBindExecutor(ThreadPoolExecutor bindExecutor) {
        this.bindExecutor = bindExecutor;
    }

    @Override
    public boolean offer(Runnable runnable) {
        return super.offer(runnable);
    }
}
