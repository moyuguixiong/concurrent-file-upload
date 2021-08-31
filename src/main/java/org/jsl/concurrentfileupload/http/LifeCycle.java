package org.jsl.concurrentfileupload.http;

import java.io.IOException;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public interface LifeCycle {

    void start() throws IOException;

    void stop();

    boolean isStarted();
}
