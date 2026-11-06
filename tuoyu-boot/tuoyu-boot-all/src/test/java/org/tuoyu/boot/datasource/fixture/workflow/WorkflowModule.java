package org.tuoyu.boot.datasource.fixture.workflow;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.springframework.stereotype.Service;
import org.tuoyu.boot.datasource.fixture.system.SystemModule;

import java.util.List;

/**
 * 测试用：workflow 模块中的组件，调用 system 模块（相当于经过本地调用代理的跨模块调用）
 */
@Service
public class WorkflowModule {

    private final SystemModule system;

    public WorkflowModule() {
        this(null);
    }

    public WorkflowModule(SystemModule system) {
        this.system = system;
    }

    /**
     * @return 调用 system 之前、之中、之后的数据源
     */
    public List<String> run() {
        String before = DynamicDataSourceContextHolder.peek();
        String inSystem = system.current();
        return List.of(before, inSystem, DynamicDataSourceContextHolder.peek());
    }

}
