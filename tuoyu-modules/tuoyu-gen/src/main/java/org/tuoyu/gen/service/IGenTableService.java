package org.tuoyu.gen.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;

import java.util.List;
import java.util.Map;

/**
 * 代码生成：业务表的维护、导入与同步表结构、生成代码
 *
 * @author tuoyu
 */
public interface IGenTableService {

    /**
     * @param tableId 业务表编号
     * @return 业务表的字段，按排序号升序
     */
    List<GenTableColumn> selectGenTableColumnListByTableId(Long tableId);

    /**
     * @param genTable  查询条件：数据源、表名与表描述（模糊，忽略大小写）、创建时间区间（params 中的 beginTime、endTime）
     * @param pageParam 分页与排序
     * @return 业务表分页
     */
    PageResult<GenTable> selectPageGenTableList(GenTable genTable, PageParam pageParam);

    /**
     * @param genTable  查询条件：数据源、表名与表注释（包含，忽略大小写）
     * @param pageParam 分页（不支持排序）
     * @return 数据源中尚未导入的表
     */
    PageResult<GenTable> selectPageDbTableList(GenTable genTable, PageParam pageParam);

    /**
     * @param tableNames 表名
     * @param dataName   数据源名称
     * @return 数据源中这些名称的表
     */
    List<GenTable> selectDbTableListByNames(String[] tableNames, String dataName);

    /**
     * @param id 业务表编号
     * @return 业务表（带字段，生成选项拆到同名属性中）
     */
    GenTable selectGenTableById(Long id);

    /**
     * 修改业务表与字段的生成配置
     *
     * @param genTable 业务表（生成选项在 params 中）
     */
    void updateGenTable(GenTable genTable);

    /**
     * 删除业务表与它们的字段
     *
     * @param tableIds 业务表编号
     */
    void deleteGenTableByIds(Long[] tableIds);

    /**
     * 导入表结构
     *
     * @param tableList 数据库中的表
     * @param dataName  数据源名称
     * @return 同一数据源中已导入、因此跳过的表名
     */
    List<String> importGenTable(List<GenTable> tableList, String dataName);

    /**
     * @param tableName 表名
     * @param dataName  数据源名称
     * @return 数据库中这张表的列（按列的顺序）；表不存在时为空列表
     */
    List<GenTableColumn> selectDbTableColumnsByName(String tableName, String dataName);

    /**
     * @param tableId 业务表编号
     * @return 模板路径 → 生成的代码
     */
    Map<String, String> previewCode(Long tableId);

    /**
     * @param tableId 业务表编号
     * @return 生成的代码（zip）
     */
    byte[] downloadCode(Long tableId);

    /**
     * 按数据库中的表结构同步业务表的字段
     *
     * @param tableId 业务表编号
     */
    void synchDb(Long tableId);

    /**
     * @param tableIds 业务表编号
     * @return 各表生成的代码（同一个 zip）
     */
    byte[] downloadCode(String[] tableIds);

    /**
     * 保存前检查生成配置，不满足时抛出带原因的业务异常
     *
     * @param genTable 业务表（生成选项在 params 中）
     */
    void validateEdit(GenTable genTable);
}
