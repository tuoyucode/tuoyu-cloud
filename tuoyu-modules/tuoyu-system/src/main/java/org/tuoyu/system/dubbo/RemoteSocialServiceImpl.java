package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;
import org.tuoyu.system.domain.bo.SysSocialBo;
import org.tuoyu.system.domain.vo.SysSocialVo;
import org.tuoyu.system.service.ISysSocialService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 社会化关系服务
 *
 * @author Michelle.Chung
 */
@RequiredArgsConstructor
@Service
public class RemoteSocialServiceImpl implements RemoteSocialService {

    private final ISysSocialService sysSocialService;

    /**
     * 根据 authId 查询用户授权信息
     *
     * @param authId 认证id
     * @return 授权信息
     */
    @Override
    public List<RemoteSocialVo> selectByAuthId(String authId) {
        List<SysSocialVo> list = sysSocialService.selectByAuthId(authId);
        return BeanMapper.mapList(list, RemoteSocialVo.class);
    }

    /**
     * 查询列表
     *
     * @param bo 社会化关系业务对象
     */
    @Override
    public List<RemoteSocialVo> queryList(RemoteSocialBo bo) {
        SysSocialBo params = BeanMapper.map(bo, SysSocialBo.class);
        List<SysSocialVo> list = sysSocialService.queryList(params);
        return BeanMapper.mapList(list, RemoteSocialVo.class);
    }

    /**
     * 保存社会化关系
     *
     * @param bo 社会化关系业务对象
     */
    @Override
    public void insertByBo(RemoteSocialBo bo) {
        sysSocialService.insertByBo(BeanMapper.map(bo, SysSocialBo.class));
    }

    /**
     * 更新社会化关系
     *
     * @param bo 社会化关系业务对象
     */
    @Override
    public void updateByBo(RemoteSocialBo bo) {
        sysSocialService.updateByBo(BeanMapper.map(bo, SysSocialBo.class));
    }

    /**
     * 删除社会化关系
     *
     * @param socialId 社会化关系ID
     */
    @Override
    public void deleteWithValidById(Long socialId) {
        sysSocialService.deleteWithValidById(socialId);
    }

}
