package org.tuoyu.mes.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 班组 mes_team
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_team")
public class MesTeam extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "team_id")
    private Long teamId;

    /**
     * 所属工厂
     */
    private Long factoryId;

    /**
     * 所属车间
     */
    private Long workshopId;

    /**
     * 班组编码
     */
    private String teamCode;

    /**
     * 班组名称
     */
    private String teamName;

    /**
     * 班组长（用户）
     */
    private Long leaderUserId;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 排序号
     */
    private Integer sortNo;

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
