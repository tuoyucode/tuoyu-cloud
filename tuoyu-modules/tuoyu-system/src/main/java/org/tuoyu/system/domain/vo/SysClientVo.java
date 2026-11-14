package org.tuoyu.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.github.linpeilie.annotations.AutoMappers;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.domain.SysClient;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 客户端视图（同时生成到跨模块对象的转换）。
 * <p>
 * 授权类型、访问路径、IP 白名单同时给出字符串与列表两种形式。密钥只在认证服务按客户端 ID 查询时带出，
 * 管理接口（详情、列表、导出）返回前置为 null，也不导出
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMappers({
    @AutoMapper(target = SysClient.class),
    @AutoMapper(target = RemoteClientVo.class, reverseConvertGenerate = false)
})
public class SysClientVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @ExcelProperty(value = "id")
    private Long id;

    /**
     * 客户端 ID
     */
    @ExcelProperty(value = "客户端id")
    private String clientId;

    /**
     * 客户端 key
     */
    @ExcelProperty(value = "客户端key")
    private String clientKey;

    /**
     * 客户端密钥
     */
    private String clientSecret;

    /**
     * 授权类型列表
     */
    private List<String> grantTypeList;

    /**
     * 授权类型（逗号分隔）
     */
    @ExcelProperty(value = "授权类型")
    private String grantType;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 允许访问的路径（逗号分隔）
     */
    @ExcelProperty(value = "允许访问路径")
    private String accessPath;

    /**
     * 允许访问的路径（列表）
     */
    private List<String> accessPathList;

    /**
     * IP 白名单（逗号分隔）
     */
    @ExcelProperty(value = "IP白名单")
    private String ipWhitelist;

    /**
     * IP 白名单（列表）
     */
    private List<String> ipWhitelistList;

    /**
     * 令牌活跃超时时间（秒）
     */
    @ExcelProperty(value = "token活跃超时时间")
    private Long activeTimeout;

    /**
     * 令牌固定超时时间（秒）
     */
    @ExcelProperty(value = "token固定超时时间")
    private Long timeout;

    /**
     * 状态（0 正常 1 停用）
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

}
