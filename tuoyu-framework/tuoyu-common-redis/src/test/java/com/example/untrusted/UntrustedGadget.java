package com.example.untrusted;

import org.tuoyu.common.redis.codec.LoadTracker;

/**
 * 测试用：模拟攻击者希望通过 Redis 数据实例化的类。位于白名单之外的包，被加载时会留下标记。
 */
public class UntrustedGadget {

    static {
        LoadTracker.UNTRUSTED_LOADED.set(true);
    }

    private String name;

    public String getName() {
        return name;
    }

}
