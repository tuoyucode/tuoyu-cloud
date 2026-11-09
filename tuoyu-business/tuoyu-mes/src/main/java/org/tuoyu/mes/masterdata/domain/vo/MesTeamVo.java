package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.masterdata.domain.MesTeam;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 班组视图对象 mes_team
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesTeam.class)
public class MesTeamVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long teamId;

    private Long factoryId;

    @ExcelProperty(value = "所属工厂")
    private String factoryName;

    private Long workshopId;

    @ExcelProperty(value = "所属车间")
    private String workshopName;

    @ExcelProperty(value = "班组编码")
    private String teamCode;

    @ExcelProperty(value = "班组名称")
    private String teamName;

    private Long leaderUserId;

    /**
     * 班组长昵称（按 leaderUserId 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "leaderUserId")
    private String leaderUserName;

    /**
     * 成员人数（列表中由服务层填充）
     */
    @ExcelProperty(value = "成员人数")
    private Integer memberCount;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "排序号")
    private Integer sortNo;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

}
