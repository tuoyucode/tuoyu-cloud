package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.system.domain.SysClient;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端请求对象（新增、修改、修改状态、列表条件）。
 * <p>
 * 访问路径与 IP 白名单可以按字符串（逗号、分号或换行分隔）或列表提交，字符串形式优先；都不提交表示不修改
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysClient.class, reverseConvertGenerate = false)
public class SysClientBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @NotNull(message = "id不能为空", groups = {OnUpdate.class})
    private Long id;

    /**
     * 客户端 ID（修改状态时按它定位；新增、修改时不使用）
     */
    private String clientId;

    /**
     * 客户端 key
     */
    @NotBlank(message = "客户端key不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String clientKey;

    /**
     * 客户端密钥（修改时为空表示不修改）
     */
    @NotBlank(message = "客户端秘钥不能为空", groups = {OnCreate.class})
    private String clientSecret;

    /**
     * 授权类型列表
     */
    @NotNull(message = "授权类型不能为空", groups = {OnCreate.class, OnUpdate.class})
    private List<String> grantTypeList;

    /**
     * 授权类型（逗号分隔；写入时以授权类型列表为准）
     */
    private String grantType;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 允许访问的路径（字符串形式）
     */
    private String accessPath;

    /**
     * 允许访问的路径（列表形式）
     */
    private List<String> accessPathList;

    /**
     * IP 白名单（字符串形式）
     */
    private String ipWhitelist;

    /**
     * IP 白名单（列表形式）
     */
    private List<String> ipWhitelistList;

    /**
     * 令牌活跃超时时间（秒）
     */
    private Long activeTimeout;

    /**
     * 令牌固定超时时间（秒）
     */
    private Long timeout;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 请求参数
     */
    private Map<String, Object> params = new HashMap<>();

}
