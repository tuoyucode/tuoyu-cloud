package org.tuoyu.mes.masterdata.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;

/**
 * 班组成员：用户 ID 与昵称
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesTeamMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    /**
     * 昵称（按 userId 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "userId")
    private String nickname;

    public MesTeamMemberVo(Long userId) {
        this(userId, null);
    }

}
