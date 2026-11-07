package org.tuoyu.common.core.utils;

import cn.hutool.extra.spring.SpringUtil;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("对象转换 BeanMapper：委托 mapstruct-plus 的 Converter，空值安全")
class BeanMapperTest {

    private static Converter converter;

    @BeforeAll
    static void initConverter() {
        converter = mock(Converter.class);
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("converter", converter);
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @AfterEach
    void resetConverter() {
        reset(converter);
    }

    @Test
    @DisplayName("转换为新对象：来源为 null 时返回 null，不调用 Converter；目标类型不能为空")
    void mapToType() {
        Source source = new Source("alice");
        Target converted = new Target("alice");
        when(converter.convert(source, Target.class)).thenReturn(converted);

        assertThat(BeanMapper.map(source, Target.class)).isSameAs(converted);
        assertThat(BeanMapper.map((Source) null, Target.class)).isNull();
        assertThatThrownBy(() -> BeanMapper.map(source, (Class<Target>) null)).isInstanceOf(IllegalArgumentException.class);
        verify(converter).convert(source, Target.class);
    }

    @Test
    @DisplayName("来源是 Map 时按键名填充")
    @SuppressWarnings("unchecked")
    void mapFromMap() {
        Map<String, Object> source = Map.of("name", "bob");
        Target converted = new Target("bob");
        when(converter.convert(any(Map.class), any(Class.class))).thenReturn(converted);

        assertThat(BeanMapper.map(source, Target.class)).isSameAs(converted);
        verify(converter).convert(source, Target.class);
    }

    @Test
    @DisplayName("复制到已有对象：来源为 null 时原样返回目标对象；目标对象不能为空")
    void mapToExisting() {
        Source source = new Source("alice");
        Target target = new Target("old");
        when(converter.convert(source, target)).thenReturn(target);

        assertThat(BeanMapper.map(source, target)).isSameAs(target);
        assertThat(BeanMapper.map((Source) null, target)).isSameAs(target);
        assertThatThrownBy(() -> BeanMapper.map(source, (Target) null)).isInstanceOf(IllegalArgumentException.class);
        verify(converter).convert(source, target);
    }

    @Test
    @DisplayName("逐个转换：null 或空列表返回可修改的空列表；非 List 的集合也能转换")
    void mapList() {
        List<Source> sources = List.of(new Source("a"), new Source("b"));
        when(converter.convert(sources, Target.class)).thenReturn(List.of(new Target("a"), new Target("b")));

        List<Target> converted = BeanMapper.mapList(sources, Target.class);
        assertThat(converted).extracting(Target::name).containsExactly("a", "b");
        converted.add(new Target("c"));

        List<Target> empty = BeanMapper.mapList(null, Target.class);
        assertThat(empty).isEmpty();
        empty.add(new Target("x"));
        assertThat(BeanMapper.mapList(List.<Source>of(), Target.class)).isEmpty();
        verify(converter, never()).convert(List.<Source>of(), Target.class);

        Set<Source> set = new LinkedHashSet<>(sources);
        when(converter.convert(anyList(), any(Class.class))).thenReturn(new ArrayList<>(List.of(new Target("a"))));
        assertThat(BeanMapper.mapList(set, Target.class)).hasSize(1);

        when(converter.convert(anyList(), any(Class.class))).thenReturn(null);
        assertThat(BeanMapper.mapList(sources, Target.class)).isEmpty();
        assertThatThrownBy(() -> BeanMapper.mapList(sources, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private record Source(String name) {
    }

    private record Target(String name) {
    }

}
