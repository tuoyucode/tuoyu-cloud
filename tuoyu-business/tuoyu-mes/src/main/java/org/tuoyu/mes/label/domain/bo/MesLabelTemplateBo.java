package org.tuoyu.mes.label.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.label.domain.MesLabelTemplate;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 标签模板业务对象 mes_label_template：新增、修改的字段与列表的查询条件
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesLabelTemplate.class, reverseConvertGenerate = false)
public class MesLabelTemplateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 一个标签最多显示的字段数
     */
    public static final int MAX_FIELDS = 8;

    @NotNull(message = "标签模板 ID 不能为空", groups = OnUpdate.class)
    private Long labelTemplateId;

    @NotBlank(message = "模板名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "模板名称不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String templateName;

    /**
     * 适用对象，见 LabelTarget（查询时为条件）
     */
    @NotBlank(message = "适用对象不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String targetType;

    /**
     * 标签宽度（毫米）
     */
    @NotNull(message = "标签宽度不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Min(value = 10, message = "标签宽度至少 10 毫米", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 200, message = "标签宽度不能超过 200 毫米", groups = {OnCreate.class, OnUpdate.class})
    private Integer paperWidth;

    /**
     * 标签高度（毫米）
     */
    @NotNull(message = "标签高度不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Min(value = 10, message = "标签高度至少 10 毫米", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 200, message = "标签高度不能超过 200 毫米", groups = {OnCreate.class, OnUpdate.class})
    private Integer paperHeight;

    /**
     * 每行几列（一行打印几个标签）
     */
    @NotNull(message = "每行列数不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Min(value = 1, message = "每行至少 1 列", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 6, message = "每行不能超过 6 列", groups = {OnCreate.class, OnUpdate.class})
    private Integer columnCount;

    /**
     * 列间距（毫米）
     */
    @Min(value = 0, message = "列间距不能为负数", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 20, message = "列间距不能超过 20 毫米", groups = {OnCreate.class, OnUpdate.class})
    private Integer columnGap;

    /**
     * 条码类型，见 BarcodeType
     */
    @NotBlank(message = "条码类型不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String barcodeType;

    /**
     * 显示的字段（LabelField 的编码，按显示顺序）
     */
    @NotEmpty(message = "请至少选择一个显示的字段", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = MAX_FIELDS, message = "显示的字段不能超过 " + MAX_FIELDS + " 个", groups = {OnCreate.class, OnUpdate.class})
    private List<String> fields;

    /**
     * 是否为该对象的默认模板（1 是 0 否）；设为默认时，同一对象的其他模板自动取消默认
     */
    @Pattern(regexp = "[01]", message = "是否默认只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String defaultFlag;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
