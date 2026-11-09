package org.tuoyu.mes.masterdata.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 班组成员（整体覆盖）：userIds 不能为 null，空数组表示清空成员
 *
 * @author tuoyu
 */
@Data
public class MesTeamMembersBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "成员列表不能为空，清空成员请传空数组")
    @Size(max = 500, message = "一个班组最多 500 名成员")
    private List<@NotNull(message = "用户 ID 不能为空") Long> userIds;

}
