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
import org.tuoyu.mes.common.UniqueCodes;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.excel.ImportErrors;
import org.tuoyu.mes.common.excel.ImportFields;
import org.tuoyu.mes.common.excel.ImportGroups;
import org.tuoyu.mes.common.excel.ImportRow;
import org.tuoyu.mes.common.excel.MesExcelImport;
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesBomItem;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.bo.MesBomBo;
import org.tuoyu.mes.masterdata.domain.bo.MesBomItemBo;
import org.tuoyu.mes.masterdata.domain.vo.MesBomImportVo;
import org.tuoyu.mes.masterdata.domain.vo.MesBomItemVo;
import org.tuoyu.mes.masterdata.domain.vo.MesBomVo;
import org.tuoyu.mes.masterdata.enums.MaterialType;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.service.IMesBomService;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 物料清单（BOM）
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesBomServiceImpl implements IMesBomService {

    /**
     * 是否默认版本：是
     */
    static final String YES = "Y";

    /**
     * 是否默认版本：否
     */
    static final String NO = "N";

    /**
     * 一个物料清单最多的子件数
     */
    static final int MAX_ITEMS = 500;

    private final MesBomMapper bomMapper;
    private final MesBomItemMapper bomItemMapper;
    private final MesMaterialMapper materialMapper;
    private final MesWorkorderMapper workorderMapper;

    @Override
    public MesBomVo queryById(Long bomId) {
        MesBom bom = MasterdataSupport.require(bomMapper.selectById(bomId), "物料清单不存在");
        MesBomVo vo = BeanMapper.map(bom, MesBomVo.class);
        fillProducts(List.of(vo));
        List<MesBomItemVo> items = BeanMapper.mapList(bomItemMapper.selectList(QueryBuilder.lambda(MesBomItem.class)
            .eq(MesBomItem::getBomId, bomId)
            .orderByAsc(MesBomItem::getSortNo, MesBomItem::getItemId)
            .build()), MesBomItemVo.class);
        MasterdataSupport.fill(items, MesBomItemVo::getMaterialId, materialMapper, MesMaterial::getMaterialId,
            (item, material) -> {
                item.setMaterialCode(material.getMaterialCode());
                item.setMaterialName(material.getMaterialName());
                item.setSpecification(material.getSpecification());
                item.setUnit(material.getUnit());
            });
        vo.setItems(items);
        vo.setUsed(usedByWorkorder(List.of(bomId)));
        return vo;
    }

    @Override
    public PageResult<MesBomVo> queryPageList(MesBomBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesBom> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesBomVo> result = PageResults.of(bomMapper.selectPage(pageParam.toPage(MesBom.class), lqw),
            MesBomVo.class);
        fillProducts(result.getItems());
        return result;
    }

    @Override
    public List<MesBomVo> queryList(MesBomBo bo) {
        List<MesBomVo> list = BeanMapper.mapList(bomMapper.selectList(buildQueryWrapper(bo, true)), MesBomVo.class);
        fillProducts(list);
        return list;
    }

    @Override
    public long countList(MesBomBo bo) {
        return bomMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(Long materialId) {
        return bomMapper.selectList(QueryBuilder.lambda(MesBom.class)
                .select(MesBom::getBomId, MesBom::getBomCode, MesBom::getBomVersion, MesBom::getMaterialId)
                .eq(MesBom::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesBom::getMaterialId, materialId)
                // Y 排在 N 之后，倒序使默认版本在前
                .orderByDesc(MesBom::getIsDefault)
                .orderByAsc(MesBom::getBomId)
                .build())
            .stream()
            .map(b -> new MesOptionVo(b.getBomId(), b.getBomCode(), b.getBomVersion(), b.getMaterialId()))
            .toList();
    }

    private LambdaQueryWrapper<MesBom> buildQueryWrapper(MesBomBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesBom.class)
            .eqIfPresent(MesBom::getMaterialId, bo.getMaterialId())
            .likeIfText(MesBom::getBomCode, bo.getBomCode())
            .likeIfText(MesBom::getBomVersion, bo.getBomVersion())
            .eqIfText(MesBom::getIsDefault, bo.getIsDefault())
            .eqIfText(MesBom::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesBom::getMaterialId)
            .orderByDesc(defaultOrder, MesBom::getIsDefault)
            .orderByAsc(defaultOrder, MesBom::getBomId)
            .build();
    }

    private void fillProducts(List<MesBomVo> list) {
        MasterdataSupport.fill(list, MesBomVo::getMaterialId, materialMapper, MesMaterial::getMaterialId,
            (vo, material) -> {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
                vo.setUnit(material.getUnit());
            });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesBomBo bo) {
        validate(bo);
        MesBom bom = toEntity(bo);
        bom.setBomId(null);
        UniqueCodes.write(() -> bomMapper.insert(bom), duplicateCode(bom.getBomCode()));
        bo.setBomId(bom.getBomId());
        saveItems(bom.getBomId(), bo.getItems());
        clearOtherDefaults(bom);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesBomBo bo) {
        MesBom current = MasterdataSupport.require(bomMapper.selectById(bo.getBomId()),
            "修改物料清单失败，物料清单不存在");
        validate(bo);
        MesBom bom = toEntity(bo);
        boolean replaceItems = true;
        if (usedByWorkorder(List.of(bo.getBomId()))) {
            // 领料按工单引用的物料清单现算需求（明细用量、损耗率与基准数量），已被使用的只允许改表头
            if (!Objects.equals(current.getMaterialId(), bo.getMaterialId())) {
                throw new ServiceException("已有工单使用该物料清单，不能更换产品");
            }
            if (!sameQty(current.getBaseQty(), bom.getBaseQty()) || !sameItems(listItems(bo.getBomId()), bo.getItems())) {
                throw new ServiceException("已有工单使用该物料清单，不能修改基准数量与子件，请新建版本");
            }
            replaceItems = false;
        }
        if (UniqueCodes.write(() -> bomMapper.updateById(bom), duplicateCode(bo.getBomCode())) == 0) {
            throw new ServiceException("修改物料清单失败，物料清单不存在");
        }
        if (replaceItems) {
            bomItemMapper.delete(QueryBuilder.lambda(MesBomItem.class).eq(MesBomItem::getBomId, bom.getBomId()).build());
            saveItems(bom.getBomId(), bo.getItems());
        }
        clearOtherDefaults(bom);
    }

    private List<MesBomItem> listItems(Long bomId) {
        return bomItemMapper.selectList(QueryBuilder.lambda(MesBomItem.class).eq(MesBomItem::getBomId, bomId).build());
    }

    /**
     * 提交的明细与库中的是否相同：按子件物料对应（同一物料清单中子件不重复），比较用量、损耗率与备注；
     * 未填的损耗率按 0 比较，与保存时补齐的默认值一致。排序号只决定显示顺序，不参与比较
     */
    static boolean sameItems(List<MesBomItem> existing, List<MesBomItemBo> submitted) {
        if (existing.size() != submitted.size()) {
            return false;
        }
        Map<Long, MesBomItem> byMaterial = new HashMap<>();
        existing.forEach(item -> byMaterial.put(item.getMaterialId(), item));
        for (MesBomItemBo b : submitted) {
            MesBomItem a = byMaterial.get(b.getMaterialId());
            boolean same = a != null
                && sameQty(a.getQty(), b.getQty())
                && sameQty(lossOrZero(a.getLossRate()), lossOrZero(b.getLossRate()))
                && Objects.equals(emptyToNull(a.getRemark()), emptyToNull(b.getRemark()));
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameQty(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static BigDecimal lossOrZero(BigDecimal lossRate) {
        return lossRate == null ? BigDecimal.ZERO : lossRate;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String duplicateCode(String code) {
        return "BOM 编码“" + code + "”已存在";
    }

    private static MesBom toEntity(MesBomBo bo) {
        MesBom bom = BeanMapper.map(bo, MesBom.class);
        if (bom.getBaseQty() == null) {
            bom.setBaseQty(BigDecimal.ONE);
        }
        if (bom.getIsDefault() == null || bom.getIsDefault().isEmpty()) {
            bom.setIsDefault(NO);
        }
        bom.setStatus(MasterdataSupport.statusOrNormal(bom.getStatus()));
        return bom;
    }

    /**
     * 校验：编码唯一、同一产品的版本不重复、产品为成品或半成品、明细物料存在且不重复、不含产品本身
     */
    void validate(MesBomBo bo) {
        if (bo.getItems() == null || bo.getItems().isEmpty()) {
            throw new ServiceException("请至少添加一个子件");
        }
        boolean codeExists = bomMapper.exists(QueryBuilder.lambda(MesBom.class)
            .func(UniqueCodes.sameCode(MesBom::getBomCode, bo.getBomCode()))
            .neIfPresent(MesBom::getBomId, bo.getBomId())
            .build());
        if (codeExists) {
            throw new ServiceException(duplicateCode(bo.getBomCode()));
        }
        boolean versionExists = bomMapper.exists(QueryBuilder.lambda(MesBom.class)
            .eq(MesBom::getMaterialId, bo.getMaterialId())
            .eq(MesBom::getBomVersion, bo.getBomVersion())
            .neIfPresent(MesBom::getBomId, bo.getBomId())
            .build());
        if (versionExists) {
            throw new ServiceException("该产品已有版本为“" + bo.getBomVersion() + "”的物料清单");
        }
        MesMaterial product = MasterdataSupport.require(materialMapper.selectById(bo.getMaterialId()), "产品不存在");
        if (!CodeEnum.in(product.getMaterialType(), MaterialType.PRODUCT, MaterialType.SEMI)) {
            throw new ServiceException("物料“" + product.getMaterialName() + "”不是成品或半成品，不能建立物料清单");
        }
        Set<Long> seen = new HashSet<>();
        for (MesBomItemBo item : bo.getItems()) {
            if (Objects.equals(item.getMaterialId(), bo.getMaterialId())) {
                throw new ServiceException("子件不能是产品本身");
            }
            if (!seen.add(item.getMaterialId())) {
                throw new ServiceException("子件物料重复，同一物料请合并为一行");
            }
            if (item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ServiceException("子件用量必须大于 0");
            }
            BigDecimal lossRate = item.getLossRate();
            if (lossRate != null && (lossRate.compareTo(BigDecimal.ZERO) < 0 || lossRate.compareTo(BigDecimal.ONE) > 0)) {
                throw new ServiceException("损耗率必须在 0 ~ 1 之间");
            }
        }
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper, seen, MesMaterial::getMaterialId);
        if (materials.size() < seen.size()) {
            throw new ServiceException("部分子件物料不存在，请刷新后重新选择");
        }
    }

    private void saveItems(Long bomId, List<MesBomItemBo> items) {
        List<MesBomItem> entities = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            MesBomItem item = BeanMapper.map(items.get(i), MesBomItem.class);
            item.setItemId(null);
            item.setBomId(bomId);
            if (item.getLossRate() == null) {
                item.setLossRate(BigDecimal.ZERO);
            }
            if (item.getSortNo() == null) {
                item.setSortNo(i + 1);
            }
            entities.add(item);
        }
        bomItemMapper.insert(entities);
    }

    /**
     * 同一产品只能有一个默认版本：本条设为默认时，其他物料清单取消默认
     */
    private void clearOtherDefaults(MesBom bom) {
        if (!YES.equals(bom.getIsDefault())) {
            return;
        }
        bomMapper.update(null, Wrappers.<MesBom>lambdaUpdate()
            .set(MesBom::getIsDefault, NO)
            .eq(MesBom::getMaterialId, bom.getMaterialId())
            .eq(MesBom::getIsDefault, YES)
            .ne(MesBom::getBomId, bom.getBomId()));
    }

    private boolean usedByWorkorder(Collection<Long> bomIds) {
        return workorderMapper.exists(QueryBuilder.lambda(MesWorkorder.class).in(MesWorkorder::getBomId, bomIds).build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        if (usedByWorkorder(ids)) {
            throw new ServiceException("已有工单使用该物料清单，不能删除，可以停用");
        }
        if (bomMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除物料清单失败，物料清单不存在");
        }
        bomItemMapper.delete(QueryBuilder.lambda(MesBomItem.class).in(MesBomItem::getBomId, ids).build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importData(MultipartFile file) {
        ImportErrors errors = new ImportErrors();
        List<ImportRow<MesBomImportVo>> rows = MesExcelImport.read(file, MesBomImportVo.class, errors);
        Map<String, List<ImportRow<MesBomImportVo>>> groups = ImportGroups.byCode(rows, MesBomImportVo::getBomCode,
            "BOM编码", 64, errors);
        List<String> materialCodes = new ArrayList<>();
        rows.forEach(row -> {
            materialCodes.add(ImportFields.blankToNull(row.data().getProductCode()));
            materialCodes.add(ImportFields.blankToNull(row.data().getItemCode()));
        });
        Map<String, MesMaterial> materials = MesLookups.byCodes(materialMapper, MesMaterial.class,
            MesMaterial::getMaterialCode, materialCodes);
        Map<String, MesBom> existingBoms = MesLookups.byCodes(bomMapper, MesBom.class, MesBom::getBomCode,
            groups.values().stream().map(group -> ImportFields.blankToNull(group.get(0).data().getBomCode())).toList());
        Set<String> versions = existingVersions(materials.values().stream().map(MesMaterial::getMaterialId).toList());
        BomImportContext context = new BomImportContext(errors, materials, existingBoms, versions);
        List<ImportedBom> imported = new ArrayList<>();
        for (List<ImportRow<MesBomImportVo>> group : groups.values()) {
            ImportedBom bom = parseImportGroup(group, context);
            if (bom != null) {
                imported.add(bom);
            }
        }
        errors.throwIfAny();
        for (ImportedBom bom : imported) {
            UniqueCodes.write(() -> bomMapper.insert(bom.header()), duplicateCode(bom.header().getBomCode()));
            bom.items().forEach(item -> item.setBomId(bom.header().getBomId()));
            bomItemMapper.insert(bom.items());
            clearOtherDefaults(bom.header());
        }
        return imported.size();
    }

    /**
     * 导入中已解析的物料清单
     */
    private record ImportedBom(MesBom header, List<MesBomItem> items) {
    }

    /**
     * 一次导入中各组共用的查询结果，以及文件内已出现的“产品 + 版本”与默认版本（用于发现文件内的冲突）
     */
    private record BomImportContext(ImportErrors errors, Map<String, MesMaterial> materials,
                                    Map<String, MesBom> existingBoms, Set<String> existingVersions,
                                    Map<String, Integer> fileVersions, Map<Long, Integer> fileDefaults) {

        BomImportContext(ImportErrors errors, Map<String, MesMaterial> materials, Map<String, MesBom> existingBoms,
                         Set<String> existingVersions) {
            this(errors, materials, existingBoms, existingVersions, new HashMap<>(), new HashMap<>());
        }
    }

    /**
     * 库中这些产品已有的版本，键为“产品 ID|版本（小写）”
     */
    private Set<String> existingVersions(Collection<Long> productIds) {
        Set<String> versions = new HashSet<>();
        for (List<Long> chunk : MesLookups.chunks(productIds)) {
            bomMapper.selectList(QueryBuilder.lambda(MesBom.class)
                    .select(MesBom::getMaterialId, MesBom::getBomVersion)
                    .in(MesBom::getMaterialId, chunk)
                    .build())
                .forEach(bom -> versions.add(versionKey(bom.getMaterialId(), bom.getBomVersion())));
        }
        return versions;
    }

    private static String versionKey(Long materialId, String version) {
        return materialId + "|" + MesLookups.codeKey(version);
    }

    /**
     * 解析同一 BOM 编码的各行：第一行是表头，每行一个子件；有错误时记入 errors 并返回 null
     */
    private static ImportedBom parseImportGroup(List<ImportRow<MesBomImportVo>> group, BomImportContext context) {
        ImportErrors errors = context.errors();
        ImportRow<MesBomImportVo> first = group.get(0);
        ImportFields head = errors.row(first.rowNumber());
        MesBomImportVo vo = first.data();
        String bomCode = ImportFields.blankToNull(vo.getBomCode());
        String owner = "BOM编码 " + bomCode + " ";
        if (context.existingBoms().containsKey(MesLookups.codeKey(bomCode))) {
            head.error("BOM编码 " + bomCode + " 已存在");
        }
        MesBom bom = new MesBom();
        bom.setBomCode(bomCode);
        MesMaterial product = MasterdataSupport.importProduct(head, vo.getProductCode(), context.materials(), "物料清单");
        bom.setMaterialId(product == null ? null : product.getMaterialId());
        bom.setBomVersion(head.text("版本", vo.getBomVersion(), 32, true));
        BigDecimal baseQty = head.decimal("基准数量", vo.getBaseQty(), false);
        if (baseQty != null && baseQty.compareTo(BigDecimal.ZERO) <= 0) {
            head.error("基准数量必须大于 0");
        }
        bom.setBaseQty(baseQty == null ? BigDecimal.ONE : baseQty);
        bom.setIsDefault(head.yesNo("默认版本", vo.getIsDefault()));
        bom.setStatus(head.status("状态", vo.getStatus()));
        bom.setRemark(head.text("备注", vo.getRemark(), 500, false));
        ImportGroups.sameAsFirst(group, MesBomImportVo::getProductCode, "产品编码", owner, errors);
        ImportGroups.sameAsFirst(group, MesBomImportVo::getBomVersion, "版本", owner, errors);
        ImportGroups.sameAsFirst(group, MesBomImportVo::getBaseQty, "基准数量", owner, errors);
        ImportGroups.sameAsFirst(group, MesBomImportVo::getIsDefault, "默认版本", owner, errors);
        ImportGroups.sameAsFirst(group, MesBomImportVo::getStatus, "状态", owner, errors);
        ImportGroups.sameAsFirst(group, MesBomImportVo::getRemark, "备注", owner, errors);
        if (product != null && bom.getBomVersion() != null) {
            String key = versionKey(product.getMaterialId(), bom.getBomVersion());
            Integer sameInFile = context.fileVersions().putIfAbsent(key, first.rowNumber());
            if (context.existingVersions().contains(key)) {
                head.error("产品 " + product.getMaterialCode() + " 已有版本为“" + bom.getBomVersion() + "”的物料清单");
            } else if (sameInFile != null) {
                head.error("产品 " + product.getMaterialCode() + " 的版本“" + bom.getBomVersion() + "”与第 " + sameInFile
                    + " 行的物料清单重复");
            }
            if (YES.equals(bom.getIsDefault())) {
                Integer otherDefault = context.fileDefaults().putIfAbsent(product.getMaterialId(), first.rowNumber());
                if (otherDefault != null) {
                    head.error("产品 " + product.getMaterialCode() + " 在文件中有多个默认版本（另见第 " + otherDefault + " 行）");
                }
            }
        }
        if (group.size() > MAX_ITEMS) {
            head.error(owner + "的子件超过 " + MAX_ITEMS + " 个");
        }
        List<MesBomItem> items = parseImportItems(group, product, context);
        boolean ok = group.stream().noneMatch(row -> errors.hasError(row.rowNumber()));
        return ok ? new ImportedBom(bom, items) : null;
    }

    private static List<MesBomItem> parseImportItems(List<ImportRow<MesBomImportVo>> group, MesMaterial product,
                                                     BomImportContext context) {
        Map<Long, Integer> seen = new HashMap<>();
        List<MesBomItem> items = new ArrayList<>(group.size());
        for (int i = 0; i < group.size(); i++) {
            ImportRow<MesBomImportVo> row = group.get(i);
            ImportFields fields = context.errors().row(row.rowNumber());
            MesBomImportVo vo = row.data();
            MesBomItem item = new MesBomItem();
            String itemCode = fields.text("子件物料编码", vo.getItemCode(), 64, true);
            MesMaterial material = itemCode == null ? null : context.materials().get(MesLookups.codeKey(itemCode));
            if (itemCode != null && material == null) {
                fields.error("子件物料编码 " + itemCode + " 不存在");
            }
            if (material != null) {
                if (product != null && Objects.equals(material.getMaterialId(), product.getMaterialId())) {
                    fields.error("子件不能是产品本身");
                }
                Integer sameRow = seen.putIfAbsent(material.getMaterialId(), row.rowNumber());
                if (sameRow != null) {
                    fields.error("子件物料 " + itemCode + " 与第 " + sameRow + " 行重复，同一物料请合并为一行");
                }
                item.setMaterialId(material.getMaterialId());
            }
            BigDecimal qty = fields.decimal("用量", vo.getQty(), true);
            if (qty != null && qty.compareTo(BigDecimal.ZERO) <= 0) {
                fields.error("用量必须大于 0");
            }
            item.setQty(qty);
            BigDecimal lossRate = fields.decimal("损耗率", vo.getLossRate(), false);
            if (lossRate != null && (lossRate.compareTo(BigDecimal.ZERO) < 0 || lossRate.compareTo(BigDecimal.ONE) > 0)) {
                fields.error("损耗率必须在 0 ~ 1 之间");
            }
            item.setLossRate(lossRate == null ? BigDecimal.ZERO : lossRate);
            Integer sortNo = fields.integer("子件排序号", vo.getItemSortNo(), false);
            item.setSortNo(sortNo == null ? i + 1 : sortNo);
            item.setRemark(fields.text("子件备注", vo.getItemRemark(), 500, false));
            items.add(item);
        }
        return items;
    }

}
