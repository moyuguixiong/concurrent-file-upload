package org.jsl.concurrentfileupload.threadpool;

import java.util.concurrent.*;

/**
 * thread first created thread pool
 * add task,first to create new thread,not insert into task queue.
 *
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class ThreadFirstThreadPoolExecutor extends ThreadPoolExecutor {

    public ThreadFirstThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                                         RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        //prestart core threads
        prestartAllCoreThreads();
    }
}
