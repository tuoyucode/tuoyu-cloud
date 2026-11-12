package org.tuoyu.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.StateGuard;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.enums.TraceMode;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.trace.domain.MesSn;
import org.tuoyu.mes.trace.domain.bo.MesSnBo;
import org.tuoyu.mes.trace.domain.bo.MesSnGenerateBo;
import org.tuoyu.mes.trace.domain.vo.MesSnVo;
import org.tuoyu.mes.trace.enums.SnStatus;
import org.tuoyu.mes.trace.mapper.MesSnMapper;
import org.tuoyu.mes.trace.service.IMesSnService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 产品序列号
 * <p>
 * 列表、导出、详情、履历与报废按序列号自己的创建部门与创建者限定在当前用户的数据权限内，不在其内的按不存在处理；
 * 生成时读取工单、工单完工时改状态是单据之间的内部操作，不加数据权限。每次状态变化（生成、完工、报废）都写状态履历。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesSnServiceImpl implements IMesSnService {

    static final int MAX_COUNT = 10000;

    private final MesSnMapper snMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesMaterialMapper materialMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;

    @Override
    public MesSnVo queryById(Long snId) {
        MesSnVo vo = BeanMapper.map(requireSn(snId), MesSnVo.class);
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesSnVo> queryPageList(MesSnBo bo, PageParam pageParam) {
        PageResult<MesSnVo> result = PageResults.of(
            snMapper.selectPageInScope(pageParam.toPage(MesSn.class), buildQueryWrapper(bo, !pageParam.hasSort())),
            MesSnVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesSnVo> queryList(MesSnBo bo) {
        List<MesSnVo> list = BeanMapper.mapList(snMapper.selectListInScope(buildQueryWrapper(bo, true)), MesSnVo.class);
        fillNames(list);
        return list;
    }

    @Override
    public long countList(MesSnBo bo) {
        return snMapper.countInScope(buildQueryWrapper(bo, false));
    }

    private LambdaQueryWrapper<MesSn> buildQueryWrapper(MesSnBo bo, boolean defaultOrder) {
        LambdaQueryBuilder<MesSn> builder = QueryBuilder.lambda(MesSn.class)
            .likeIfText(MesSn::getSnCode, bo.getSnCode())
            .eqIfPresent(MesSn::getWorkorderId, bo.getWorkorderId())
            .eqIfText(MesSn::getSnStatus, bo.getSnStatus());
        if (StringUtils.isNotBlank(bo.getWorkorderCode())) {
            builder.inSub(MesSn::getWorkorderId, MesWorkorder.class, sub -> sub
                .select(MesWorkorder::getWorkorderId)
                .like(MesWorkorder::getWorkorderCode, bo.getWorkorderCode().trim()));
        }
        return builder
            .orderByDesc(defaultOrder, MesSn::getWorkorderId)
            .orderByAsc(defaultOrder, MesSn::getSnCode)
            .build();
    }

    /**
     * 批量填充工单编号与产品编码、名称
     */
    private void fillNames(List<MesSnVo> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, MesWorkorder> workorders = byIds(workorderMapper,
            MesLookups.ids(list, MesSnVo::getWorkorderId), MesWorkorder::getWorkorderId);
        Map<Long, MesMaterial> materials = byIds(materialMapper,
            MesLookups.ids(list, MesSnVo::getMaterialId), MesMaterial::getMaterialId);
        for (MesSnVo vo : list) {
            MesWorkorder workorder = workorders.get(vo.getWorkorderId());
            if (workorder != null) {
                vo.setWorkorderCode(workorder.getWorkorderCode());
            }
            MesMaterial material = materials.get(vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generate(MesSnGenerateBo bo) {
        int count = bo.getCount() == null ? 0 : bo.getCount();
        if (count < 1 || count > MAX_COUNT) {
            throw new ServiceException("生成数量应在 1 ~ " + MAX_COUNT + " 之间");
        }
        // 锁住工单行：同一工单并发生成时依次校验数量上限，避免合计超过计划数
        MesWorkorder workorder = bo.getWorkorderId() == null ? null
            : workorderMapper.selectOne(QueryBuilder.lambda(MesWorkorder.class)
                .eq(MesWorkorder::getWorkorderId, bo.getWorkorderId())
                .last("for update")
                .build());
        if (workorder == null) {
            throw new ServiceException("工单不存在");
        }
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), "生成序列号",
            WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED);
        MesMaterial product = materialMapper.selectById(workorder.getMaterialId());
        if (product == null) {
            throw new ServiceException("工单的产品不存在");
        }
        if (!TraceMode.SN.code().equals(product.getTraceMode())) {
            throw new ServiceException("产品“" + product.getMaterialName() + "”不按序列号追溯，不能生成序列号，"
                + "请先在物料中把追溯方式设为“" + TraceMode.SN.label() + "”");
        }
        long existing = snMapper.selectCount(QueryBuilder.lambda(MesSn.class)
            .eq(MesSn::getWorkorderId, workorder.getWorkorderId())
            .ne(MesSn::getSnStatus, SnStatus.SCRAPPED.code())
            .build());
        BigDecimal planQty = workorder.getPlanQty() == null ? BigDecimal.ZERO : workorder.getPlanQty();
        if (BigDecimal.valueOf(existing + count).compareTo(planQty) > 0) {
            BigDecimal remaining = planQty.subtract(BigDecimal.valueOf(existing)).max(BigDecimal.ZERO);
            throw new ServiceException("该工单最多还能生成 " + plain(remaining) + " 个序列号（计划数 " + plain(planQty)
                + "，未报废的序列号 " + existing + " 个）");
        }
        List<String> codes = codeService.next(CodeRules.SN, count);
        List<MesSn> rows = new ArrayList<>(codes.size());
        for (String code : codes) {
            MesSn sn = new MesSn();
            sn.setSnCode(code);
            sn.setWorkorderId(workorder.getWorkorderId());
            sn.setMaterialId(workorder.getMaterialId());
            sn.setBatchCode(workorder.getBatchCode());
            sn.setSnStatus(SnStatus.CREATED.code());
            rows.add(sn);
        }
        snMapper.insert(rows);
        for (MesSn sn : rows) {
            statusLogService.record(BizType.SN, sn.getSnId(), sn.getSnCode(), "create", null, SnStatus.CREATED.code(),
                "工单 " + workorder.getWorkorderCode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completeByWorkorder(Long workorderId) {
        // 加锁读取：与报废同一序列号的操作依次执行，下面的条件更新恰好改这些行，履历中的原状态取自最新数据
        List<MesSn> sns = snMapper.selectList(QueryBuilder.lambda(MesSn.class)
            .select(MesSn::getSnId, MesSn::getSnCode)
            .eq(MesSn::getWorkorderId, workorderId)
            .eq(MesSn::getSnStatus, SnStatus.CREATED.code())
            .last("for update")
            .build());
        if (sns.isEmpty()) {
            return 0;
        }
        snMapper.update(null, Wrappers.<MesSn>lambdaUpdate()
            .set(MesSn::getSnStatus, SnStatus.COMPLETED.code())
            .eq(MesSn::getWorkorderId, workorderId)
            .eq(MesSn::getSnStatus, SnStatus.CREATED.code()));
        for (MesSn sn : sns) {
            statusLogService.record(BizType.SN, sn.getSnId(), sn.getSnCode(), "complete", SnStatus.CREATED.code(),
                SnStatus.COMPLETED.code(), "工单完工");
        }
        return sns.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scrap(Long snId, String reason) {
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("请填写报废原因");
        }
        MesSn sn = requireSn(snId);
        SnStatus from = StateGuard.require("序列号", SnStatus.class, sn.getSnStatus(), "报废",
            SnStatus.CREATED, SnStatus.COMPLETED);
        int rows = snMapper.update(null, Wrappers.<MesSn>lambdaUpdate()
            .set(MesSn::getSnStatus, SnStatus.SCRAPPED.code())
            .eq(MesSn::getSnId, snId)
            .eq(MesSn::getSnStatus, from.code()));
        if (rows == 0) {
            throw new ServiceException("序列号状态已变化，请刷新后重试");
        }
        statusLogService.record(BizType.SN, snId, sn.getSnCode(), "scrap", from.code(), SnStatus.SCRAPPED.code(), reason);
    }

    @Override
    public List<MesStatusLogVo> logs(Long snId) {
        requireSn(snId);
        return statusLogService.list(BizType.SN, snId);
    }

    /**
     * 按 ID 取当前用户数据权限内的序列号，不存在或不在数据权限内时抛出业务异常
     */
    private MesSn requireSn(Long snId) {
        MesSn sn = snId == null ? null : snMapper.selectByIdInScope(snId);
        if (sn == null) {
            throw new ServiceException("序列号不存在");
        }
        return sn;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 按 ID 批量取关联对象；返回可变的 Map，关联 ID 为空时 get(null) 返回空而不是抛出异常
     */
    private static <T> Map<Long, T> byIds(BaseMapper<T> mapper, Collection<Long> ids, Function<T, Long> idOf) {
        return new HashMap<>(MesLookups.byIds(mapper, ids, idOf));
    }

}
