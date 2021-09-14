package org.jsl.concurrentfileupload.http;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class AttributeConstants {

    public static AttributeKey<NettyHttpServer> SERVER = AttributeKey.valueOf("server");

    public static AttributeKey<Integer> UPLOAD_CONTENT_LENGTH = AttributeKey.valueOf("upload_content_length");

    public static AttributeKey<String> GENERATE_FILE_NAME = AttributeKey.valueOf("generate_file_name");

    public static <T> T getAttrValue(Channel channel, AttributeKey<T> key) {
        return channel.attr(key).get();
    }

    public static void setAttrValue(Channel channel, AttributeKey key, Object value) {
        Attribute attr = channel.attr(key);
        attr.set(value);
    }
}
