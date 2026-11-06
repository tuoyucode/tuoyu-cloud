package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.system.domain.bo.SysClientBo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.mapper.SysClientMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("客户端管理：管理接口不下发密钥")
class SysClientServiceImplTest {

    private static SysClientVo client() {
        SysClientVo vo = new SysClientVo();
        vo.setId(1L);
        vo.setClientId("client-id");
        vo.setClientKey("pc");
        vo.setClientSecret("secret-value");
        vo.setGrantType("password,sms");
        return vo;
    }

    @Test
    @DisplayName("详情接口去掉密钥，其他字段照常回填")
    void queryByIdHidesSecret() {
        SysClientMapper mapper = mock(SysClientMapper.class);
        when(mapper.selectVoById(1L)).thenReturn(client());

        SysClientVo vo = new SysClientServiceImpl(mapper).queryById(1L);

        assertNull(vo.getClientSecret());
        assertEquals("client-id", vo.getClientId());
        assertEquals(List.of("password", "sms"), vo.getGrantTypeList());
    }

    @Test
    @DisplayName("列表接口去掉每条记录的密钥")
    void queryPageListHidesSecret() {
        SysClientMapper mapper = mock(SysClientMapper.class);
        Page<SysClientVo> page = new Page<>(1, 10);
        page.setRecords(List.of(client(), client()));
        page.setTotal(2);
        when(mapper.selectVoPage(any(), any())).thenReturn(page);

        PageResult<SysClientVo> result = new SysClientServiceImpl(mapper).queryPageList(new SysClientBo(), new PageQuery());

        assertEquals(2, result.getRows().size());
        result.getRows().forEach(vo -> assertNull(vo.getClientSecret()));
    }

    @Test
    @DisplayName("记录不存在时返回 null")
    void queryByIdMissing() {
        SysClientMapper mapper = mock(SysClientMapper.class);

        assertNull(new SysClientServiceImpl(mapper).queryById(2L));
    }
}
