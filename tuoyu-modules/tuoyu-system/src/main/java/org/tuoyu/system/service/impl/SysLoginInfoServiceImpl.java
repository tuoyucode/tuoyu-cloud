package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.utils.MapstructUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysLoginInfo;
import org.tuoyu.system.domain.bo.SysLoginInfoBo;
import org.tuoyu.system.domain.vo.SysLoginInfoVo;
import org.tuoyu.system.mapper.SysLoginInfoMapper;
import org.tuoyu.system.service.ISysLoginInfoService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 系统访问日志情况信息 服务层处理
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class SysLoginInfoServiceImpl implements ISysLoginInfoService {

    private final SysLoginInfoMapper loginInfoMapper;

    /**
     * 分页查询登录日志列表
     *
     * @param loginInfo 查询条件
     * @param pageQuery 分页参数
     * @return 登录日志分页列表
     */
    @Override
    public PageResult<SysLoginInfoVo> selectPageLoginInfoList(SysLoginInfoBo loginInfo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysLoginInfo> lqw = buildQueryWrapper(loginInfo);
        if (StringUtils.isBlank(pageQuery.getOrderByColumn())) {
            lqw.orderByDesc(SysLoginInfo::getInfoId);
        }
        Page<SysLoginInfoVo> page = loginInfoMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 新增系统登录日志
     *
     * @param bo 访问日志对象
     */
    @Override
    public void insertLoginInfo(SysLoginInfoBo bo) {
        SysLoginInfo loginInfo = MapstructUtils.convert(bo, SysLoginInfo.class);
        loginInfo.setLoginTime(LocalDateTime.now());
        loginInfoMapper.insert(loginInfo);
    }

    /**
     * 查询系统登录日志集合
     *
     * @param loginInfo 访问日志对象
     * @return 登录记录集合
     */
    @Override
    public List<SysLoginInfoVo> selectLoginInfoList(SysLoginInfoBo loginInfo) {
        return loginInfoMapper.selectVoList(buildQueryWrapper(loginInfo)
            .orderByDesc(SysLoginInfo::getInfoId));
    }

    /**
     * 统计符合条件的登录记录数量（导出前先统计，查询条件与列表一致）
     */
    @Override
    public long countLoginInfoList(SysLoginInfoBo loginInfo) {
        return loginInfoMapper.selectVoPage(PageQuery.countOnly(), buildQueryWrapper(loginInfo)).getTotal();
    }

    private LambdaQueryWrapper<SysLoginInfo> buildQueryWrapper(SysLoginInfoBo loginInfo) {
        Map<String, Object> params = loginInfo.getParams();
        return QueryBuilder.lambda(SysLoginInfo.class)
            .likeIfText(SysLoginInfo::getIpaddr, loginInfo.getIpaddr())
            .eqIfText(SysLoginInfo::getStatus, loginInfo.getStatus())
            .likeIfText(SysLoginInfo::getUserName, loginInfo.getUserName())
            .betweenParams(SysLoginInfo::getLoginTime, params, "beginTime", "endTime")
            .build();
    }

    /**
     * 批量删除系统登录日志
     *
     * @param infoIds 需要删除的登录日志ID
     * @return 结果
     */
    @Override
    public int deleteLoginInfoByIds(Long[] infoIds) {
        return loginInfoMapper.deleteByIds(Arrays.asList(infoIds));
    }

    /**
     * 清空系统登录日志
     */
    @Override
    public void cleanLoginInfo() {
        loginInfoMapper.lambda().delete();
    }
}
