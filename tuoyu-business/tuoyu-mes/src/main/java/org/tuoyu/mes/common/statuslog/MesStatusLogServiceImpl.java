package org.tuoyu.mes.common.statuslog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.domain.MesStatusLog;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.mapper.MesStatusLogMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 状态履历
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesStatusLogServiceImpl implements IMesStatusLogService {

    private static final int MAX_REASON = 500;

    private final MesStatusLogMapper statusLogMapper;

    @Override
    public void record(BizType bizType, Long bizId, String bizCode, String action, String from, String to, String reason) {
        MesStatusLog log = new MesStatusLog();
        log.setBizType(bizType.code());
        log.setBizId(bizId);
        log.setBizCode(bizCode);
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setReason(StringUtils.isBlank(reason) ? null : StringUtils.substring(reason, 0, MAX_REASON));
        log.setOperatorUserId(UserContext.current().map(user -> user.getUserId()).orElse(null));
        log.setOperateTime(LocalDateTime.now());
        statusLogMapper.insert(log);
    }

    @Override
    public List<MesStatusLogVo> list(BizType bizType, Long bizId) {
        return BeanMapper.mapList(statusLogMapper.selectList(QueryBuilder.lambda(MesStatusLog.class)
            .eq(MesStatusLog::getBizType, bizType.code())
            .eq(MesStatusLog::getBizId, bizId)
            .orderByAsc(MesStatusLog::getOperateTime, MesStatusLog::getLogId)
            .build()), MesStatusLogVo.class);
    }

}
