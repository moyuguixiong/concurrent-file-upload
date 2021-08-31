package org.jsl.concurrentfileupload.http;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

import java.util.Map;

/**
 * @author jinshilei
 * @version 0.0.1
 * @date 2021/08/31
 */
public class HttpUtil {

    public static boolean isMultipartContent(HttpRequest request) {
        String headerValue = getRequestHeader(request, "content-type");
        if (headerValue != null && headerValue.toLowerCase().startsWith("multipart/")) {
            //example：Content-Type:multipart/form-data; boundary=----WebKitFormBoundarydj1NU6EbLQZ03oaa
            return true;
        }
        return false;
    }


    public static String getRequestHeader(HttpRequest request, String headerName) {
        if (headerName == null || "".equalsIgnoreCase(headerName)) {
            return null;
        }
        HttpHeaders headers = request.headers();
        String headerNameLowerCase = headerName.toLowerCase();
        if (headers != null && headers.size() > 0) {
            for (Map.Entry<String, String> kv : headers.entries()) {
                if (kv.getKey().equalsIgnoreCase(headerNameLowerCase)) {
                    return kv.getValue();
                }
            }
        }
        return null;
    }
}
