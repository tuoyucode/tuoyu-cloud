package org.tuoyu.common.mybatis.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.mybatis.TestSpringContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tuoyu.common.mybatis.TestSpringContext.ID_GENERATOR;

/**
 * 主键生成：使用容器中的 MyBatis-Plus 主键生成器（与插入数据时的主键来源相同）
 */
@DisplayName("IdGeneratorUtil：取容器中的主键生成器")
class IdGeneratorUtilTest {

    @BeforeEach
    void installContext() {
        TestSpringContext.install();
        reset(ID_GENERATOR);
    }

    @Test
    @DisplayName("nextLongId 返回生成器生成的数字主键（不传实体）")
    void nextLongId() {
        when(ID_GENERATOR.nextId(isNull())).thenReturn(1761100000000000123L, 1761100000000000124L);
        assertThat(IdGeneratorUtil.nextLongId()).isEqualTo(1761100000000000123L);
        assertThat(IdGeneratorUtil.nextLongId()).isEqualTo(1761100000000000124L);
        verify(ID_GENERATOR, times(2)).nextId(isNull());
    }

    @Test
    @DisplayName("生成器返回其他数字类型时转为 long")
    void convertsNumber() {
        when(ID_GENERATOR.nextId(isNull())).thenReturn(42);
        assertThat(IdGeneratorUtil.nextLongId()).isEqualTo(42L);
    }

}
