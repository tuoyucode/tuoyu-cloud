package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.excel.ImportErrors;
import org.tuoyu.mes.common.excel.ImportFields;
import org.tuoyu.mes.common.excel.ImportGroups;
import org.tuoyu.mes.common.excel.ImportRow;
import org.tuoyu.mes.common.excel.MesExcelImport;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesRoute;
import org.tuoyu.mes.masterdata.domain.MesRouteOperation;
import org.tuoyu.mes.masterdata.domain.bo.MesRouteBo;
import org.tuoyu.mes.masterdata.domain.bo.MesRouteOperationBo;
import org.tuoyu.mes.masterdata.domain.vo.MesRouteImportVo;
import org.tuoyu.mes.masterdata.domain.vo.MesRouteOperationVo;
import org.tuoyu.mes.masterdata.domain.vo.MesRouteVo;
import org.tuoyu.mes.masterdata.enums.MaterialType;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteOperationMapper;
import org.tuoyu.mes.masterdata.service.IMesRouteService;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 工艺路线
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesRouteServiceImpl implements IMesRouteService {

    private static final String YES = "Y";

    private static final String NO = "N";

    /**
     * 一条工艺路线最多的工序数
     */
    static final int MAX_OPERATIONS = 200;

    private final MesRouteMapper routeMapper;
    private final MesRouteOperationMapper operationMapper;
    private final MesMaterialMapper materialMapper;
    private final MesWorkorderMapper workorderMapper;

    @Override
    public MesRouteVo queryById(Long routeId) {
        MesRoute route = MasterdataSupport.require(routeMapper.selectById(routeId), "工艺路线不存在");
        MesRouteVo vo = BeanMapper.map(route, MesRouteVo.class);
        fillProducts(List.of(vo));
        vo.setOperations(BeanMapper.mapList(listOperations(routeId), MesRouteOperationVo.class));
        return vo;
    }

    private List<MesRouteOperation> listOperations(Long routeId) {
        return operationMapper.selectList(QueryBuilder.lambda(MesRouteOperation.class)
            .eq(MesRouteOperation::getRouteId, routeId)
            .orderByAsc(MesRouteOperation::getSeqNo)
            .build());
    }

    @Override
    public PageResult<MesRouteVo> queryPageList(MesRouteBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesRoute> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesRouteVo> result = PageResults.of(routeMapper.selectPage(pageParam.toPage(MesRoute.class), lqw),
            MesRouteVo.class);
        fillProducts(result.getItems());
        return result;
    }

    @Override
    public List<MesRouteVo> queryList(MesRouteBo bo) {
        List<MesRouteVo> list = BeanMapper.mapList(routeMapper.selectList(buildQueryWrapper(bo, true)),
            MesRouteVo.class);
        fillProducts(list);
        return list;
    }

    @Override
    public long countList(MesRouteBo bo) {
        return routeMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(Long materialId) {
        return routeMapper.selectList(QueryBuilder.lambda(MesRoute.class)
                .select(MesRoute::getRouteId, MesRoute::getRouteCode, MesRoute::getRouteName,
                    MesRoute::getRouteVersion, MesRoute::getMaterialId)
                .eq(MesRoute::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesRoute::getMaterialId, materialId)
                // Y 排在 N 之后，倒序使默认版本在前
                .orderByDesc(MesRoute::getIsDefault)
                .orderByAsc(MesRoute::getRouteId)
                .build())
            .stream()
            .map(r -> new MesOptionVo(r.getRouteId(), r.getRouteCode(), r.getRouteName() + " " + r.getRouteVersion(),
                r.getMaterialId()))
            .toList();
    }

    private LambdaQueryWrapper<MesRoute> buildQueryWrapper(MesRouteBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesRoute.class)
            .eqIfPresent(MesRoute::getMaterialId, bo.getMaterialId())
            .likeIfText(MesRoute::getRouteCode, bo.getRouteCode())
            .likeIfText(MesRoute::getRouteName, bo.getRouteName())
            .likeIfText(MesRoute::getRouteVersion, bo.getRouteVersion())
            .eqIfText(MesRoute::getIsDefault, bo.getIsDefault())
            .eqIfText(MesRoute::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesRoute::getMaterialId)
            .orderByDesc(defaultOrder, MesRoute::getIsDefault)
            .orderByAsc(defaultOrder, MesRoute::getRouteId)
            .build();
    }

    private void fillProducts(List<MesRouteVo> list) {
        MasterdataSupport.fill(list, MesRouteVo::getMaterialId, materialMapper, MesMaterial::getMaterialId,
            (vo, material) -> {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesRouteBo bo) {
        validate(bo);
        MesRoute route = toEntity(bo);
        route.setRouteId(null);
        routeMapper.insert(route);
        bo.setRouteId(route.getRouteId());
        operationMapper.insert(toOperations(route.getRouteId(), bo.getOperations()));
        clearOtherDefaults(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesRouteBo bo) {
        MesRoute current = MasterdataSupport.require(routeMapper.selectById(bo.getRouteId()),
            "修改工艺路线失败，工艺路线不存在");
        validate(bo);
        List<MesRouteOperation> submitted = toOperations(bo.getRouteId(), bo.getOperations());
        boolean used = workorderMapper.exists(QueryBuilder.lambda(MesWorkorder.class)
            .eq(MesWorkorder::getRouteId, bo.getRouteId()).build());
        boolean replaceOperations = true;
        if (used) {
            // 工序任务按工序 ID 复制了工艺路线，已被使用的路线只允许改表头
            boolean productChanged = !Objects.equals(current.getMaterialId(), bo.getMaterialId());
            if (productChanged || !sameOperations(listOperations(bo.getRouteId()), submitted)) {
                throw new ServiceException("已有工单使用该工艺路线，请新建版本");
            }
            replaceOperations = false;
        }
        MesRoute route = toEntity(bo);
        if (routeMapper.updateById(route) == 0) {
            throw new ServiceException("修改工艺路线失败，工艺路线不存在");
        }
        if (replaceOperations) {
            operationMapper.delete(QueryBuilder.lambda(MesRouteOperation.class)
                .eq(MesRouteOperation::getRouteId, bo.getRouteId()).build());
            operationMapper.insert(submitted);
        }
        clearOtherDefaults(route);
    }

    private static MesRoute toEntity(MesRouteBo bo) {
        MesRoute route = BeanMapper.map(bo, MesRoute.class);
        if (route.getIsDefault() == null || route.getIsDefault().isEmpty()) {
            route.setIsDefault(NO);
        }
        route.setStatus(MasterdataSupport.statusOrNormal(route.getStatus()));
        return route;
    }

    /**
     * 校验：编码唯一、同一产品的版本不重复、产品为成品或半成品、至少一道工序、工序顺序大于 0 且不重复
     */
    void validate(MesRouteBo bo) {
        if (bo.getOperations() == null || bo.getOperations().isEmpty()) {
            throw new ServiceException("请至少添加一道工序");
        }
        Set<Integer> seqNos = new HashSet<>();
        for (MesRouteOperationBo operation : bo.getOperations()) {
            if (operation.getSeqNo() == null || operation.getSeqNo() <= 0) {
                throw new ServiceException("工序顺序必须大于 0");
            }
            if (!seqNos.add(operation.getSeqNo())) {
                throw new ServiceException("工序顺序 " + operation.getSeqNo() + " 重复");
            }
        }
        boolean codeExists = routeMapper.exists(QueryBuilder.lambda(MesRoute.class)
            .eq(MesRoute::getRouteCode, bo.getRouteCode())
            .neIfPresent(MesRoute::getRouteId, bo.getRouteId())
            .build());
        if (codeExists) {
            throw new ServiceException("工艺路线编码“" + bo.getRouteCode() + "”已存在");
        }
        boolean versionExists = routeMapper.exists(QueryBuilder.lambda(MesRoute.class)
            .eq(MesRoute::getMaterialId, bo.getMaterialId())
            .eq(MesRoute::getRouteVersion, bo.getRouteVersion())
            .neIfPresent(MesRoute::getRouteId, bo.getRouteId())
            .build());
        if (versionExists) {
            throw new ServiceException("该产品已有版本为“" + bo.getRouteVersion() + "”的工艺路线");
        }
        MesMaterial product = MasterdataSupport.require(materialMapper.selectById(bo.getMaterialId()), "产品不存在");
        if (!CodeEnum.in(product.getMaterialType(), MaterialType.PRODUCT, MaterialType.SEMI)) {
            throw new ServiceException("物料“" + product.getMaterialName() + "”不是成品或半成品，不能建立工艺路线");
        }
    }

    /**
     * 转为工序实体并补齐默认值，按工序顺序排列
     */
    private static List<MesRouteOperation> toOperations(Long routeId, List<MesRouteOperationBo> operations) {
        return operations.stream().map(bo -> {
                MesRouteOperation operation = BeanMapper.map(bo, MesRouteOperation.class);
                operation.setOperationId(null);
                operation.setRouteId(routeId);
                if (operation.getStandardMinutes() == null) {
                    operation.setStandardMinutes(BigDecimal.ZERO);
                }
                if (operation.getFirstInspection() == null || operation.getFirstInspection().isEmpty()) {
                    operation.setFirstInspection(NO);
                }
                operation.setStationType(emptyToNull(operation.getStationType()));
                operation.setRemark(emptyToNull(operation.getRemark()));
                return operation;
            })
            .sorted(Comparator.comparing(MesRouteOperation::getSeqNo))
            .toList();
    }

    /**
     * 两组工序（均按顺序排列）的内容是否相同
     */
    static boolean sameOperations(List<MesRouteOperation> existing, List<MesRouteOperation> submitted) {
        if (existing.size() != submitted.size()) {
            return false;
        }
        for (int i = 0; i < existing.size(); i++) {
            MesRouteOperation a = existing.get(i);
            MesRouteOperation b = submitted.get(i);
            boolean same = Objects.equals(a.getSeqNo(), b.getSeqNo())
                && Objects.equals(a.getOperationCode(), b.getOperationCode())
                && Objects.equals(a.getOperationName(), b.getOperationName())
                && Objects.equals(emptyToNull(a.getStationType()), emptyToNull(b.getStationType()))
                && decimalEquals(a.getStandardMinutes(), b.getStandardMinutes())
                && Objects.equals(a.getFirstInspection(), b.getFirstInspection())
                && Objects.equals(emptyToNull(a.getRemark()), emptyToNull(b.getRemark()));
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private static boolean decimalEquals(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.compareTo(right) == 0;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * 同一产品只能有一个默认版本：本条设为默认时，其他工艺路线取消默认
     */
    private void clearOtherDefaults(MesRoute route) {
        if (!YES.equals(route.getIsDefault())) {
            return;
        }
        routeMapper.update(null, Wrappers.<MesRoute>lambdaUpdate()
            .set(MesRoute::getIsDefault, NO)
            .eq(MesRoute::getMaterialId, route.getMaterialId())
            .eq(MesRoute::getIsDefault, YES)
            .ne(MesRoute::getRouteId, route.getRouteId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        if (workorderMapper.exists(QueryBuilder.lambda(MesWorkorder.class).in(MesWorkorder::getRouteId, ids).build())) {
            throw new ServiceException("已有工单使用该工艺路线，不能删除，可以停用");
        }
        if (routeMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除工艺路线失败，工艺路线不存在");
        }
        operationMapper.delete(QueryBuilder.lambda(MesRouteOperation.class).in(MesRouteOperation::getRouteId, ids).build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importData(MultipartFile file) {
        ImportErrors errors = new ImportErrors();
        List<ImportRow<MesRouteImportVo>> rows = MesExcelImport.read(file, MesRouteImportVo.class, errors);
        Map<String, List<ImportRow<MesRouteImportVo>>> groups = ImportGroups.byCode(rows, MesRouteImportVo::getRouteCode,
            "工艺路线编码", 64, errors);
        Map<String, MesMaterial> products = MesLookups.byCodes(materialMapper, MesMaterial.class,
            MesMaterial::getMaterialCode, rows.stream().map(row -> ImportFields.blankToNull(row.data().getProductCode())).toList());
        Map<String, MesRoute> existingRoutes = MesLookups.byCodes(routeMapper, MesRoute.class, MesRoute::getRouteCode,
            groups.values().stream().map(group -> ImportFields.blankToNull(group.get(0).data().getRouteCode())).toList());
        Set<String> versions = existingVersions(products.values().stream().map(MesMaterial::getMaterialId).toList());
        RouteImportContext context = new RouteImportContext(errors, products, existingRoutes, versions);
        List<ImportedRoute> imported = new ArrayList<>();
        for (List<ImportRow<MesRouteImportVo>> group : groups.values()) {
            ImportedRoute route = parseImportGroup(group, context);
            if (route != null) {
                imported.add(route);
            }
        }
        errors.throwIfAny();
        for (ImportedRoute route : imported) {
            routeMapper.insert(route.header());
            route.operations().forEach(operation -> operation.setRouteId(route.header().getRouteId()));
            operationMapper.insert(route.operations());
            clearOtherDefaults(route.header());
        }
        return imported.size();
    }

    /**
     * 导入中已解析的工艺路线（工序按顺序排列）
     */
    private record ImportedRoute(MesRoute header, List<MesRouteOperation> operations) {
    }

    /**
     * 一次导入中各组共用的查询结果，以及文件内已出现的“产品 + 版本”与默认版本（用于发现文件内的冲突）
     */
    private record RouteImportContext(ImportErrors errors, Map<String, MesMaterial> products,
                                      Map<String, MesRoute> existingRoutes, Set<String> existingVersions,
                                      Map<String, Integer> fileVersions, Map<Long, Integer> fileDefaults) {

        RouteImportContext(ImportErrors errors, Map<String, MesMaterial> products, Map<String, MesRoute> existingRoutes,
                           Set<String> existingVersions) {
            this(errors, products, existingRoutes, existingVersions, new HashMap<>(), new HashMap<>());
        }
    }

    /**
     * 库中这些产品已有的版本，键为“产品 ID|版本（小写）”
     */
    private Set<String> existingVersions(Collection<Long> productIds) {
        Set<String> versions = new HashSet<>();
        for (List<Long> chunk : MesLookups.chunks(productIds)) {
            routeMapper.selectList(QueryBuilder.lambda(MesRoute.class)
                    .select(MesRoute::getMaterialId, MesRoute::getRouteVersion)
                    .in(MesRoute::getMaterialId, chunk)
                    .build())
                .forEach(route -> versions.add(versionKey(route.getMaterialId(), route.getRouteVersion())));
        }
        return versions;
    }

    private static String versionKey(Long materialId, String version) {
        return materialId + "|" + MesLookups.codeKey(version);
    }

    /**
     * 解析同一工艺路线编码的各行：第一行是表头，每行一道工序；有错误时记入 errors 并返回 null
     */
    private static ImportedRoute parseImportGroup(List<ImportRow<MesRouteImportVo>> group, RouteImportContext context) {
        ImportErrors errors = context.errors();
        ImportRow<MesRouteImportVo> first = group.get(0);
        ImportFields head = errors.row(first.rowNumber());
        MesRouteImportVo vo = first.data();
        String routeCode = ImportFields.blankToNull(vo.getRouteCode());
        String owner = "工艺路线编码 " + routeCode + " ";
        if (context.existingRoutes().containsKey(MesLookups.codeKey(routeCode))) {
            head.error("工艺路线编码 " + routeCode + " 已存在");
        }
        MesRoute route = new MesRoute();
        route.setRouteCode(routeCode);
        route.setRouteName(head.text("工艺路线名称", vo.getRouteName(), 128, true));
        MesMaterial product = MasterdataSupport.importProduct(head, vo.getProductCode(), context.products(), "工艺路线");
        route.setMaterialId(product == null ? null : product.getMaterialId());
        route.setRouteVersion(head.text("版本", vo.getRouteVersion(), 32, true));
        route.setIsDefault(head.yesNo("默认版本", vo.getIsDefault()));
        route.setStatus(head.status("状态", vo.getStatus()));
        route.setRemark(head.text("备注", vo.getRemark(), 500, false));
        ImportGroups.sameAsFirst(group, MesRouteImportVo::getRouteName, "工艺路线名称", owner, errors);
        ImportGroups.sameAsFirst(group, MesRouteImportVo::getProductCode, "产品编码", owner, errors);
        ImportGroups.sameAsFirst(group, MesRouteImportVo::getRouteVersion, "版本", owner, errors);
        ImportGroups.sameAsFirst(group, MesRouteImportVo::getIsDefault, "默认版本", owner, errors);
        ImportGroups.sameAsFirst(group, MesRouteImportVo::getStatus, "状态", owner, errors);
        ImportGroups.sameAsFirst(group, MesRouteImportVo::getRemark, "备注", owner, errors);
        if (product != null && route.getRouteVersion() != null) {
            String key = versionKey(product.getMaterialId(), route.getRouteVersion());
            Integer sameInFile = context.fileVersions().putIfAbsent(key, first.rowNumber());
            if (context.existingVersions().contains(key)) {
                head.error("产品 " + product.getMaterialCode() + " 已有版本为“" + route.getRouteVersion() + "”的工艺路线");
            } else if (sameInFile != null) {
                head.error("产品 " + product.getMaterialCode() + " 的版本“" + route.getRouteVersion() + "”与第 "
                    + sameInFile + " 行的工艺路线重复");
            }
            if (YES.equals(route.getIsDefault())) {
                Integer otherDefault = context.fileDefaults().putIfAbsent(product.getMaterialId(), first.rowNumber());
                if (otherDefault != null) {
                    head.error("产品 " + product.getMaterialCode() + " 在文件中有多个默认版本（另见第 " + otherDefault + " 行）");
                }
            }
        }
        if (group.size() > MAX_OPERATIONS) {
            head.error(owner + "的工序超过 " + MAX_OPERATIONS + " 道");
        }
        List<MesRouteOperation> operations = parseImportOperations(group, errors);
        boolean ok = group.stream().noneMatch(row -> errors.hasError(row.rowNumber()));
        return ok ? new ImportedRoute(route, operations) : null;
    }

    private static List<MesRouteOperation> parseImportOperations(List<ImportRow<MesRouteImportVo>> group,
                                                                 ImportErrors errors) {
        Map<Integer, Integer> seqRows = new HashMap<>();
        List<MesRouteOperation> operations = new ArrayList<>(group.size());
        for (ImportRow<MesRouteImportVo> row : group) {
            ImportFields fields = errors.row(row.rowNumber());
            MesRouteImportVo vo = row.data();
            MesRouteOperation operation = new MesRouteOperation();
            Integer seqNo = fields.integer("工序顺序", vo.getSeqNo(), true);
            if (seqNo != null && seqNo <= 0) {
                fields.error("工序顺序必须大于 0");
            } else if (seqNo != null) {
                Integer sameRow = seqRows.putIfAbsent(seqNo, row.rowNumber());
                if (sameRow != null) {
                    fields.error("工序顺序 " + seqNo + " 与第 " + sameRow + " 行重复");
                }
            }
            operation.setSeqNo(seqNo);
            operation.setOperationCode(fields.text("工序编码", vo.getOperationCode(), 64, true));
            operation.setOperationName(fields.text("工序名称", vo.getOperationName(), 128, true));
            operation.setStationType(fields.text("工位类型", vo.getStationType(), 32, false));
            BigDecimal minutes = fields.decimal("标准工时", vo.getStandardMinutes(), false);
            if (minutes != null && minutes.compareTo(BigDecimal.ZERO) < 0) {
                fields.error("标准工时不能小于 0");
            }
            operation.setStandardMinutes(minutes == null ? BigDecimal.ZERO : minutes);
            operation.setFirstInspection(fields.yesNo("首检", vo.getFirstInspection()));
            operation.setRemark(fields.text("工序备注", vo.getOperationRemark(), 500, false));
            operations.add(operation);
        }
        if (operations.stream().anyMatch(operation -> operation.getSeqNo() == null)) {
            return operations;
        }
        return operations.stream().sorted(Comparator.comparing(MesRouteOperation::getSeqNo)).toList();
    }

}
