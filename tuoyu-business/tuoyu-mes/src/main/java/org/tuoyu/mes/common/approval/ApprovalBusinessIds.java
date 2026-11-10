package org.tuoyu.mes.common.approval;

/**
 * 单据与流程实例之间的业务 ID。
 * <p>
 * 第一次提交审批用单据主键；一个业务 ID 只能对应一个流程实例，终止、作废后的实例不能再次提交，
 * 所以之后的审批在主键后加轮次（{@code <主键>-2}、{@code <主键>-3} ……）。单据保存当前使用的业务 ID，
 * 审批结果按它匹配，旧一轮的审批结果不会作用到新一轮上。
 *
 * @author tuoyu
 */
public final class ApprovalBusinessIds {

    private static final char SEPARATOR = '-';

    private ApprovalBusinessIds() {
    }

    /**
     * 第一次提交审批时的业务 ID
     */
    public static String first(Long bizId) {
        return String.valueOf(bizId);
    }

    /**
     * 下一轮审批的业务 ID
     *
     * @param bizId   单据主键
     * @param current 当前的业务 ID，为空时取第一轮
     */
    public static String next(Long bizId, String current) {
        if (current == null || current.isBlank()) {
            return first(bizId);
        }
        int index = current.lastIndexOf(SEPARATOR);
        int round = 1;
        if (index > 0) {
            try {
                round = Integer.parseInt(current.substring(index + 1));
            } catch (NumberFormatException e) {
                round = 1;
            }
        }
        return bizId + String.valueOf(SEPARATOR) + (round + 1);
    }

    /**
     * 从业务 ID 中取出单据主键
     *
     * @return 不是本模块格式的业务 ID 时为 null
     */
    public static Long parseId(String businessId) {
        if (businessId == null || businessId.isBlank()) {
            return null;
        }
        int index = businessId.indexOf(SEPARATOR);
        String id = index < 0 ? businessId : businessId.substring(0, index);
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
