package org.jsl.concurrentfileupload.threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class NamedThreadFactory implements ThreadFactory {

    private String namePrefix;
    private AtomicInteger number = new AtomicInteger(0);
    private ThreadGroup tGroup;

    public NamedThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
        SecurityManager manager = System.getSecurityManager();
        tGroup = manager == null ? Thread.currentThread().getThreadGroup() : manager.getThreadGroup();
    }

    @Override
    public Thread newThread(Runnable r) {
        String tName = namePrefix + "-thread-" + number.incrementAndGet();
        return new Thread(tGroup, r, tName, 0);
    }
}
