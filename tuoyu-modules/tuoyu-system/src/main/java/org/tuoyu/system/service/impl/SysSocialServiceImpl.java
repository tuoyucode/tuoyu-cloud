package org.tuoyu.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysSocial;
import org.tuoyu.system.domain.bo.SysSocialBo;
import org.tuoyu.system.domain.vo.SysSocialVo;
import org.tuoyu.system.mapper.SysSocialMapper;
import org.tuoyu.system.service.ISysSocialService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 社会化关系Service业务层处理
 *
 * @author thiszhc
 * @date 2023-06-12
 */
@RequiredArgsConstructor
@Service
public class SysSocialServiceImpl implements ISysSocialService {

    private final SysSocialMapper socialMapper;

    /**
     * 根据ID查询社会化关系
     *
     * @param id 社会化关系的唯一标识符
     * @return 返回与给定ID对应的SysSocialVo对象，如果未找到则返回null
     */
    @Override
    public SysSocialVo queryById(String id) {
        return BeanMapper.map(socialMapper.selectById(id), SysSocialVo.class);
    }

    /**
     * 查询社会化关系列表
     *
     * @param bo 用于过滤查询条件的SysSocialBo对象
     * @return 返回符合条件的SysSocialVo对象列表
     */
    @Override
    public List<SysSocialVo> queryList(SysSocialBo bo) {
        return BeanMapper.mapList(socialMapper.selectList(QueryBuilder.lambda(SysSocial.class)
            .eqIfPresent(SysSocial::getUserId, bo.getUserId())
            .eqIfText(SysSocial::getAuthId, bo.getAuthId())
            .eqIfText(SysSocial::getSource, bo.getSource())
            .build()), SysSocialVo.class);
    }

    /**
     * 根据用户ID查询社会化关系列表
     *
     * @param userId 用户的唯一标识符
     * @return 返回与给定用户ID相关联的SysSocialVo对象列表
     */
    @Override
    public List<SysSocialVo> queryListByUserId(Long userId) {
        return BeanMapper.mapList(socialMapper.selectList(QueryBuilder.lambda(SysSocial.class).eq(SysSocial::getUserId, userId).build()), SysSocialVo.class);
    }

    /**
     * 新增授权关系
     *
     * @param bo 包含新增授权关系信息的SysSocialBo对象
     */
    @Override
    public void insertByBo(SysSocialBo bo) {
        SysSocial add = BeanMapper.map(bo, SysSocial.class);
        validEntityBeforeSave(add);
        socialMapper.insert(add);
        bo.setId(add.getId());
    }

    /**
     * 更新社会化关系
     *
     * @param bo 包含更新信息的SysSocialBo对象
     */
    @Override
    public void updateByBo(SysSocialBo bo) {
        SysSocial update = BeanMapper.map(bo, SysSocial.class);
        validEntityBeforeSave(update);
        socialMapper.updateById(update);
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(SysSocial entity) {
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 删除社会化关系信息
     *
     * @param id 要删除的社会化关系的唯一标识符
     */
    @Override
    public void deleteWithValidById(Long id) {
        if (socialMapper.deleteById(id) == 0) {
            throw new ServiceException("第三方账号绑定不存在或已解除");
        }
    }

    /**
     * 根据认证ID查询社会化关系和用户信息
     *
     * @param authId 认证ID
     * @return 返回包含SysSocial和用户信息的SysSocialVo对象列表
     */
    @Override
    public List<SysSocialVo> selectByAuthId(String authId) {
        return BeanMapper.mapList(socialMapper.selectList(QueryBuilder.lambda(SysSocial.class).eq(SysSocial::getAuthId, authId).build()), SysSocialVo.class);
    }

}
