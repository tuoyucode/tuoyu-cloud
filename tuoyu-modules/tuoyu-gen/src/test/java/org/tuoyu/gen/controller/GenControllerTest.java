package org.tuoyu.gen.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.service.IGenTableService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 代码生成接口的响应结构：分页结果为 {@code items}、{@code total}，详情为表信息与字段列表
 */
@DisplayName("代码生成接口")
class GenControllerTest {

    private final IGenTableService service = mock(IGenTableService.class);

    private final GenController controller = new GenController(service);

    private static GenTableColumn column(String name) {
        GenTableColumn column = new GenTableColumn();
        column.setColumnName(name);
        return column;
    }

    @Test
    @DisplayName("业务表列表：分页参数原样交给服务，结果放在 data 中")
    void genList() {
        GenTable query = new GenTable();
        PageParam pageParam = new PageParam(2, 20);
        PageResult<GenTable> page = PageResult.of(List.of(new GenTable()), 21);
        when(service.selectPageGenTableList(query, pageParam)).thenReturn(page);

        ApiResult<PageResult<GenTable>> result = controller.genList(query, pageParam);

        assertEquals(ApiResult.SUCCESS, result.getCode());
        assertSame(page, result.getData());
    }

    @Test
    @DisplayName("字段列表：不分页，total 为字段数")
    void columnList() {
        when(service.selectGenTableColumnListByTableId(1L)).thenReturn(List.of(column("id"), column("name")));

        PageResult<GenTableColumn> data = controller.columnList(1L).getData();

        assertEquals(2, data.getTotal());
        assertEquals(List.of("id", "name"), data.getItems().stream().map(GenTableColumn::getColumnName).toList());
    }

    @Test
    @DisplayName("详情：info 为表信息，columns 为字段列表")
    void getInfo() {
        GenTable table = new GenTable();
        table.setTableName("demo");
        List<GenTableColumn> columns = List.of(column("id"));
        when(service.selectGenTableById(1L)).thenReturn(table);
        when(service.selectGenTableColumnListByTableId(1L)).thenReturn(columns);

        Map<String, Object> data = controller.getInfo(1L).getData();

        assertSame(table, data.get("info"));
        assertSame(columns, data.get("columns"));
    }

    @Test
    @DisplayName("修改、删除、同步成功时返回成功，不带数据")
    void writeOperations() {
        GenTable table = new GenTable();

        assertEquals(ApiResult.SUCCESS, controller.editSave(table).getCode());
        verify(service).validateEdit(table);
        verify(service).updateGenTable(table);

        assertEquals(ApiResult.SUCCESS, controller.remove(new Long[]{1L, 2L}).getCode());
        verify(service).deleteGenTableByIds(new Long[]{1L, 2L});

        assertEquals(ApiResult.SUCCESS, controller.synchDb(3L).getCode());
        verify(service).synchDb(3L);
    }

}
