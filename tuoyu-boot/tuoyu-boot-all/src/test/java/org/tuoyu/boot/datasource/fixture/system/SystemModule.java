package org.tuoyu.boot.datasource.fixture.system;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.springframework.stereotype.Service;

/**
 * 测试用：system 模块中的组件
 */
@Service
public class SystemModule {

    public String current() {
        return DynamicDataSourceContextHolder.peek();
    }

    public void fail() {
        throw new IllegalStateException("boom");
    }

}
