package org.tuoyu.common.rpc.testapi;

import org.tuoyu.common.rpc.annotation.RemoteApi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试用的跨模块接口
 */
@RemoteApi
public interface GreetingApi {

    String greet(String name);

    /**
     * 提供方修改参数（违反值语义），用于验证严格模式
     */
    Box append(Box box);

    void fail(String message);

    boolean inTransaction();

    class Box implements Serializable {
        public final List<String> items = new ArrayList<>();
    }

}
