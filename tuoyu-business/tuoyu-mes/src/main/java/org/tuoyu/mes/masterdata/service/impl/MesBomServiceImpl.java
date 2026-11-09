package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesBomItem;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.bo.MesBomBo;
import org.tuoyu.mes.masterdata.domain.bo.MesBomItemBo;
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
        bomMapper.insert(bom);
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
        if (!Objects.equals(current.getMaterialId(), bo.getMaterialId()) && usedByWorkorder(List.of(bo.getBomId()))) {
            throw new ServiceException("已有工单使用该物料清单，不能更换产品");
        }
        MesBom bom = toEntity(bo);
        if (bomMapper.updateById(bom) == 0) {
            throw new ServiceException("修改物料清单失败，物料清单不存在");
        }
        bomItemMapper.delete(QueryBuilder.lambda(MesBomItem.class).eq(MesBomItem::getBomId, bom.getBomId()).build());
        saveItems(bom.getBomId(), bo.getItems());
        clearOtherDefaults(bom);
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
            .eq(MesBom::getBomCode, bo.getBomCode())
            .neIfPresent(MesBom::getBomId, bo.getBomId())
            .build());
        if (codeExists) {
            throw new ServiceException("BOM 编码“" + bo.getBomCode() + "”已存在");
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

}
