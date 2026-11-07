package org.tuoyu.system.service.impl;

import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageResults;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysOperLog;
import org.tuoyu.system.domain.bo.SysOperLogBo;
import org.tuoyu.system.domain.vo.SysOperLogVo;
import org.tuoyu.system.mapper.SysOperLogMapper;
import org.tuoyu.system.service.ISysOperLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 操作日志 服务层处理
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysOperLogServiceImpl implements ISysOperLogService {

    private final SysOperLogMapper operLogMapper;

    /**
     * 分页查询操作日志列表
     *
     * @param operLog   查询条件
     * @param pageParam 分页参数
     * @return 操作日志分页列表
     */
    @Override
    public PageResult<SysOperLogVo> selectPageOperLogList(SysOperLogBo operLog, PageParam pageParam) {
        LambdaQueryWrapper<SysOperLog> lqw = buildQueryWrapper(operLog);
        if (!pageParam.hasSort()) {
            lqw.orderByDesc(SysOperLog::getOperId);
        }
        return PageResults.of(operLogMapper.selectPage(pageParam.toPage(SysOperLog.class), lqw), SysOperLogVo.class);
    }

    private LambdaQueryWrapper<SysOperLog> buildQueryWrapper(SysOperLogBo operLog) {
        Map<String, Object> params = operLog.getParams();
        return QueryBuilder.lambda(SysOperLog.class)
            .likeIfText(SysOperLog::getOperIp, operLog.getOperIp())
            .likeIfText(SysOperLog::getTitle, operLog.getTitle())
            .eq(operLog.getBusinessType() != null && operLog.getBusinessType() > 0,
                SysOperLog::getBusinessType, operLog.getBusinessType())
            .func(f -> {
                if (ArrayUtil.isNotEmpty(operLog.getBusinessTypes())) {
                    f.in(SysOperLog::getBusinessType, Arrays.asList(operLog.getBusinessTypes()));
                }
            })
            .eqIfPresent(SysOperLog::getStatus, operLog.getStatus())
            .likeIfText(SysOperLog::getOperName, operLog.getOperName())
            .eqIfPresent(SysOperLog::getUserId, operLog.getUserId())
            .eqIfPresent(SysOperLog::getDeptId, operLog.getDeptId())
            .eqIfText(SysOperLog::getClientKey, operLog.getClientKey())
            .eqIfText(SysOperLog::getDeviceType, operLog.getDeviceType())
            .likeIfText(SysOperLog::getBrowser, operLog.getBrowser())
            .likeIfText(SysOperLog::getOs, operLog.getOs())
            .betweenParams(SysOperLog::getOperTime, params, "beginTime", "endTime")
            .build();
    }

    /**
     * 新增操作日志
     *
     * @param bo 操作日志对象
     */
    @Override
    public void insertOperlog(SysOperLogBo bo) {
        SysOperLog operLog = BeanMapper.map(bo, SysOperLog.class);
        operLog.setOperTime(LocalDateTime.now());
        operLogMapper.insert(operLog);
    }

    /**
     * 查询系统操作日志集合
     *
     * @param operLog 操作日志对象
     * @return 操作日志集合
     */
    @Override
    public List<SysOperLogVo> selectOperLogList(SysOperLogBo operLog) {
        LambdaQueryWrapper<SysOperLog> lqw = buildQueryWrapper(operLog);
        return BeanMapper.mapList(operLogMapper.selectList(lqw.orderByDesc(SysOperLog::getOperId)), SysOperLogVo.class);
    }

    /**
     * 统计符合条件的操作日志数量（导出前先统计，查询条件与列表一致）
     */
    @Override
    public long countOperLogList(SysOperLogBo operLog) {
        return operLogMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(operLog)).getTotal();
    }

    /**
     * 批量删除系统操作日志
     *
     * @param operIds 需要删除的操作日志ID
     */
    @Override
    public void deleteOperLogByIds(Long[] operIds) {
        if (operLogMapper.deleteByIds(Arrays.asList(operIds)) == 0) {
            throw new ServiceException("删除操作日志失败，记录不存在");
        }
    }

    /**
     * 查询操作日志详细
     *
     * @param operId 操作ID
     * @return 操作日志对象
     */
    @Override
    public SysOperLogVo selectOperLogById(Long operId) {
        return BeanMapper.map(operLogMapper.selectById(operId), SysOperLogVo.class);
    }

    /**
     * 清空操作日志
     */
    @Override
    public void cleanOperLog() {
        operLogMapper.delete(Wrappers.emptyWrapper());
    }
}
