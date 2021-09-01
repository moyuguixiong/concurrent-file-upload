package org.jsl;

import static org.junit.Assert.assertTrue;

import org.jsl.concurrentfileupload.threadpool.NamedThreadFactory;
import org.junit.Test;

import javax.naming.Name;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() throws InterruptedException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TimeUnit.SECONDS.sleep(5);
                    System.out.println("run");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        TimeUnit.SECONDS.sleep(2);
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Executor executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("flowcontrolassistant", true));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    TimeUnit.SECONDS.sleep(2);
                    System.out.println("1");
                    latch.countDown();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        latch.await();
        try {
            TimeUnit.SECONDS.sleep(2);
            latch.countDown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println("2");
            }
        });

        try {
            TimeUnit.SECONDS.sleep(2);
            latch.countDown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
