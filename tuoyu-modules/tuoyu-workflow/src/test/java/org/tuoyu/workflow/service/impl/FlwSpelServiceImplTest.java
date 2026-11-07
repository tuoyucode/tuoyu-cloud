package org.tuoyu.workflow.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.workflow.mapper.FlwSpelMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("流程表达式：删除")
class FlwSpelServiceImplTest {

    private final FlwSpelMapper mapper = mock(FlwSpelMapper.class);

    private final FlwSpelServiceImpl service = new FlwSpelServiceImpl(mapper);

    @Test
    @DisplayName("删除成功时不报错；一条都没删掉（表达式不存在）时抛出带原因的业务异常")
    void deleteRequiresExistingRows() {
        when(mapper.deleteByIds(List.of(1L))).thenReturn(1);
        assertThatCode(() -> service.deleteWithValidByIds(List.of(1L), true)).doesNotThrowAnyException();
        verify(mapper).deleteByIds(List.of(1L));

        when(mapper.deleteByIds(List.of(2L))).thenReturn(0);
        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(2L), false))
            .isInstanceOf(ServiceException.class)
            .hasMessage("删除流程表达式失败，表达式不存在");
    }

}
