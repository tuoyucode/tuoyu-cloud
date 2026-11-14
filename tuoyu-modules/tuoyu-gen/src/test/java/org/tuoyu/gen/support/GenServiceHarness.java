package org.tuoyu.gen.support;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.mapper.GenTableColumnMapper;
import org.tuoyu.gen.mapper.GenTableMapper;
import org.tuoyu.gen.service.GenTableServiceImpl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用模拟的 Mapper 构造服务。登记的业务表（{@link #store}）相当于数据库中的数据：
 * <ul>
 *     <li>按 ID 读取业务表（{@code selectById}）时返回新的对象，不含字段；不存在时返回 null；</li>
 *     <li>按条件读取字段（{@code selectList}）时，返回条件参数中出现的业务表 ID 对应的字段（按表 ID、排序号），每次都是新对象。</li>
 * </ul>
 */
public final class GenServiceHarness {

    public final GenTableMapper tableMapper = mock(GenTableMapper.class);

    public final GenTableColumnMapper columnMapper = mock(GenTableColumnMapper.class);

    public final GenTableServiceImpl service = new GenTableServiceImpl(tableMapper, columnMapper);

    private final Map<Long, Supplier<GenTable>> stored = new LinkedHashMap<>();

    public GenServiceHarness() {
        GenTestContext.install();
        when(tableMapper.selectById(anyLong())).thenAnswer(invocation -> {
            Supplier<GenTable> supplier = stored.get(invocation.<Long>getArgument(0));
            if (supplier == null) {
                return null;
            }
            GenTable table = supplier.get();
            table.setColumns(null);
            return table;
        });
        when(columnMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> columnsFor(invocation.getArgument(0)));
    }

    public GenServiceHarness(Supplier<GenTable> table) {
        this();
        store(table);
    }

    /**
     * 登记一张业务表（ID 取自它的 tableId）
     */
    public GenServiceHarness store(Supplier<GenTable> table) {
        stored.put(table.get().getTableId(), table);
        return this;
    }

    /**
     * 登记一张业务表，ID 设为 tableId（字段的 tableId 一并设置）
     */
    public GenServiceHarness store(long tableId, Supplier<GenTable> table) {
        return store(() -> {
            GenTable copy = table.get();
            copy.setTableId(tableId);
            copy.getColumns().forEach(column -> column.setTableId(tableId));
            return copy;
        });
    }

    private List<GenTableColumn> columnsFor(Wrapper<?> wrapper) {
        List<Object> values = new ArrayList<>();
        if (wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper) {
            // 条件参数在生成 SQL 片段时才写入参数表
            abstractWrapper.getSqlSegment();
            for (Object value : abstractWrapper.getParamNameValuePairs().values()) {
                if (value instanceof Collection<?> collection) {
                    values.addAll(collection);
                } else {
                    values.add(value);
                }
            }
        }
        List<GenTableColumn> result = new ArrayList<>();
        stored.forEach((id, supplier) -> {
            if (values.stream().anyMatch(value -> value != null && String.valueOf(id).equals(String.valueOf(value)))) {
                result.addAll(supplier.get().getColumns());
            }
        });
        return result;
    }

    /**
     * 预览：键为模板路径，值为生成的代码（换行统一为 \n）。菜单 ID 从 1001 开始
     */
    public Map<String, String> preview(long tableId) {
        GenTestContext.install();
        GenTestContext.resetIds(1000);
        Map<String, String> result = new LinkedHashMap<>();
        service.previewCode(tableId).forEach((path, code) -> result.put(path, normalize(code)));
        return result;
    }

    /**
     * 下载一张表：键为 zip 中的路径（按写入顺序），值为文件内容（换行统一为 \n）。菜单 ID 从 1001 开始
     */
    public Map<String, String> download(long tableId) {
        GenTestContext.install();
        GenTestContext.resetIds(1000);
        return unzip(service.downloadCode(tableId));
    }

    /**
     * zip 中的文件：键为路径（重名时后者覆盖），值为内容（换行统一为 \n）
     */
    public static Map<String, String> unzip(byte[] zip) {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                files.put(entry.getName(), normalize(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return files;
    }

    /**
     * zip 中的全部条目名（按写入顺序，包括重名的）
     */
    public static List<String> entryNames(byte[] zip) {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return names;
    }

    public static String normalize(String code) {
        return code.replace("\r\n", "\n");
    }
}
