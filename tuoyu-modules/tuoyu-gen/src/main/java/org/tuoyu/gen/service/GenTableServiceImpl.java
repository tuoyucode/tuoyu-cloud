package org.tuoyu.gen.service;

import cn.hutool.core.convert.Convert;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.anyline.metadata.Column;
import org.anyline.metadata.Table;
import org.anyline.proxy.ServiceProxy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.mybatis.helper.DataBaseHelper;
import org.tuoyu.gen.config.properties.GenProperties;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.mapper.GenTableColumnMapper;
import org.tuoyu.gen.mapper.GenTableMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成：业务表的维护、导入与同步表结构、生成代码。
 * <p>
 * 业务表与字段在主数据源中；读取表结构的方法用 {@code @DS} 切换到业务表所属的数据源，
 * 服务内部经自身代理调用它们，使切换生效。
 *
 * @author tuoyu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenTableServiceImpl implements IGenTableService {

    /**
     * 框架与组件自带的表，不在导入列表中出现
     */
    private static final String[] EXCLUDED_TABLE_PREFIXES = {"sai_", "sj_", "flow_", "gen_"};

    private final GenTableMapper tableMapper;

    private final GenTableColumnMapper columnMapper;

    @Override
    public List<GenTableColumn> selectGenTableColumnListByTableId(Long tableId) {
        return columnMapper.selectList(QueryBuilder.lambda(GenTableColumn.class)
            .eq(GenTableColumn::getTableId, tableId)
            .orderByAsc(GenTableColumn::getSort)
            .build());
    }

    @Override
    public PageResult<GenTable> selectPageGenTableList(GenTable genTable, PageParam pageParam) {
        String dataName = genTable.getDataName();
        String tableName = genTable.getTableName();
        String tableComment = genTable.getTableComment();
        Map<String, Object> params = genTable.getParams() == null ? new HashMap<>() : genTable.getParams();
        Object beginTime = params.get("beginTime");
        Object endTime = params.get("endTime");
        QueryWrapper<GenTable> wrapper = new QueryWrapper<>();
        wrapper.eq(StringUtils.isNotEmpty(dataName), "data_name", dataName)
            .like(StringUtils.isNotBlank(tableName), "lower(table_name)", lowerCase(tableName))
            .like(StringUtils.isNotBlank(tableComment), "lower(table_comment)", lowerCase(tableComment))
            .between(beginTime != null && endTime != null, "create_time", beginTime, endTime)
            .orderByDesc(!pageParam.hasSort(), "update_time");
        Page<GenTable> page = tableMapper.selectPage(pageParam.toPage(GenTable.class), wrapper);
        return PageResults.of(page);
    }

    @DS("#genTable.dataName")
    @Override
    public PageResult<GenTable> selectPageDbTableList(GenTable genTable, PageParam pageParam) {
        String dataName = genTable.getDataName();
        checkDataSource(dataName);
        Map<String, Table<?>> tables = ServiceProxy.metadata().<Table<?>>tables();
        if (tables == null || tables.isEmpty()) {
            return PageResult.empty();
        }
        Set<String> imported = new HashSet<>();
        for (String name : tableMapper.selectTableNameList(dataName)) {
            imported.add(lowerCase(name));
        }
        String nameFilter = genTable.getTableName();
        String commentFilter = genTable.getTableComment();
        List<GenTable> rows = new ArrayList<>();
        for (Table<?> table : tables.values()) {
            String name = table.getName();
            if (excluded(name) || imported.contains(lowerCase(name))) {
                continue;
            }
            if (StringUtils.isNotBlank(nameFilter) && !StringUtils.containsIgnoreCase(name, nameFilter)) {
                continue;
            }
            if (StringUtils.isNotBlank(commentFilter) && !StringUtils.containsIgnoreCase(table.getComment(), commentFilter)) {
                continue;
            }
            rows.add(toGenTable(table, null));
        }
        if (rows.isEmpty()) {
            return PageResult.empty();
        }
        rows.sort(Comparator.comparing(GenTable::getCreateTime).reversed());
        long size = pageParam.size();
        long from = Math.min((pageParam.currentPage() - 1) * size, rows.size());
        long to = Math.min(from + size, rows.size());
        return PageResult.of(new ArrayList<>(rows.subList((int) from, (int) to)), rows.size());
    }

    @DS("#dataName")
    @Override
    public List<GenTable> selectDbTableListByNames(String[] tableNames, String dataName) {
        if (StringUtils.isBlank(dataName)) {
            throw new ServiceException("数据源名称不能为空");
        }
        checkDataSource(dataName);
        if (tableNames == null || tableNames.length == 0) {
            return new ArrayList<>();
        }
        Set<String> names = new HashSet<>(Arrays.asList(tableNames));
        Map<String, Table<?>> tables = ServiceProxy.metadata().<Table<?>>tables();
        List<GenTable> result = new ArrayList<>();
        if (tables == null) {
            return result;
        }
        for (Table<?> table : tables.values()) {
            String name = table.getName();
            if (!excluded(name) && names.contains(name)) {
                result.add(toGenTable(table, dataName));
            }
        }
        return result;
    }

    @Override
    public GenTable selectGenTableById(Long id) {
        GenTable table = loadTable(id);
        GenOptions options = GenOptions.parse(table.getOptions());
        table.setTreeCode(options.string(GenOptions.TREE_CODE));
        table.setTreeParentCode(options.string(GenOptions.TREE_PARENT_CODE));
        table.setTreeName(options.string(GenOptions.TREE_NAME));
        table.setTreeAncestorsField(options.string(GenOptions.TREE_ANCESTORS));
        table.setTreeOrderField(options.string(GenOptions.TREE_ORDER_FIELD));
        table.setTreeRootValue(options.string(GenOptions.TREE_ROOT_VALUE));
        table.setParentMenuName(options.string(GenOptions.PARENT_MENU_NAME));
        table.setStatusField(options.string(GenOptions.STATUS_FIELD));
        table.setSortField(options.string(GenOptions.SORT_FIELD));
        String parentMenuId = options.string(GenOptions.PARENT_MENU_ID);
        table.setParentMenuId(StringUtils.isEmpty(parentMenuId)
            ? Long.valueOf(GenOptions.DEFAULT_PARENT_MENU_ID) : Convert.toLong(parentMenuId));
        table.setEnableExport(options.bool(GenOptions.ENABLE_EXPORT, true));
        table.setEnableStatus(options.bool(GenOptions.ENABLE_STATUS, false));
        table.setEnableUnique(options.bool(GenOptions.ENABLE_UNIQUE, false));
        table.setEnableSort(options.bool(GenOptions.ENABLE_SORT, false));
        table.setUniqueFields(options.stringList(GenOptions.UNIQUE_FIELDS));
        return table;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateGenTable(GenTable genTable) {
        List<GenTableColumn> columns = genTable.getColumns();
        if (columns != null) {
            columns.forEach(GenTableServiceImpl::normalizeDict);
        }
        genTable.setOptions(JsonUtils.toJsonString(genTable.getParams()));
        // 不修改数据源；审计字段由填充写入
        genTable.setDataName(null);
        genTable.setCreateDept(null);
        genTable.setCreateBy(null);
        genTable.setCreateTime(null);
        genTable.setUpdateBy(null);
        genTable.setUpdateTime(null);
        if (tableMapper.updateById(genTable) == 0) {
            throw new ServiceException("业务表不存在");
        }
        if (columns == null || columns.isEmpty()) {
            return;
        }
        Map<Long, GenTableColumn> saved = new HashMap<>();
        for (GenTableColumn column : columnMapper.selectList(QueryBuilder.lambda(GenTableColumn.class)
            .eq(GenTableColumn::getTableId, genTable.getTableId())
            .build())) {
            saved.put(column.getColumnId(), column);
        }
        for (GenTableColumn column : columns) {
            if (column.getColumnId() == null || !saved.containsKey(column.getColumnId())) {
                throw new ServiceException("字段不属于该业务表，请刷新后重试");
            }
        }
        List<GenTableColumn> updates = new ArrayList<>(columns.size());
        for (GenTableColumn column : columns) {
            updates.add(editableCopy(saved.get(column.getColumnId()), column));
        }
        columnMapper.updateById(updates);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteGenTableByIds(Long[] tableIds) {
        List<Long> ids = tableIds == null ? new ArrayList<>() : Arrays.asList(tableIds);
        if (ids.isEmpty() || tableMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除失败，业务表不存在");
        }
        columnMapper.delete(QueryBuilder.lambda(GenTableColumn.class)
            .in(GenTableColumn::getTableId, ids)
            .build());
    }

    @DSTransactional
    @Override
    public List<String> importGenTable(List<GenTable> tableList, String dataName) {
        if (StringUtils.isBlank(dataName)) {
            throw new ServiceException("数据源名称不能为空");
        }
        checkDataSource(dataName);
        List<String> skipped = new ArrayList<>();
        try {
            Set<String> imported = new HashSet<>();
            for (String name : tableMapper.selectTableNameList(dataName)) {
                imported.add(lowerCase(name));
            }
            List<GenTable> tables = new ArrayList<>();
            for (GenTable table : tableList == null ? List.<GenTable>of() : tableList) {
                if (imported.contains(lowerCase(table.getTableName()))) {
                    skipped.add(table.getTableName());
                } else {
                    tables.add(table);
                }
            }
            // 先检查全部表名：有一张不合规时整批不导入
            for (GenTable table : tables) {
                if (!GenConfigChecker.isValidTableName(table.getTableName())) {
                    throw new ServiceException("表 " + table.getTableName()
                        + " 的名称只能包含字母、数字、下划线与 $，且不能以数字开头，不能导入");
                }
            }
            GenProperties properties = SpringUtils.getBean(GenProperties.class);
            IGenTableService self = SpringUtils.getAopProxy(this);
            for (GenTable table : tables) {
                GenTableDefaults.initTable(table, dataName, properties);
                if (tableMapper.insert(table) > 0) {
                    List<GenTableColumn> columns = self.selectDbTableColumnsByName(table.getTableName(), dataName);
                    for (GenTableColumn column : columns) {
                        GenTableDefaults.initColumn(column, table.getTableId());
                    }
                    if (!columns.isEmpty()) {
                        columnMapper.insert(columns);
                    }
                }
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("导入表结构失败，数据源 {}", dataName, e);
            throw new ServiceException("导入失败，请联系管理员");
        }
        return skipped;
    }

    @DS("#dataName")
    @Override
    public List<GenTableColumn> selectDbTableColumnsByName(String tableName, String dataName) {
        Table<?> table = ServiceProxy.metadata().table(tableName);
        List<GenTableColumn> result = new ArrayList<>();
        if (table == null) {
            return result;
        }
        Map<String, Column> columns = table.getColumns();
        if (columns == null) {
            return result;
        }
        for (Column column : columns.values()) {
            GenTableColumn genColumn = new GenTableColumn();
            genColumn.setIsPk(column.isPrimaryKey() ? "1" : "0");
            genColumn.setColumnName(column.getName());
            genColumn.setColumnComment(column.getComment());
            genColumn.setColumnType(column.getOriginType() == null ? null : column.getOriginType().toLowerCase(Locale.ROOT));
            genColumn.setSort(column.getPosition());
            genColumn.setIsRequired(column.isNullable() ? "0" : "1");
            genColumn.setIsIncrement(Boolean.TRUE.equals(column.isAutoIncrement()) ? "1" : "0");
            result.add(genColumn);
        }
        return result;
    }

    @Override
    public Map<String, String> previewCode(Long tableId) {
        Map<String, String> result = new LinkedHashMap<>();
        for (GenCodeGenerator.GeneratedFile file : GenCodeGenerator.generate(loadTable(tableId))) {
            result.put(file.template(), file.content());
        }
        return result;
    }

    @Override
    public byte[] downloadCode(Long tableId) {
        return zip(GenCodeGenerator.generate(loadTable(tableId)));
    }

    @DSTransactional
    @Override
    public void synchDb(Long tableId) {
        GenTable table = loadTable(tableId);
        List<GenTableColumn> savedColumns = table.getColumns();
        List<GenTableColumn> dbColumns = SpringUtils.getAopProxy(this)
            .selectDbTableColumnsByName(table.getTableName(), table.getDataName());
        if (dbColumns == null || dbColumns.isEmpty()) {
            throw new ServiceException("同步数据失败，原表结构不存在");
        }
        Map<String, GenTableColumn> savedByName = new LinkedHashMap<>();
        for (GenTableColumn column : savedColumns) {
            savedByName.putIfAbsent(column.getColumnName(), column);
        }
        Set<String> dbNames = new HashSet<>();
        List<GenTableColumn> result = new ArrayList<>(dbColumns.size());
        for (GenTableColumn column : dbColumns) {
            dbNames.add(column.getColumnName());
            GenTableColumn saved = savedByName.get(column.getColumnName());
            if (saved != null && sameType(saved.getColumnType(), column.getColumnType())) {
                keepSettings(column, saved, table.getTableId());
            } else {
                // 新列，或列类型变了：按默认值重新初始化，已有的字段保留编号
                GenTableDefaults.initColumn(column, table.getTableId());
                column.setColumnId(saved == null ? null : saved.getColumnId());
            }
            normalizeDict(column);
            result.add(column);
        }
        columnMapper.insertOrUpdate(result);
        List<Long> removed = new ArrayList<>();
        for (GenTableColumn column : savedColumns) {
            if (!dbNames.contains(column.getColumnName())) {
                removed.add(column.getColumnId());
            }
        }
        if (!removed.isEmpty()) {
            columnMapper.deleteByIds(removed);
        }
    }

    @Override
    public byte[] downloadCode(String[] tableIds) {
        if (tableIds == null || tableIds.length == 0) {
            throw new ServiceException("请选择要生成的表");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String tableId : tableIds) {
            try {
                ids.add(Long.parseLong(StringUtils.trim(tableId)));
            } catch (NumberFormatException e) {
                throw new ServiceException("业务表编号格式不正确");
            }
        }
        List<GenCodeGenerator.GeneratedFile> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (Long id : ids) {
            for (GenCodeGenerator.GeneratedFile file : GenCodeGenerator.generate(loadTable(id))) {
                if (!paths.add(file.path())) {
                    throw new ServiceException("多张表生成了同名文件 " + file.path() + "，请修改其中一张表的实体类名称或业务名");
                }
                files.add(file);
            }
        }
        return zip(files);
    }

    @Override
    public void validateEdit(GenTable genTable) {
        GenConfigChecker.checkEdit(genTable);
    }

    /**
     * 读取业务表与它的字段（按表编号、排序号升序）
     */
    private GenTable loadTable(Long id) {
        GenTable table = tableMapper.selectById(id);
        if (table == null) {
            throw new ServiceException("业务表不存在");
        }
        List<GenTableColumn> columns = columnMapper.selectList(QueryBuilder.lambda(GenTableColumn.class)
            .eq(GenTableColumn::getTableId, id)
            .orderByAsc(GenTableColumn::getTableId, GenTableColumn::getSort)
            .build());
        table.setColumns(columns == null ? new ArrayList<>() : new ArrayList<>(columns));
        return table;
    }

    /**
     * 列类型不变：以数据库中的列为基础，保留用户在已保存字段上的配置
     */
    private static void keepSettings(GenTableColumn column, GenTableColumn saved, Long tableId) {
        column.setColumnId(saved.getColumnId());
        column.setTableId(tableId);
        column.setJavaField(saved.getJavaField());
        column.setJavaType(saved.getJavaType());
        column.setHtmlType(saved.getHtmlType());
        column.setQueryType(saved.getQueryType());
        column.setDictType(saved.getDictType());
        column.setIsInsert(saved.getIsInsert());
        column.setIsEdit(saved.getIsEdit());
        column.setIsList(saved.getIsList());
        column.setIsQuery(saved.getIsQuery());
        column.setIsRequired(saved.getIsRequired());
        column.setSort(saved.getSort());
    }

    /**
     * 以已保存的字段为基础，只取请求中可编辑的属性；列名、列类型、主键、自增、归属表与排序号保持已保存的值
     */
    private static GenTableColumn editableCopy(GenTableColumn saved, GenTableColumn request) {
        GenTableColumn column = new GenTableColumn();
        column.setColumnId(saved.getColumnId());
        column.setTableId(saved.getTableId());
        column.setColumnName(saved.getColumnName());
        column.setColumnType(saved.getColumnType());
        column.setIsPk(saved.getIsPk());
        column.setIsIncrement(saved.getIsIncrement());
        column.setSort(saved.getSort());
        column.setColumnComment(request.getColumnComment());
        column.setJavaType(request.getJavaType());
        column.setJavaField(request.getJavaField());
        column.setIsInsert(request.getIsInsert());
        column.setIsEdit(request.getIsEdit());
        column.setIsList(request.getIsList());
        column.setIsQuery(request.getIsQuery());
        column.setIsRequired(request.getIsRequired());
        column.setQueryType(request.getQueryType());
        column.setHtmlType(request.getHtmlType());
        column.setDictType(request.getDictType());
        return column;
    }

    /**
     * 显示类型不能选择字典时清空字典类型
     */
    private static void normalizeDict(GenTableColumn column) {
        if (!column.isDictColumn()) {
            column.setDictType("");
        }
    }

    private static boolean sameType(String saved, String current) {
        return StringUtils.equalsIgnoreCase(StringUtils.trim(saved), StringUtils.trim(current));
    }

    /**
     * 数据源名称不为空白时必须是已配置的数据源
     */
    private static void checkDataSource(String dataName) {
        if (StringUtils.isNotBlank(dataName) && !DataBaseHelper.getDataSourceNameList().contains(dataName)) {
            throw new ServiceException("数据源不存在");
        }
    }

    private static boolean excluded(String tableName) {
        return StringUtils.startWithAnyIgnoreCase(tableName, EXCLUDED_TABLE_PREFIXES);
    }

    private static String lowerCase(String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static GenTable toGenTable(Table<?> table, String dataName) {
        GenTable genTable = new GenTable();
        genTable.setDataName(dataName);
        genTable.setTableName(table.getName());
        genTable.setTableComment(table.getComment());
        LocalDateTime createTime = toLocalDateTime(table.getCreateTime());
        genTable.setCreateTime(createTime == null ? LocalDateTime.now() : createTime);
        genTable.setUpdateTime(toLocalDateTime(table.getUpdateTime()));
        return genTable;
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        // java.sql.Date 不支持 toInstant，按毫秒数转换
        return date == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
    }

    private static byte[] zip(List<GenCodeGenerator.GeneratedFile> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (GenCodeGenerator.GeneratedFile file : files) {
                zip.putNextEntry(new ZipEntry(file.path()));
                zip.write(file.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
