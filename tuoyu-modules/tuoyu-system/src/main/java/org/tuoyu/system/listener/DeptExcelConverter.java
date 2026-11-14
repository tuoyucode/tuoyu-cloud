package org.tuoyu.system.listener;

import cn.hutool.core.lang.tree.Tree;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.enums.CellDataTypeEnum;
import org.apache.fesod.sheet.metadata.GlobalConfiguration;
import org.apache.fesod.sheet.metadata.data.ReadCellData;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.metadata.property.ExcelContentProperty;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.TreeBuildUtils;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.service.ISysDeptService;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Excel 中的部门列：单元格写部门的完整路径（从根到该部门的名称用 / 连接，例如 {@code 总部/研发部}），
 * 与部门 ID 互相转换。
 * <p>
 * 路径来自当前用户可见的部门树（受租户与数据权限限制）。映射按“租户 + 用户”缓存 30 秒：一次导入、导出逐行转换时
 * 不重复查询，不同租户、不同数据范围的用户各用各的映射。
 *
 * @author tuoyu
 */
public class DeptExcelConverter implements Converter<Long> {

    /**
     * 路径中各级名称的连接符
     */
    static final String PATH_JOINER = "/";

    private static final Cache<String, DeptPaths> CACHE = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(1000)
        .build();

    @Override
    public Class<Long> supportJavaTypeKey() {
        return Long.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public Long convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
                                  GlobalConfiguration globalConfiguration) {
        String text = cellData == null ? null : cellData.getStringValue();
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Long deptId = currentPaths().pathToId().get(text.trim());
        if (deptId == null) {
            throw new ServiceException("部门不存在：" + text);
        }
        return deptId;
    }

    @Override
    public WriteCellData<?> convertToExcelData(Long value, ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        if (value == null) {
            return new WriteCellData<>("");
        }
        String path = currentPaths().idToPath().get(value);
        return new WriteCellData<>(path == null ? "" : path);
    }

    /**
     * 当前用户可见的部门路径（按租户与用户缓存）
     */
    private static DeptPaths currentPaths() {
        String key = TenantHelper.currentTenantId() + "|" + UserContext.userId();
        return CACHE.get(key, k -> load());
    }

    /**
     * 当前用户可见的部门树中的全部路径（按部门树的遍历顺序）
     */
    static Map<String, Tree<Long>> loadPathNodes() {
        ISysDeptService deptService = SpringUtils.getBean(ISysDeptService.class);
        return TreeBuildUtils.buildTreeNodeMap(deptService.selectDeptTreeList(new SysDeptBo()), PATH_JOINER, Tree::getName);
    }

    private static DeptPaths load() {
        Map<String, Long> pathToId = new LinkedHashMap<>();
        Map<Long, String> idToPath = new LinkedHashMap<>();
        for (Map.Entry<String, Tree<Long>> entry : loadPathNodes().entrySet()) {
            Long deptId = entry.getValue().getId();
            pathToId.put(entry.getKey(), deptId);
            idToPath.put(deptId, entry.getKey());
        }
        return new DeptPaths(Collections.unmodifiableMap(pathToId), Collections.unmodifiableMap(idToPath));
    }

    /**
     * 路径与部门 ID 的双向映射
     */
    private record DeptPaths(Map<String, Long> pathToId, Map<Long, String> idToPath) {
    }

}
