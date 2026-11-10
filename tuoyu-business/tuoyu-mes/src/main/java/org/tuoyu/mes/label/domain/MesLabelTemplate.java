package org.tuoyu.mes.label.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 标签模板 mes_label_template：序列号、批次标签的尺寸、条码类型与显示的字段，前端按它排版并调用浏览器打印
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_label_template")
public class MesLabelTemplate extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "label_template_id")
    private Long labelTemplateId;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 适用对象（LabelTarget：sn 序列号、batch 批次）
     */
    private String targetType;

    /**
     * 标签宽度（毫米）
     */
    private Integer paperWidth;

    /**
     * 标签高度（毫米）
     */
    private Integer paperHeight;

    /**
     * 每行几列
     */
    private Integer columnCount;

    /**
     * 列间距（毫米）
     */
    private Integer columnGap;

    /**
     * 条码类型（BarcodeType：code128 一维码、qrcode 二维码）
     */
    private String barcodeType;

    /**
     * 显示的字段（LabelField 的编码，按顺序，逗号分隔）
     */
    private String fieldKeys;

    /**
     * 是否为该对象的默认模板（1 是 0 否）
     */
    private String defaultFlag;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 删除标志（0 存在 1 删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
