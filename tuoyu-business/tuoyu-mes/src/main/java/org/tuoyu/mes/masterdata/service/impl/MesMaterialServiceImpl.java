package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.tuoyu.mes.common.excel.ImportRow;
import org.tuoyu.mes.common.excel.MesExcelImport;
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesBomItem;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesRoute;
import org.tuoyu.mes.masterdata.domain.bo.MesMaterialBo;
import org.tuoyu.mes.masterdata.domain.vo.MesMaterialImportVo;
import org.tuoyu.mes.masterdata.domain.vo.MesMaterialVo;
import org.tuoyu.mes.masterdata.enums.MaterialType;
import org.tuoyu.mes.masterdata.enums.TraceMode;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.masterdata.service.IMesMaterialService;
import org.tuoyu.mes.material.domain.MesMaterialRequestItem;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesMaterialServiceImpl implements IMesMaterialService {

    private final MesMaterialMapper materialMapper;
    private final MesBomMapper bomMapper;
    private final MesBomItemMapper bomItemMapper;
    private final MesRouteMapper routeMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesMaterialRequestItemMapper requestItemMapper;

    @Override
    public MesMaterialVo queryById(Long materialId) {
        return BeanMapper.map(MasterdataSupport.require(materialMapper.selectById(materialId), "物料不存在"),
            MesMaterialVo.class);
    }

    @Override
    public PageResult<MesMaterialVo> queryPageList(MesMaterialBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesMaterial> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        return PageResults.of(materialMapper.selectPage(pageParam.toPage(MesMaterial.class), lqw), MesMaterialVo.class);
    }

    @Override
    public List<MesMaterialVo> queryList(MesMaterialBo bo) {
        return BeanMapper.mapList(materialMapper.selectList(buildQueryWrapper(bo, true)), MesMaterialVo.class);
    }

    @Override
    public long countList(MesMaterialBo bo) {
        return materialMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(String materialTypes, String keyword) {
        List<String> types = materialTypes == null ? List.of() : Arrays.stream(materialTypes.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
        String text = keyword == null ? null : keyword.trim();
        boolean hasKeyword = text != null && !text.isEmpty();
        return materialMapper.selectList(QueryBuilder.lambda(MesMaterial.class)
                .select(MesMaterial::getMaterialId, MesMaterial::getMaterialCode, MesMaterial::getMaterialName)
                .eq(MesMaterial::getStatus, SystemConstants.NORMAL)
                .inIfNotEmpty(MesMaterial::getMaterialType, types)
                .and(hasKeyword, w -> w.like(MesMaterial::getMaterialCode, text)
                    .or().like(MesMaterial::getMaterialName, text))
                .orderByAsc(MesMaterial::getSortNo, MesMaterial::getMaterialId)
                .last("limit " + OPTION_LIMIT)
                .build())
            .stream()
            .map(m -> new MesOptionVo(m.getMaterialId(), m.getMaterialCode(), m.getMaterialName()))
            .toList();
    }

    private LambdaQueryWrapper<MesMaterial> buildQueryWrapper(MesMaterialBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesMaterial.class)
            .likeIfText(MesMaterial::getMaterialCode, bo.getMaterialCode())
            .likeIfText(MesMaterial::getMaterialName, bo.getMaterialName())
            .likeIfText(MesMaterial::getSpecification, bo.getSpecification())
            .eqIfText(MesMaterial::getMaterialType, bo.getMaterialType())
            .eqIfText(MesMaterial::getTraceMode, bo.getTraceMode())
            .eqIfText(MesMaterial::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesMaterial::getSortNo)
            .orderByAsc(defaultOrder, MesMaterial::getMaterialId)
            .build();
    }

    @Override
    public void insertByBo(MesMaterialBo bo) {
        checkCodeUnique(bo.getMaterialCode(), null);
        MesMaterial material = BeanMapper.map(bo, MesMaterial.class);
        material.setMaterialId(null);
        normalize(material);
        materialMapper.insert(material);
        bo.setMaterialId(material.getMaterialId());
    }

    @Override
    public void updateByBo(MesMaterialBo bo) {
        MasterdataSupport.require(materialMapper.selectById(bo.getMaterialId()), "修改物料失败，物料不存在");
        checkCodeUnique(bo.getMaterialCode(), bo.getMaterialId());
        MesMaterial material = BeanMapper.map(bo, MesMaterial.class);
        normalize(material);
        boolean notProductType = !CodeEnum.in(material.getMaterialType(), MaterialType.PRODUCT, MaterialType.SEMI);
        if (notProductType && usedAsProduct(bo.getMaterialId())) {
            throw new ServiceException("物料已作为产品用于物料清单或工艺路线，类型只能是成品或半成品");
        }
        if (materialMapper.updateById(material) == 0) {
            throw new ServiceException("修改物料失败，物料不存在");
        }
    }

    /**
     * 校验类型与追溯方式，并补齐默认值
     */
    private static void normalize(MesMaterial material) {
        CodeEnum.of(MaterialType.class, material.getMaterialType(), "物料类型");
        if (material.getTraceMode() == null || material.getTraceMode().isEmpty()) {
            material.setTraceMode(TraceMode.NONE.code());
        } else {
            CodeEnum.of(TraceMode.class, material.getTraceMode(), "追溯方式");
        }
        material.setStatus(MasterdataSupport.statusOrNormal(material.getStatus()));
        material.setSortNo(MasterdataSupport.sortOrZero(material.getSortNo()));
    }

    private boolean usedAsProduct(Long materialId) {
        return bomMapper.exists(QueryBuilder.lambda(MesBom.class).eq(MesBom::getMaterialId, materialId).build())
            || routeMapper.exists(QueryBuilder.lambda(MesRoute.class).eq(MesRoute::getMaterialId, materialId).build());
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = materialMapper.exists(QueryBuilder.lambda(MesMaterial.class)
            .eq(MesMaterial::getMaterialCode, code)
            .neIfPresent(MesMaterial::getMaterialId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException("物料编码“" + code + "”已存在");
        }
    }

    @Override
    public void deleteByIds(Collection<Long> ids) {
        if (bomMapper.exists(QueryBuilder.lambda(MesBom.class).in(MesBom::getMaterialId, ids).build())) {
            throw new ServiceException("物料已有物料清单，不能删除");
        }
        if (bomItemMapper.exists(QueryBuilder.lambda(MesBomItem.class)
            .in(MesBomItem::getMaterialId, ids).build())) {
            throw new ServiceException("物料已用作物料清单的子件，不能删除");
        }
        if (routeMapper.exists(QueryBuilder.lambda(MesRoute.class).in(MesRoute::getMaterialId, ids).build())) {
            throw new ServiceException("物料已有工艺路线，不能删除");
        }
        if (workorderMapper.exists(QueryBuilder.lambda(MesWorkorder.class).in(MesWorkorder::getMaterialId, ids).build())) {
            throw new ServiceException("物料已有工单，不能删除，可以停用");
        }
        if (requestItemMapper.exists(QueryBuilder.lambda(MesMaterialRequestItem.class)
            .in(MesMaterialRequestItem::getMaterialId, ids).build())) {
            throw new ServiceException("物料已有领退料记录，不能删除，可以停用");
        }
        if (materialMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除物料失败，物料不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importData(MultipartFile file) {
        ImportErrors errors = new ImportErrors();
        List<ImportRow<MesMaterialImportVo>> rows = MesExcelImport.read(file, MesMaterialImportVo.class, errors);
        Map<String, Integer> firstRowOfCode = new HashMap<>();
        List<ImportRow<MesMaterial>> parsed = new ArrayList<>();
        for (ImportRow<MesMaterialImportVo> row : rows) {
            MesMaterial material = parseImportRow(errors.row(row.rowNumber()), row.data(), firstRowOfCode);
            if (material != null) {
                parsed.add(new ImportRow<>(row.rowNumber(), material));
            }
        }
        Map<String, MesMaterial> existing = MesLookups.byCodes(materialMapper, MesMaterial.class,
            MesMaterial::getMaterialCode, rows.stream().map(row -> ImportFields.blankToNull(row.data().getMaterialCode())).toList());
        for (ImportRow<MesMaterialImportVo> row : rows) {
            String code = ImportFields.blankToNull(row.data().getMaterialCode());
            if (code != null && existing.containsKey(MesLookups.codeKey(code))) {
                errors.add(row.rowNumber(), "物料编码 " + code + " 已存在");
            }
        }
        errors.throwIfAny();
        List<MesMaterial> materials = parsed.stream().map(ImportRow::data).toList();
        materialMapper.insert(materials);
        return materials.size();
    }

    /**
     * 解析一行；有错误时记入 errors 并返回 null。物料编码在文件内重复时提示与哪一行重复
     */
    private static MesMaterial parseImportRow(ImportFields fields, MesMaterialImportVo row,
                                              Map<String, Integer> firstRowOfCode) {
        MesMaterial material = new MesMaterial();
        material.setMaterialCode(fields.text("物料编码", row.getMaterialCode(), 64, true));
        material.setMaterialName(fields.text("物料名称", row.getMaterialName(), 128, true));
        material.setSpecification(fields.text("规格型号", row.getSpecification(), 255, false));
        material.setMaterialType(fields.label("物料类型", row.getMaterialType(), MaterialType.class, null));
        material.setUnit(fields.text("计量单位", row.getUnit(), 32, true));
        material.setTraceMode(fields.label("追溯方式", row.getTraceMode(), TraceMode.class, TraceMode.NONE));
        material.setStatus(fields.status("状态", row.getStatus()));
        Integer sortNo = fields.integer("排序号", row.getSortNo(), false);
        material.setSortNo(MasterdataSupport.sortOrZero(sortNo));
        material.setRemark(fields.text("备注", row.getRemark(), 500, false));
        String code = material.getMaterialCode();
        if (code != null) {
            Integer first = firstRowOfCode.putIfAbsent(MesLookups.codeKey(code), fields.rowNumber());
            if (first != null) {
                fields.error("物料编码 " + code + " 与第 " + first + " 行重复");
            }
        }
        return fields.ok() ? material : null;
    }

}
