package org.tuoyu.mes.masterdata.service;

import org.springframework.web.multipart.MultipartFile;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesBomBo;
import org.tuoyu.mes.masterdata.domain.vo.MesBomVo;

import java.util.Collection;
import java.util.List;

/**
 * 物料清单（BOM）
 *
 * @author tuoyu
 */
public interface IMesBomService {

    /**
     * 查询物料清单的表头与明细（明细带物料编码、名称与单位），不存在时抛出业务异常
     */
    MesBomVo queryById(Long bomId);

    PageResult<MesBomVo> queryPageList(MesBomBo bo, PageParam pageParam);

    List<MesBomVo> queryList(MesBomBo bo);

    long countList(MesBomBo bo);

    /**
     * 启用的物料清单（下拉选项），可按产品过滤；name 为版本，默认版本在前，parentId 为产品 ID
     */
    List<MesOptionVo> options(Long materialId);

    /**
     * 新增物料清单（表头与明细）。设为默认版本时，同一产品的其他物料清单取消默认
     */
    void insertByBo(MesBomBo bo);

    /**
     * 修改物料清单，明细整体覆盖
     */
    void updateByBo(MesBomBo bo);

    /**
     * 删除物料清单：被工单引用的不能删除
     */
    void deleteByIds(Collection<Long> ids);

    /**
     * 从 Excel 导入物料清单（BOM 编码相同的行为同一个物料清单，子件按物料编码引用）：先整体校验，有任何一行不合格时一行都不写入，并逐行列出错误（第几行、哪个字段、原因）；
     * 全部合格时在一个事务中写入。行数超过 tuoyu.excel.max-import-rows 时直接拒绝
     *
     * @param file 按导入模板填写的文件
     * @return 导入的物料清单数量
     */
    int importData(MultipartFile file);

}
