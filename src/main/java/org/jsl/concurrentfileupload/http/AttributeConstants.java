package org.jsl.concurrentfileupload.http;

import io.netty.util.AttributeKey;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class AttributeConstants {

    public static AttributeKey<NettyServer> SERVER = AttributeKey.valueOf("server");
}
