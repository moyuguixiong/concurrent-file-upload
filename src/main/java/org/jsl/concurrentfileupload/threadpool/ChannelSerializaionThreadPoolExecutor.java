package org.jsl.concurrentfileupload.threadpool;

import io.netty.channel.Channel;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * same channel's data handled serialized thread pool
 * the same channel's data will be handle serializable,and the same channel's data will be handle
 * by different thread in thread pool.
 *
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/09/01
 */
public class ChannelSerializaionThreadPoolExecutor extends ThreadFirstThreadPoolExecutor {

    private ConcurrentHashMap<Object, Executor> channelToChildExecutor = new ConcurrentHashMap<>();

    public ChannelSerializaionThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                                                 RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    public void execute(Runnable command) {
        if (command instanceof ChannelEventRunnable) {
            Executor childExecutor = getChildExecutorByChannel(((ChannelEventRunnable) command).getChannel());
            childExecutor.execute(command);
        } else {
            super.execute(command);
        }
    }

    private Executor getChildExecutorByChannel(Channel channel) {
        Executor executor = channelToChildExecutor.get(channel);
        if (executor == null) {
            executor = new ChildExecutor();
            Executor oldExecutor = channelToChildExecutor.putIfAbsent(channel, executor);
            if (oldExecutor != null) {
                executor = oldExecutor;
            }
        }
        return executor;
    }

    public int getChannelPendingByteSize(Channel channel) {
        Executor executor = getChildExecutorByChannel(channel);
        if (executor instanceof ChildExecutor) {
            ChildExecutor childExecutor = (ChildExecutor) executor;
            return childExecutor.getPendingSize();
        }
        return 0;
    }

    private class ChildExecutor implements Executor, Runnable {
        private ConcurrentLinkedQueue<Runnable> channelTaskQueue = new ConcurrentLinkedQueue<>();
        private AtomicBoolean isRunning = new AtomicBoolean(false);
        private AtomicInteger pendingSize = new AtomicInteger(0);

        public int getPendingSize() {
            return pendingSize.get();
        }

        @Override
        public void execute(Runnable command) {
            if (channelTaskQueue.offer(command) && command instanceof ChannelEventRunnable) {
                pendingSize.addAndGet(((ChannelEventRunnable) command).getByteSize());
            }
            if (!isRunning.get()) {
                //repeat submit a channel's childExecutor
                ChannelSerializaionThreadPoolExecutor.this.execute(this);
            }
        }

        @Override
        public void run() {
            if (isRunning.compareAndSet(false, true)) {
                try {
                    while (true) {
                        // TODO: 2021/09/01 实现上传的负载均衡,类似netty io线程读取多个连接的数据，对于有数据可读的连接，每轮循环最多读取16次，向腾讯云上传，
                        // 因为使用了线程池，为了均衡每个连接的上传速度，可以上传固定大小后，就结束并重新提交当前childExecutor，代价是会增加线程竞争(抢队列中任务)。
                        // 可以根据上传的速度动态调整单个连接每次的最大上传字节数。还可以上传耗费的时间制定策略
                        Runnable poll = channelTaskQueue.poll();
                        if (poll == null) {
                            break;
                        }
                        boolean isRun = false;
                        Thread currentThread = Thread.currentThread();
                        try {
                            ChannelSerializaionThreadPoolExecutor.this.beforeExecute(currentThread, poll);
                            poll.run();
                            isRun = true;
                            ChannelSerializaionThreadPoolExecutor.this.afterExecute(poll, null);
                        } catch (Exception e) {
                            if (!isRun) {
                                ChannelSerializaionThreadPoolExecutor.this.afterExecute(poll, e);
                            }
                            throw e;
                        } finally {
                            if (poll instanceof ChannelEventRunnable) {
                                ChannelEventRunnable r = (ChannelEventRunnable) poll;
                                pendingSize.addAndGet(-r.getByteSize());
                            }
                        }
                    }
                } finally {
                    isRunning.set(false);
                }
            }
        }
    }
}
