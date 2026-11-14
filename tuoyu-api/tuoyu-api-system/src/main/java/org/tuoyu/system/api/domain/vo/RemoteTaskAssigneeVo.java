package org.tuoyu.system.api.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * 工作流设计器选择办理人的分页结果
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class RemoteTaskAssigneeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 总数
     */
    private Long total = 0L;

    /**
     * 本页的办理人
     */
    private List<TaskHandler> list;

    /**
     * @param total 总数
     * @param list  本页的办理人
     */
    public RemoteTaskAssigneeVo(Long total, List<TaskHandler> list) {
        this.total = total;
        this.list = list;
    }

    /**
     * 把任意来源转换为办理人列表：对每个元素按顺序调用取值函数
     *
     * @param source      来源，为 null 时返回空列表
     * @param storageId   取存储 ID
     * @param handlerCode 取办理人编码
     * @param handlerName 取办理人名称
     * @param groupName   取分组名称
     * @param createTime  取创建时间
     * @param <T>         来源元素类型
     * @return 可修改的新列表
     */
    public static <T> List<TaskHandler> convertToHandlerList(Collection<T> source, Function<T, String> storageId,
                                                             Function<T, String> handlerCode,
                                                             Function<T, String> handlerName,
                                                             Function<T, String> groupName,
                                                             Function<T, LocalDateTime> createTime) {
        List<TaskHandler> handlers = new ArrayList<>();
        if (source == null) {
            return handlers;
        }
        for (T item : source) {
            handlers.add(new TaskHandler(storageId.apply(item), handlerCode.apply(item), handlerName.apply(item),
                groupName.apply(item), createTime.apply(item)));
        }
        return handlers;
    }

    /**
     * 办理人
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskHandler implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 存储 ID（角色、岗位、部门或用户的 ID）
         */
        private String storageId;

        /**
         * 办理人编码
         */
        private String handlerCode;

        /**
         * 办理人名称
         */
        private String handlerName;

        /**
         * 分组名称
         */
        private String groupName;

        /**
         * 创建时间
         */
        private LocalDateTime createTime;

    }

}
