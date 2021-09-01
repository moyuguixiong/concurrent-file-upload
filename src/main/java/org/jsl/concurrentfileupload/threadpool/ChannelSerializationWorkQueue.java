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
        if (bindExecutor != null) {
            //if current number of threads in the pool is smaller than max size of threads, then return false.
            //so when the all thread in the thread pool is busy,but the current number of threads is smaller then max size of threads,
            //will firset create new thread to handle the task,not add to then task queue.
            if (bindExecutor.getPoolSize() < bindExecutor.getMaximumPoolSize()) {
                return false;
            }
        }
        return super.offer(runnable);
    }
}
