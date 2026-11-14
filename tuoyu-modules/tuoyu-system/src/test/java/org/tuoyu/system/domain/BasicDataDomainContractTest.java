package org.tuoyu.system.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.system.domain.bo.SysClientBo;
import org.tuoyu.system.domain.bo.SysConfigBo;
import org.tuoyu.system.domain.bo.SysDictDataBo;
import org.tuoyu.system.domain.bo.SysDictTypeBo;
import org.tuoyu.system.domain.bo.SysNoticeBo;
import org.tuoyu.system.domain.vo.SysClientVo;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 参数、字典、客户端、通知公告请求体的校验提示（规格 S2 第 3 节）
 */
@DisplayName("数据类契约：参数、字典、客户端、通知公告")
class BasicDataDomainContractTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> messages(Object bo, Class<?>... groups) {
        return VALIDATOR.validate(bo, groups).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("参数：名称、键名、键值必填，长度上限")
    void config() {
        assertThat(messages(new SysConfigBo())).containsExactlyInAnyOrder("参数名称不能为空", "参数键名不能为空", "参数键值不能为空");
        SysConfigBo bo = new SysConfigBo();
        bo.setConfigName("n".repeat(101));
        bo.setConfigKey("k".repeat(101));
        bo.setConfigValue("v".repeat(501));
        assertThat(messages(bo)).containsExactlyInAnyOrder("参数名称不能超过100个字符", "参数键名长度不能超过100个字符",
            "参数键值长度不能超过500个字符");
    }

    @Test
    @DisplayName("字典类型：类型编码以小写字母开头，只含小写字母、数字、下划线")
    void dictType() {
        SysDictTypeBo bo = new SysDictTypeBo();
        bo.setDictName("是否");
        bo.setDictType("Sys-Yes");
        assertThat(messages(bo)).containsExactly("字典类型必须以字母开头，且只能为（小写字母，数字，下滑线）");
        bo.setDictType("sys_yes_no");
        assertThat(messages(bo)).isEmpty();
        assertThat(messages(new SysDictTypeBo())).containsExactlyInAnyOrder("字典名称不能为空", "字典类型不能为空");
    }

    @Test
    @DisplayName("字典数据：标签、键值、类型必填；样式属性长度上限")
    void dictData() {
        assertThat(messages(new SysDictDataBo())).containsExactlyInAnyOrder("字典标签不能为空", "字典键值不能为空", "字典类型不能为空");
        SysDictDataBo bo = new SysDictDataBo();
        bo.setDictLabel("是");
        bo.setDictValue("Y");
        bo.setDictType("sys_yes_no");
        bo.setCssClass("c".repeat(101));
        assertThat(messages(bo)).containsExactly("样式属性长度不能超过100个字符");
    }

    @Test
    @DisplayName("客户端：新增时 key、密钥、授权类型必填；修改时 ID、key、授权类型必填、密钥可空")
    void client() {
        assertThat(messages(new SysClientBo(), OnCreate.class))
            .containsExactlyInAnyOrder("客户端key不能为空", "客户端秘钥不能为空", "授权类型不能为空");
        assertThat(messages(new SysClientBo(), OnUpdate.class))
            .containsExactlyInAnyOrder("id不能为空", "客户端key不能为空", "授权类型不能为空");
    }

    @Test
    @DisplayName("通知公告：标题必填、不超过 50 个字符、不能含脚本")
    void notice() {
        assertThat(messages(new SysNoticeBo())).containsExactly("公告标题不能为空");
        SysNoticeBo bo = new SysNoticeBo();
        bo.setNoticeTitle("<script>x</script>");
        assertThat(messages(bo)).containsExactly("公告标题不能包含脚本字符");
        bo.setNoticeTitle("t".repeat(51));
        assertThat(messages(bo)).containsExactly("公告标题不能超过50个字符");
    }

    @Test
    @DisplayName("客户端视图：授权类型、访问路径、IP 白名单同时有字符串与列表两种形式")
    void clientVoJson() throws Exception {
        SysClientVo vo = new SysClientVo();
        vo.setGrantTypeList(List.of("password"));
        vo.setAccessPathList(List.of("/**"));
        vo.setIpWhitelistList(List.of());
        String json = JsonMapper.builder().build().writeValueAsString(vo);
        assertThat(json).contains("\"grantTypeList\":[\"password\"]").contains("\"accessPathList\":[\"/**\"]")
            .contains("\"ipWhitelistList\":[]").contains("\"clientSecret\"");
    }
}
