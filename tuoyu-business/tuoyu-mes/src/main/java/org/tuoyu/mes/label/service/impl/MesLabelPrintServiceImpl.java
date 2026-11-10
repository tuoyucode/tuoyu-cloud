package org.tuoyu.mes.label.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.label.domain.bo.MesLabelLimits;
import org.tuoyu.mes.label.domain.bo.MesSnLabelBo;
import org.tuoyu.mes.label.domain.vo.MesLabelDataVo;
import org.tuoyu.mes.label.service.IMesLabelPrintService;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.trace.domain.MesSn;
import org.tuoyu.mes.trace.enums.SnStatus;
import org.tuoyu.mes.trace.mapper.MesSnMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 标签的打印数据
 * <p>
 * 数据权限按工单（创建部门与创建者）：序列号标签只能打印数据权限内工单的序列号，批次标签只能打印数据权限内的工单。
 * 请求中有不存在或无权访问的对象时整体拒绝，不悄悄少打几张（现场按数量贴标，少打不容易发现）。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesLabelPrintServiceImpl implements IMesLabelPrintService {

    /**
     * 提示中最多列出的编号个数
     */
    private static final int MAX_CODES_IN_MESSAGE = 5;

    private final MesSnMapper snMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesMaterialMapper materialMapper;

    private Clock clock = Clock.systemDefaultZone();

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<MesLabelDataVo> snLabels(MesSnLabelBo bo) {
        List<Long> snIds = bo.getSnIds() == null ? List.of() : bo.getSnIds();
        if (!snIds.isEmpty() && bo.getWorkorderId() != null) {
            throw new ServiceException("请选择按勾选的序列号打印，或按工单打印，二者只能选一种");
        }
        List<MesSn> sns;
        Map<Long, MesWorkorder> workorders;
        if (!snIds.isEmpty()) {
            sns = selectedSns(snIds);
            workorders = workordersInScope(sns.stream().map(MesSn::getWorkorderId).collect(Collectors.toSet()));
            Map<Long, MesWorkorder> visible = workorders;
            long requested = snIds.stream().filter(Objects::nonNull).distinct().count();
            long missing = requested - sns.stream().filter(sn -> visible.containsKey(sn.getWorkorderId())).count();
            if (missing > 0) {
                throw new ServiceException("有 " + missing + " 个序列号不存在或无权访问，请刷新列表后重新选择");
            }
            List<String> scrapped = sns.stream().filter(sn -> SnStatus.SCRAPPED.code().equals(sn.getSnStatus()))
                .map(MesSn::getSnCode).toList();
            if (!scrapped.isEmpty()) {
                throw new ServiceException("已报废的序列号不能打印标签：" + codes(scrapped));
            }
        } else if (bo.getWorkorderId() != null) {
            MesWorkorder workorder = workorderMapper.selectByIdInScope(bo.getWorkorderId());
            if (workorder == null) {
                throw new ServiceException("工单不存在");
            }
            sns = snsOfWorkorder(workorder);
            workorders = Map.of(workorder.getWorkorderId(), workorder);
        } else {
            throw new ServiceException("请选择要打印标签的序列号或工单");
        }
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(sns, MesSn::getMaterialId), MesMaterial::getMaterialId);
        LocalDate today = LocalDate.now(clock);
        List<MesLabelDataVo> labels = new ArrayList<>(sns.size());
        for (MesSn sn : sns) {
            MesWorkorder workorder = workorders.get(sn.getWorkorderId());
            MesLabelDataVo label = label(workorder, materials.get(sn.getMaterialId()), today);
            label.setId(sn.getSnId());
            label.setBarcode(sn.getSnCode());
            label.setSnCode(sn.getSnCode());
            label.setBatchCode(sn.getBatchCode());
            labels.add(label);
        }
        return labels;
    }

    /**
     * 勾选的序列号，按勾选的顺序（去掉重复的）
     */
    private List<MesSn> selectedSns(List<Long> snIds) {
        Set<Long> ids = snIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() > MesLabelLimits.MAX_LABELS) {
            throw new ServiceException("一次最多打印 " + MesLabelLimits.MAX_LABELS + " 个标签");
        }
        if (ids.isEmpty()) {
            throw new ServiceException("请选择要打印标签的序列号");
        }
        Map<Long, MesSn> found = snMapper.selectByIds(ids).stream()
            .collect(Collectors.toMap(MesSn::getSnId, Function.identity(), (a, b) -> a));
        return ids.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    /**
     * 工单的全部未报废序列号，按序列号排序；超过上限时提示在列表中勾选后分批打印
     */
    private List<MesSn> snsOfWorkorder(MesWorkorder workorder) {
        long count = snMapper.selectCount(QueryBuilder.lambda(MesSn.class)
            .eq(MesSn::getWorkorderId, workorder.getWorkorderId())
            .ne(MesSn::getSnStatus, SnStatus.SCRAPPED.code())
            .build());
        if (count == 0) {
            throw new ServiceException("工单 " + workorder.getWorkorderCode() + " 还没有可以打印的序列号");
        }
        if (count > MesLabelLimits.MAX_LABELS) {
            throw new ServiceException("工单 " + workorder.getWorkorderCode() + " 有 " + count + " 个序列号，一次最多打印 "
                + MesLabelLimits.MAX_LABELS + " 个，请在列表中勾选后分批打印");
        }
        return snMapper.selectList(QueryBuilder.lambda(MesSn.class)
            .eq(MesSn::getWorkorderId, workorder.getWorkorderId())
            .ne(MesSn::getSnStatus, SnStatus.SCRAPPED.code())
            .orderByAsc(MesSn::getSnCode)
            .build());
    }

    @Override
    public List<MesLabelDataVo> batchLabels(List<Long> workorderIds) {
        Set<Long> ids = workorderIds == null ? Set.of()
            : workorderIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new ServiceException("请选择要打印批次标签的工单");
        }
        if (ids.size() > MesLabelLimits.MAX_LABELS) {
            throw new ServiceException("一次最多打印 " + MesLabelLimits.MAX_LABELS + " 个标签");
        }
        Map<Long, MesWorkorder> workorders = workordersInScope(ids);
        if (workorders.size() < ids.size()) {
            throw new ServiceException("有 " + (ids.size() - workorders.size()) + " 个工单不存在或无权访问，请刷新列表后重新选择");
        }
        List<String> withoutBatch = ids.stream().map(workorders::get)
            .filter(workorder -> StringUtils.isBlank(workorder.getBatchCode()))
            .map(MesWorkorder::getWorkorderCode).toList();
        if (!withoutBatch.isEmpty()) {
            throw new ServiceException("工单没有批次号，不能打印批次标签：" + codes(withoutBatch));
        }
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(workorders.values(), MesWorkorder::getMaterialId), MesMaterial::getMaterialId);
        LocalDate today = LocalDate.now(clock);
        List<MesLabelDataVo> labels = new ArrayList<>(ids.size());
        for (Long id : ids) {
            MesWorkorder workorder = workorders.get(id);
            MesMaterial material = materials.get(workorder.getMaterialId());
            MesLabelDataVo label = label(workorder, material, today);
            label.setId(workorder.getWorkorderId());
            label.setBarcode(workorder.getBatchCode());
            label.setBatchCode(workorder.getBatchCode());
            label.setQuantity(workorder.getPlanQty());
            label.setUnit(material == null ? null : material.getUnit());
            labels.add(label);
        }
        return labels;
    }

    /**
     * 数据权限内的工单：ID → 工单
     */
    private Map<Long, MesWorkorder> workordersInScope(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workorderMapper.selectByIdsInScope(ids).stream()
            .collect(Collectors.toMap(MesWorkorder::getWorkorderId, Function.identity(), (a, b) -> a));
    }

    /**
     * 工单与产品带来的字段：工单号、产品编码与名称、规格、生产日期（实际开工日期，未开工时为当天）
     */
    private static MesLabelDataVo label(MesWorkorder workorder, MesMaterial material, LocalDate today) {
        MesLabelDataVo label = new MesLabelDataVo();
        if (workorder != null) {
            label.setWorkorderCode(workorder.getWorkorderCode());
            label.setProductionDate(workorder.getActualStartTime() == null ? today : workorder.getActualStartTime().toLocalDate());
        } else {
            label.setProductionDate(today);
        }
        if (material != null) {
            label.setMaterialCode(material.getMaterialCode());
            label.setMaterialName(material.getMaterialName());
            label.setSpecification(material.getSpecification());
        }
        return label;
    }

    /**
     * 提示中的编号：最多列出几个，其余写“等 N 个”
     */
    static String codes(List<String> codes) {
        String shown = String.join("、", codes.subList(0, Math.min(codes.size(), MAX_CODES_IN_MESSAGE)));
        return codes.size() > MAX_CODES_IN_MESSAGE ? shown + " 等 " + codes.size() + " 个" : shown;
    }

}
