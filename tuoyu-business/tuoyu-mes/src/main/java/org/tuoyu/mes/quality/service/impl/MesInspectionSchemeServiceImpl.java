package org.tuoyu.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.UniqueCodes;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.material.support.SafeLookup;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.domain.MesInspectionScheme;
import org.tuoyu.mes.quality.domain.bo.MesInspectionSchemeBo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionSchemeVo;
import org.tuoyu.mes.quality.enums.InspectionStatus;
import org.tuoyu.mes.quality.enums.InspectionType;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionSchemeMapper;
import org.tuoyu.mes.quality.service.IMesInspectionSchemeService;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 检验方案
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesInspectionSchemeServiceImpl implements IMesInspectionSchemeService {

    private final MesInspectionSchemeMapper schemeMapper;
    private final MesInspectionMapper inspectionMapper;
    private final MesMaterialMapper materialMapper;

    @Override
    public MesInspectionSchemeVo queryById(Long schemeId) {
        MesInspectionSchemeVo vo = BeanMapper.map(requireScheme(schemeId), MesInspectionSchemeVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesInspectionSchemeVo> queryPageList(MesInspectionSchemeBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesInspectionScheme> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesInspectionSchemeVo> result = PageResults.of(
            schemeMapper.selectPage(pageParam.toPage(MesInspectionScheme.class), lqw), MesInspectionSchemeVo.class);
        fill(result.getItems());
        return result;
    }

    @Override
    public List<MesInspectionSchemeVo> queryList(MesInspectionSchemeBo bo) {
        List<MesInspectionSchemeVo> vos = BeanMapper.mapList(schemeMapper.selectList(buildQueryWrapper(bo, true)),
            MesInspectionSchemeVo.class);
        fill(vos);
        return vos;
    }

    @Override
    public long countList(MesInspectionSchemeBo bo) {
        return schemeMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    private LambdaQueryWrapper<MesInspectionScheme> buildQueryWrapper(MesInspectionSchemeBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesInspectionScheme.class)
            .likeIfText(MesInspectionScheme::getSchemeCode, bo.getSchemeCode())
            .likeIfText(MesInspectionScheme::getSchemeName, bo.getSchemeName())
            .eqIfText(MesInspectionScheme::getInspectionType, bo.getInspectionType())
            .eqIfPresent(MesInspectionScheme::getMaterialId, bo.getMaterialId())
            .likeIfText(MesInspectionScheme::getOperationCode, bo.getOperationCode())
            .eqIfText(MesInspectionScheme::getStatus, bo.getStatus())
            .orderByDesc(defaultOrder, MesInspectionScheme::getSchemeId)
            .build();
    }

    private void fill(List<MesInspectionSchemeVo> vos) {
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(vos, MesInspectionSchemeVo::getMaterialId), MesMaterial::getMaterialId);
        for (MesInspectionSchemeVo vo : vos) {
            MesMaterial material = SafeLookup.get(materials, vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            }
        }
    }

    @Override
    public List<MesOptionVo> options(String inspectionType, Long materialId) {
        return schemeMapper.selectList(QueryBuilder.lambda(MesInspectionScheme.class)
                .select(MesInspectionScheme::getSchemeId, MesInspectionScheme::getSchemeCode,
                    MesInspectionScheme::getSchemeName, MesInspectionScheme::getMaterialId)
                .eq(MesInspectionScheme::getStatus, SystemConstants.NORMAL)
                .eqIfText(MesInspectionScheme::getInspectionType, inspectionType)
                .and(materialId != null, w -> w.isNull(MesInspectionScheme::getMaterialId)
                    .or().eq(MesInspectionScheme::getMaterialId, materialId))
                .orderByAsc(MesInspectionScheme::getSchemeCode, MesInspectionScheme::getSchemeId)
                .build())
            .stream()
            .map(s -> new MesOptionVo(s.getSchemeId(), s.getSchemeCode(), s.getSchemeName(), s.getMaterialId()))
            .toList();
    }

    @Override
    public MesInspectionSchemeVo match(String inspectionType, Long materialId, String operationCode) {
        if (StringUtils.isBlank(inspectionType)) {
            return null;
        }
        List<MesInspectionScheme> candidates = schemeMapper.selectList(QueryBuilder.lambda(MesInspectionScheme.class)
            .eq(MesInspectionScheme::getStatus, SystemConstants.NORMAL)
            .eq(MesInspectionScheme::getInspectionType, inspectionType)
            .and(w -> {
                w.isNull(MesInspectionScheme::getMaterialId);
                if (materialId != null) {
                    w.or().eq(MesInspectionScheme::getMaterialId, materialId);
                }
            })
            .build());
        MesInspectionScheme best = candidates.stream()
            .filter(s -> matchScore(s, materialId, operationCode) >= 0)
            .max(Comparator.<MesInspectionScheme>comparingInt(s -> matchScore(s, materialId, operationCode))
                // 匹配程度相同时取最早建立的方案，结果稳定
                .thenComparing(MesInspectionScheme::getSchemeId, Comparator.reverseOrder()))
            .orElse(null);
        if (best == null) {
            return null;
        }
        MesInspectionSchemeVo vo = BeanMapper.map(best, MesInspectionSchemeVo.class);
        fill(List.of(vo));
        return vo;
    }

    /**
     * 匹配程度：产品匹配记 2 分，工序匹配记 1 分；方案指定了产品或工序但与给定的不同，记 -1（不适用）
     */
    static int matchScore(MesInspectionScheme scheme, Long materialId, String operationCode) {
        int score = 0;
        if (scheme.getMaterialId() != null) {
            if (!scheme.getMaterialId().equals(materialId)) {
                return -1;
            }
            score += 2;
        }
        if (StringUtils.isNotBlank(scheme.getOperationCode())) {
            if (!scheme.getOperationCode().equals(operationCode)) {
                return -1;
            }
            score += 1;
        }
        return score;
    }

    @Override
    public void insertByBo(MesInspectionSchemeBo bo) {
        validate(bo);
        MesInspectionScheme scheme = BeanMapper.map(bo, MesInspectionScheme.class);
        scheme.setSchemeId(null);
        scheme.setOperationCode(StringUtils.trimToNull(bo.getOperationCode()));
        if (scheme.getStatus() == null) {
            scheme.setStatus(SystemConstants.NORMAL);
        }
        UniqueCodes.write(() -> schemeMapper.insert(scheme), duplicateCode(bo.getSchemeCode()));
        bo.setSchemeId(scheme.getSchemeId());
    }

    @Override
    public void updateByBo(MesInspectionSchemeBo bo) {
        validate(bo);
        MesInspectionScheme scheme = new MesInspectionScheme();
        scheme.setSchemeCode(bo.getSchemeCode());
        scheme.setSchemeName(bo.getSchemeName());
        scheme.setInspectionType(bo.getInspectionType());
        scheme.setStatus(bo.getStatus());
        // 可清空的字段显式写入，为空时清空
        int rows = UniqueCodes.write(() -> schemeMapper.update(scheme, Wrappers.<MesInspectionScheme>lambdaUpdate()
            .set(MesInspectionScheme::getMaterialId, bo.getMaterialId())
            .set(MesInspectionScheme::getOperationCode, StringUtils.trimToNull(bo.getOperationCode()))
            .set(MesInspectionScheme::getSampleQty, bo.getSampleQty())
            .set(MesInspectionScheme::getCheckItems, bo.getCheckItems())
            .set(MesInspectionScheme::getRemark, bo.getRemark())
            .eq(MesInspectionScheme::getSchemeId, bo.getSchemeId())), duplicateCode(bo.getSchemeCode()));
        if (rows == 0) {
            throw new ServiceException("修改检验方案失败，检验方案不存在");
        }
    }

    private void validate(MesInspectionSchemeBo bo) {
        CodeEnum.of(InspectionType.class, bo.getInspectionType(), "检验类型");
        boolean exists = schemeMapper.exists(QueryBuilder.lambda(MesInspectionScheme.class)
            .func(UniqueCodes.sameCode(MesInspectionScheme::getSchemeCode, bo.getSchemeCode()))
            .neIfPresent(MesInspectionScheme::getSchemeId, bo.getSchemeId())
            .build());
        if (exists) {
            throw new ServiceException(duplicateCode(bo.getSchemeCode()));
        }
        if (bo.getMaterialId() != null && materialMapper.selectById(bo.getMaterialId()) == null) {
            throw new ServiceException("适用产品不存在");
        }
    }

    private static String duplicateCode(String code) {
        return "方案编码“" + code + "”已存在";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        boolean inUse = inspectionMapper.exists(QueryBuilder.lambda(MesInspection.class)
            .in(MesInspection::getSchemeId, ids)
            .eq(MesInspection::getInspectionStatus, InspectionStatus.PENDING.code())
            .build());
        if (inUse) {
            throw new ServiceException("还有待检验的检验单使用该方案，不能删除");
        }
        if (schemeMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除检验方案失败，检验方案不存在");
        }
    }

    private MesInspectionScheme requireScheme(Long schemeId) {
        MesInspectionScheme scheme = schemeId == null ? null : schemeMapper.selectById(schemeId);
        if (scheme == null) {
            throw new ServiceException("检验方案不存在");
        }
        return scheme;
    }

}
