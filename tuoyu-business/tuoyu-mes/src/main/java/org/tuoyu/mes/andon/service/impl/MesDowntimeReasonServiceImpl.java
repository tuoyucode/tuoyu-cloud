package org.tuoyu.mes.andon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;
import org.tuoyu.mes.andon.domain.bo.MesDowntimeReasonBo;
import org.tuoyu.mes.andon.domain.vo.MesDowntimeReasonVo;
import org.tuoyu.mes.andon.enums.DowntimeReasonType;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.andon.mapper.MesDowntimeReasonMapper;
import org.tuoyu.mes.andon.service.IMesDowntimeReasonService;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.UniqueCodes;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;

import java.util.Collection;
import java.util.List;

/**
 * 停机原因
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesDowntimeReasonServiceImpl implements IMesDowntimeReasonService {

    private final MesDowntimeReasonMapper reasonMapper;
    private final MesAndonEventMapper andonEventMapper;

    @Override
    public MesDowntimeReasonVo queryById(Long reasonId) {
        MesDowntimeReason reason = reasonId == null ? null : reasonMapper.selectById(reasonId);
        if (reason == null) {
            throw new ServiceException("停机原因不存在");
        }
        return BeanMapper.map(reason, MesDowntimeReasonVo.class);
    }

    @Override
    public PageResult<MesDowntimeReasonVo> queryPageList(MesDowntimeReasonBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesDowntimeReason> lqw = QueryBuilder.lambda(MesDowntimeReason.class)
            .likeIfText(MesDowntimeReason::getReasonCode, bo.getReasonCode())
            .likeIfText(MesDowntimeReason::getReasonName, bo.getReasonName())
            .eqIfText(MesDowntimeReason::getReasonType, bo.getReasonType())
            .eqIfText(MesDowntimeReason::getStatus, bo.getStatus())
            .orderByAsc(!pageParam.hasSort(), MesDowntimeReason::getSortNo)
            .orderByAsc(!pageParam.hasSort(), MesDowntimeReason::getReasonId)
            .build();
        return PageResults.of(reasonMapper.selectPage(pageParam.toPage(MesDowntimeReason.class), lqw),
            MesDowntimeReasonVo.class);
    }

    @Override
    public List<MesOptionVo> options() {
        return reasonMapper.selectList(QueryBuilder.lambda(MesDowntimeReason.class)
                .select(MesDowntimeReason::getReasonId, MesDowntimeReason::getReasonCode, MesDowntimeReason::getReasonName)
                .eq(MesDowntimeReason::getStatus, SystemConstants.NORMAL)
                .orderByAsc(MesDowntimeReason::getSortNo, MesDowntimeReason::getReasonId)
                .build())
            .stream().map(r -> new MesOptionVo(r.getReasonId(), r.getReasonCode(), r.getReasonName())).toList();
    }

    @Override
    public void insertByBo(MesDowntimeReasonBo bo) {
        validate(bo);
        checkCodeUnique(bo.getReasonCode(), null);
        MesDowntimeReason reason = BeanMapper.map(bo, MesDowntimeReason.class);
        reason.setReasonId(null);
        if (reason.getStatus() == null) {
            reason.setStatus(SystemConstants.NORMAL);
        }
        if (reason.getSortNo() == null) {
            reason.setSortNo(0);
        }
        UniqueCodes.write(() -> reasonMapper.insert(reason), duplicateCode(reason.getReasonCode()));
        bo.setReasonId(reason.getReasonId());
    }

    @Override
    public void updateByBo(MesDowntimeReasonBo bo) {
        validate(bo);
        checkCodeUnique(bo.getReasonCode(), bo.getReasonId());
        MesDowntimeReason reason = BeanMapper.map(bo, MesDowntimeReason.class);
        if (UniqueCodes.write(() -> reasonMapper.updateById(reason), duplicateCode(bo.getReasonCode())) == 0) {
            throw new ServiceException("修改停机原因失败，停机原因不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        if (andonEventMapper.exists(QueryBuilder.lambda(MesAndonEvent.class)
            .in(MesAndonEvent::getDowntimeReasonId, ids).build())) {
            throw new ServiceException("停机原因已被安灯事件使用，不能删除，可以改为停用");
        }
        if (reasonMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除停机原因失败，停机原因不存在");
        }
    }

    private static void validate(MesDowntimeReasonBo bo) {
        CodeEnum.of(DowntimeReasonType.class, bo.getReasonType(), "原因分类");
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = reasonMapper.exists(QueryBuilder.lambda(MesDowntimeReason.class)
            .func(UniqueCodes.sameCode(MesDowntimeReason::getReasonCode, code))
            .neIfPresent(MesDowntimeReason::getReasonId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException(duplicateCode(code));
        }
    }

    private static String duplicateCode(String code) {
        return "停机原因编码“" + code + "”已存在";
    }

}
