package org.tuoyu.common.mybatis.core.page;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.tuoyu.common.core.domain.PageResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("分页结果转换 PageResults")
class PageResultsTest {

    private static Page<String> page(List<String> records, long total) {
        Page<String> page = new Page<>(1, 10);
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }

    @Test
    @DisplayName("元素类型不变：items 为当前页数据，total 为总条数；null 分页为空结果")
    void same() {
        PageResult<String> result = PageResults.of(page(List.of("a", "b"), 25));
        assertThat(result.getItems()).containsExactly("a", "b");
        assertThat(result.getTotal()).isEqualTo(25);
        assertThat(PageResults.of((Page<String>) null).getItems()).isEmpty();
    }

    @Test
    @DisplayName("按函数逐个转换；记录为 null 时为空列表")
    void mapped() {
        PageResult<Integer> result = PageResults.of(page(List.of("a", "bbb"), 2), String::length);
        assertThat(result.getItems()).containsExactly(1, 3);
        assertThat(result.getTotal()).isEqualTo(2);

        Page<String> noRecords = page(null, 0);
        noRecords.setRecords(null);
        assertThat(PageResults.of(noRecords, String::length).getItems()).isEmpty();
        assertThat(PageResults.of((Page<String>) null, String::length).getTotal()).isZero();
    }

    @Test
    @DisplayName("按类型转换（BeanMapper）")
    void mappedByType() {
        Converter converter = mock(Converter.class);
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("converter", converter);
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        List<String> records = List.of("a");
        when(converter.convert(records, Integer.class)).thenReturn(List.of(1));

        PageResult<Integer> result = PageResults.of(page(records, 8), Integer.class);
        assertThat(result.getItems()).containsExactly(1);
        assertThat(result.getTotal()).isEqualTo(8);
        assertThat(PageResults.of((Page<String>) null, Integer.class).getItems()).isEmpty();
    }

}
