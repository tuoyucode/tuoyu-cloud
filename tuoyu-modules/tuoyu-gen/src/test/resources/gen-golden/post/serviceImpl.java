package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.bo.PostDemoBo;
import org.tuoyu.system.domain.vo.PostDemoVo;
import org.tuoyu.system.domain.PostDemo;
import org.tuoyu.system.mapper.PostDemoMapper;
import org.tuoyu.system.service.IPostDemoService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class PostDemoServiceImpl implements IPostDemoService {

    private final PostDemoMapper postDemoMapper;

    @Override
    public PostDemoVo queryById(Long postId) {
        return BeanMapper.map(postDemoMapper.selectById(postId), PostDemoVo.class);
    }

    @Override
    public PageResult<PostDemoVo> queryPageList(PostDemoBo bo, PageParam pageParam) {
        LambdaQueryWrapper<PostDemo> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        return PageResults.of(postDemoMapper.selectPage(pageParam.toPage(PostDemo.class), lqw), PostDemoVo.class);
    }

    @Override
    public List<PostDemoVo> queryList(PostDemoBo bo) {
        return BeanMapper.mapList(postDemoMapper.selectList(buildQueryWrapper(bo, true)), PostDemoVo.class);
    }

    @Override
    public long countList(PostDemoBo bo) {
        return postDemoMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public boolean checkUnique(PostDemoBo bo) {
        boolean hasUniqueValue = true;
        hasUniqueValue = hasUniqueValue && StringUtils.isNotBlank(bo.getPostCode());
        if (!hasUniqueValue) {
            return true;
        }
        return !postDemoMapper.exists(QueryBuilder.lambda(PostDemo.class)
            .eq(PostDemo::getPostCode, bo.getPostCode())
            .neIfPresent(PostDemo::getPostId, bo.getPostId())
            .build());
    }

    private LambdaQueryWrapper<PostDemo> buildQueryWrapper(PostDemoBo bo, boolean defaultOrder) {
        Map<String, Object> params = bo.getParams();
        return QueryBuilder.lambda(PostDemo.class)
            .likeIfText(PostDemo::getPostCode, bo.getPostCode())
            .likeIfText(PostDemo::getPostName, bo.getPostName())
            .eqIfText(PostDemo::getPostCategory, bo.getPostCategory())
            .eqIfText(PostDemo::getStatus, bo.getStatus())
            .betweenParams(PostDemo::getCreateTime, params, "beginCreateTime", "endCreateTime")
            .orderByAsc(defaultOrder, PostDemo::getPostSort)
            .orderByAsc(defaultOrder, PostDemo::getPostId)
            .build();
    }

    @Override
    public void insertByBo(PostDemoBo bo) {
        if (!checkUnique(bo)) {
            throw new ServiceException("新增岗位失败，组合唯一字段已存在");
        }
        PostDemo add = BeanMapper.map(bo, PostDemo.class);
        validEntityBeforeSave(add);
        postDemoMapper.insert(add);
        bo.setPostId(add.getPostId());
    }

    @Override
    public void updateByBo(PostDemoBo bo) {
        if (!checkUnique(bo)) {
            throw new ServiceException("修改岗位失败，组合唯一字段已存在");
        }
        PostDemo update = BeanMapper.map(bo, PostDemo.class);
        validEntityBeforeSave(update);
        if (postDemoMapper.updateById(update) == 0) {
            throw new ServiceException("修改岗位失败，数据不存在");
        }
    }

    @Override
    public void updateStatus(Long postId, String status) {
        int rows = postDemoMapper.update(Wrappers.<PostDemo>lambdaUpdate()
            .set(PostDemo::getStatus, status)
            .eq(PostDemo::getPostId, postId));
        if (rows == 0) {
            throw new ServiceException("修改岗位状态失败，数据不存在");
        }
    }

    @Override
    public void updateSort(Long postId, Integer sortValue) {
        int rows = postDemoMapper.update(Wrappers.<PostDemo>lambdaUpdate()
            .set(PostDemo::getPostSort, sortValue)
            .eq(PostDemo::getPostId, postId));
        if (rows == 0) {
            throw new ServiceException("调整岗位排序失败，数据不存在");
        }
    }

    private void validEntityBeforeSave(PostDemo entity) {
    }

    @Override
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (Boolean.TRUE.equals(isValid)) {
        }
        if (postDemoMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除岗位失败，数据不存在");
        }
    }

}
