package org.tuoyu.mes.common.approval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 制造执行中需要审批的单据：不良处置评审（让步接收、报废）与补料申请。
 * <p>
 * 两类审批默认关闭，关闭时单据照常直接流转。开启前先在“流程定义”中导入并发布对应编码的流程
 * （见 tuoyu-business/tuoyu-mes/deploy/workflow/README.md）。
 * <pre>
 * tuoyu:
 *   mes:
 *     approval:
 *       defect-disposition:
 *         enabled: true
 *         flow-code: mes_defect_disposition
 *       supplement:
 *         enabled: true
 *         flow-code: mes_material_supplement
 * </pre>
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "tuoyu.mes.approval")
public class MesApprovalProperties {

    /**
     * 不良处置评审：处置方式为让步接收或报废时，审批通过后才执行处置
     */
    private Flow defectDisposition = new Flow("mes_defect_disposition");

    /**
     * 补料申请：补料单审批通过后才变为已提交，仓库才能执行
     */
    private Flow supplement = new Flow("mes_material_supplement");

    /**
     * 一类审批的开关与流程编码
     */
    @Data
    public static class Flow {

        /**
         * 是否开启
         */
        private boolean enabled;

        /**
         * 流程定义编码（与导入的流程定义一致）。关闭开关后仍按它处理审批中单据的审批结果
         */
        private String flowCode;

        public Flow() {
        }

        public Flow(String flowCode) {
            this.flowCode = flowCode;
        }

    }

}
