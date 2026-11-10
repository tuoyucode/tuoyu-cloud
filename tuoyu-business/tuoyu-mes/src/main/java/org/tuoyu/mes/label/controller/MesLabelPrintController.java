package org.tuoyu.mes.label.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.mes.label.domain.bo.MesBatchLabelBo;
import org.tuoyu.mes.label.domain.bo.MesSnLabelBo;
import org.tuoyu.mes.label.domain.vo.MesLabelDataVo;
import org.tuoyu.mes.label.service.IMesLabelPrintService;

import java.util.List;

/**
 * 标签打印数据：只读，返回每个标签的字段，前端按模板排版后调用浏览器打印。
 * 用 POST 是因为一次可以有几百个 ID，放在请求体中；打印记入操作日志（导出类，不保存返回的标签数据）
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/label-print")
public class MesLabelPrintController {

    private final IMesLabelPrintService printService;

    @SaCheckPermission("mes:sn:print")
    @OperationLog(module = "产品序列号", action = AuditAction.EXPORT, saveResponseData = false)
    @PostMapping("/sn")
    public ApiResult<List<MesLabelDataVo>> sn(@Validated @RequestBody MesSnLabelBo bo) {
        return ApiResult.ok(printService.snLabels(bo));
    }

    @SaCheckPermission("mes:workorder:print")
    @OperationLog(module = "生产工单", action = AuditAction.EXPORT, saveResponseData = false)
    @PostMapping("/batch")
    public ApiResult<List<MesLabelDataVo>> batch(@Validated @RequestBody MesBatchLabelBo bo) {
        return ApiResult.ok(printService.batchLabels(bo.getWorkorderIds()));
    }

}
