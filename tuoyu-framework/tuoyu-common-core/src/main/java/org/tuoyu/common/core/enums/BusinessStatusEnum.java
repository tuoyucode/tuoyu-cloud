package org.tuoyu.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.tuoyu.common.core.exception.ServiceException;

import java.util.Arrays;
import java.util.List;

/**
 * 流程单据的业务状态。
 * <p>
 * 校验方法的提示会返回给前端；状态比较区分大小写。
 *
 * @author tuoyu
 */
@Getter
@AllArgsConstructor
public enum BusinessStatusEnum {

    /**
     * 已撤销
     */
    CANCEL("cancel", "已撤销"),

    /**
     * 草稿
     */
    DRAFT("draft", "草稿"),

    /**
     * 待审核
     */
    WAITING("waiting", "待审核"),

    /**
     * 已完成
     */
    FINISH("finish", "已完成"),

    /**
     * 已作废
     */
    INVALID("invalid", "已作废"),

    /**
     * 已退回
     */
    BACK("back", "已退回"),

    /**
     * 已终止
     */
    TERMINATION("termination", "已终止");

    /**
     * 状态码
     */
    private final String status;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按状态码查找。
     *
     * @param status 状态码
     * @return 对应的状态；找不到或参数为 null 时返回 null
     */
    public static BusinessStatusEnum getByStatus(String status) {
        if (status == null) {
            return null;
        }
        for (BusinessStatusEnum value : values()) {
            if (value.status.equals(status)) {
                return value;
            }
        }
        return null;
    }

    /**
     * @param status 状态码
     * @return 是否为草稿、已撤销或已退回
     */
    public static boolean isDraftOrCancelOrBack(String status) {
        return is(status, DRAFT, CANCEL, BACK);
    }

    /**
     * @param status 状态码
     * @return 是否为可以重新发起的状态：已撤销、已退回、已作废、已终止
     */
    public static boolean initialState(String status) {
        return is(status, CANCEL, BACK, INVALID, TERMINATION);
    }

    /**
     * @return 运行中的状态码（草稿、待审核、已退回、已撤销），每次返回新的列表
     */
    public static List<String> runningStatus() {
        return Arrays.asList(DRAFT.status, WAITING.status, BACK.status, CANCEL.status);
    }

    /**
     * @return 已结束的状态码（已完成、已作废、已终止），每次返回新的列表
     */
    public static List<String> finishStatus() {
        return Arrays.asList(FINISH.status, INVALID.status, TERMINATION.status);
    }

    /**
     * 发起流程前校验：待审核、已完成、已作废、已终止的单据不能再发起。
     *
     * @param status 状态码
     * @throws ServiceException 状态不允许发起时
     */
    public static void checkStartStatus(String status) {
        BusinessStatusEnum current = require(status);
        if (current == WAITING) {
            throw new ServiceException("该单据已提交过申请,正在审批中！");
        }
        checkNotEnded(current);
    }

    /**
     * 撤销前校验：已撤销、已完成、已作废、已终止、已退回的单据不能撤销。
     *
     * @param status 状态码
     * @throws ServiceException 状态不允许撤销时
     */
    public static void checkCancelStatus(String status) {
        BusinessStatusEnum current = require(status);
        if (current == CANCEL) {
            throw new ServiceException("该单据已撤销！");
        }
        checkNotEnded(current);
        if (current == BACK) {
            throw new ServiceException("该单据已退回！");
        }
    }

    /**
     * 退回前校验：已退回、已完成、已作废、已终止、已撤销的单据不能退回。
     *
     * @param status 状态码
     * @throws ServiceException 状态不允许退回时
     */
    public static void checkBackStatus(String status) {
        BusinessStatusEnum current = require(status);
        if (current == BACK) {
            throw new ServiceException("该单据已退回！");
        }
        checkNotEnded(current);
        if (current == CANCEL) {
            throw new ServiceException("该单据已撤销！");
        }
    }

    /**
     * 作废、终止前校验：已完成、已作废、已终止的单据不能再作废或终止。
     *
     * @param status 状态码
     * @throws ServiceException 状态不允许时
     */
    public static void checkInvalidStatus(String status) {
        checkNotEnded(require(status));
    }

    private static boolean is(String status, BusinessStatusEnum... candidates) {
        BusinessStatusEnum current = getByStatus(status);
        if (current == null) {
            return false;
        }
        for (BusinessStatusEnum candidate : candidates) {
            if (candidate == current) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 对应的状态；不认识的状态码为 null（校验方法对它不做限制）
     */
    private static BusinessStatusEnum require(String status) {
        if (StringUtils.isBlank(status)) {
            throw new ServiceException("流程状态为空！");
        }
        return getByStatus(status);
    }

    private static void checkNotEnded(BusinessStatusEnum current) {
        if (current == FINISH) {
            throw new ServiceException("该单据已完成申请！");
        }
        if (current == INVALID) {
            throw new ServiceException("该单据已作废！");
        }
        if (current == TERMINATION) {
            throw new ServiceException("该单据已终止！");
        }
    }

}
