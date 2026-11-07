package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageResults;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.ObjectUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysNotice;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.bo.SysNoticeBo;
import org.tuoyu.system.domain.vo.SysNoticeVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.mapper.SysNoticeMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.service.ISysNoticeService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 公告 服务层实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysNoticeServiceImpl implements ISysNoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserMapper userMapper;

    /**
     * 分页查询通知公告列表
     *
     * @param notice    查询条件
     * @param pageParam 分页参数
     * @return 通知公告分页列表
     */
    @Override
    public PageResult<SysNoticeVo> selectPageNoticeList(SysNoticeBo notice, PageParam pageParam) {
        LambdaQueryWrapper<SysNotice> lqw = buildQueryWrapper(notice);
        return PageResults.of(noticeMapper.selectPage(pageParam.toPage(SysNotice.class), lqw), SysNoticeVo.class);
    }

    /**
     * 查询公告信息
     *
     * @param noticeId 公告ID
     * @return 公告信息
     */
    @Override
    public SysNoticeVo selectNoticeById(Long noticeId) {
        return BeanMapper.map(noticeMapper.selectById(noticeId), SysNoticeVo.class);
    }

    /**
     * 查询公告列表
     *
     * @param notice 公告信息
     * @return 公告集合
     */
    @Override
    public List<SysNoticeVo> selectNoticeList(SysNoticeBo notice) {
        LambdaQueryWrapper<SysNotice> lqw = buildQueryWrapper(notice);
        return BeanMapper.mapList(noticeMapper.selectList(lqw), SysNoticeVo.class);
    }

    private LambdaQueryWrapper<SysNotice> buildQueryWrapper(SysNoticeBo bo) {
        LambdaQueryBuilder<SysNotice> builder = QueryBuilder.lambda(SysNotice.class)
            .likeIfText(SysNotice::getNoticeTitle, bo.getNoticeTitle())
            .eqIfText(SysNotice::getNoticeType, bo.getNoticeType());
        if (StringUtils.isNotBlank(bo.getCreateByName())) {
            SysUserVo sysUser = BeanMapper.map(userMapper.selectOne(QueryBuilder.lambda(SysUser.class)
                .eq(SysUser::getUserName, bo.getCreateByName())
                .build()), SysUserVo.class);
            builder.eq(SysNotice::getCreateBy, ObjectUtils.notNullGetter(sysUser, SysUserVo::getUserId));
        }
        return builder.orderByAsc(SysNotice::getNoticeId).build();
    }

    /**
     * 新增公告
     *
     * @param bo 公告信息
     */
    @Override
    public void insertNotice(SysNoticeBo bo) {
        SysNotice notice = BeanMapper.map(bo, SysNotice.class);
        noticeMapper.insert(notice);
        bo.setNoticeId(notice.getNoticeId());
    }

    /**
     * 修改公告
     *
     * @param bo 公告信息
     */
    @Override
    public void updateNotice(SysNoticeBo bo) {
        SysNotice notice = BeanMapper.map(bo, SysNotice.class);
        if (noticeMapper.updateById(notice) == 0) {
            throw new ServiceException("修改公告'{}'失败，公告不存在", bo.getNoticeTitle());
        }
    }

    /**
     * 删除公告对象
     *
     * @param noticeId 公告ID
     */
    @Override
    public void deleteNoticeById(Long noticeId) {
        noticeMapper.deleteById(noticeId);
    }

    /**
     * 批量删除公告信息
     *
     * @param noticeIds 需要删除的公告ID
     */
    @Override
    public void deleteNoticeByIds(Long[] noticeIds) {
        if (noticeMapper.deleteByIds(Arrays.asList(noticeIds)) == 0) {
            throw new ServiceException("删除公告失败，公告不存在");
        }
    }
}
