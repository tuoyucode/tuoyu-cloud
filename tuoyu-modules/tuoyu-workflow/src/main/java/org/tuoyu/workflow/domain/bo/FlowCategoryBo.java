package org.tuoyu.workflow.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.workflow.domain.FlowCategory;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程分类业务对象 wf_category
 *
 * @author may
 * @date 2023-06-27
 */
@Data
@AutoMapper(target = FlowCategory.class, reverseConvertGenerate = false)
public class FlowCategoryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流程分类ID
     */
    @NotNull(message = "流程分类ID不能为空", groups = {OnUpdate.class})
    private Long categoryId;

    /**
     * 父流程分类id
     */
    @NotNull(message = "父流程分类id不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long parentId;

    /**
     * 流程分类名称
     */
    @NotBlank(message = "流程分类名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String categoryName;

    /**
     * 显示顺序
     */
    private Long orderNum;

    /**
     * 请求参数
     */
    private Map<String, Object> params = new HashMap<>();

}
