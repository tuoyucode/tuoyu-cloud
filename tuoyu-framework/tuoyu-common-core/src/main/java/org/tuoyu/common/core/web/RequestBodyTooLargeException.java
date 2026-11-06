package org.tuoyu.common.core.web;

import java.io.IOException;

/**
 * 读取请求体时超过了大小上限。用于长度未知（分块传输）的请求，在读取过程中中止。
 *
 * @author tuoyu
 */
public class RequestBodyTooLargeException extends IOException {

    private final long limit;

    public RequestBodyTooLargeException(long limit) {
        super("请求体超过上限：" + limit + " 字节");
        this.limit = limit;
    }

    public long getLimit() {
        return limit;
    }

}
