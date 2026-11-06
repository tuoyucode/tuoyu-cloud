package org.tuoyu.common.redis.codec;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 测试用：记录不受信任的类是否被加载过。单独成类，读取标记时不会触发被观察类的加载。
 */
public final class LoadTracker {

    public static final AtomicBoolean UNTRUSTED_LOADED = new AtomicBoolean(false);

    private LoadTracker() {
    }

}
