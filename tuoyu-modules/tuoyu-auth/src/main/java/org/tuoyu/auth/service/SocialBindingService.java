package org.tuoyu.auth.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.lock.annotation.Lock4j;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;

import java.util.List;

/**
 * 当前用户与第三方平台账号的绑定与解绑。
 * <p>
 * 一个第三方账号只能绑定一个用户；同一用户在同一平台只保留一条绑定（再次绑定时更新原记录）。
 * 解绑只能解除本人的绑定。
 *
 * @author tuoyu
 */
@Service
public class SocialBindingService {

    private final RemoteSocialService remoteSocialService;

    public SocialBindingService(RemoteSocialService remoteSocialService) {
        this.remoteSocialService = remoteSocialService;
    }

    /**
     * 把第三方账号绑定到当前用户。加锁串行处理，避免并发请求把同一个第三方账号绑定给两个用户
     *
     * @param authUser 第三方平台返回的用户
     * @throws ServiceException 该第三方账号已被绑定时
     */
    @Lock4j
    public void bind(AuthUser authUser) {
        String authId = authUser.getSource() + authUser.getUuid();
        List<RemoteSocialVo> bound = remoteSocialService.selectByAuthId(authId);
        if (bound != null && !bound.isEmpty()) {
            throw new ServiceException("此三方账号已经被绑定!");
        }
        Long userId = UserContext.userId();
        RemoteSocialBo bo = BeanUtil.toBean(authUser, RemoteSocialBo.class);
        if (authUser.getToken() != null) {
            BeanUtil.copyProperties(authUser.getToken(), bo);
        }
        bo.setUserId(userId);
        bo.setAuthId(authId);
        bo.setOpenId(authUser.getUuid());
        bo.setUserName(authUser.getUsername());
        bo.setNickName(authUser.getNickname());

        RemoteSocialBo query = new RemoteSocialBo();
        query.setUserId(userId);
        query.setSource(authUser.getSource());
        List<RemoteSocialVo> existing = remoteSocialService.queryList(query);
        if (existing == null || existing.isEmpty()) {
            remoteSocialService.insertByBo(bo);
        } else {
            bo.setId(existing.getFirst().getId());
            remoteSocialService.updateByBo(bo);
        }
    }

    /**
     * 解除当前用户的一条绑定
     *
     * @param socialId 绑定 ID
     * @throws ServiceException 当前用户没有这条绑定时
     */
    public void unbind(Long socialId) {
        RemoteSocialBo query = new RemoteSocialBo();
        query.setUserId(UserContext.userId());
        List<RemoteSocialVo> own = remoteSocialService.queryList(query);
        boolean owned = own != null && socialId != null && own.stream().anyMatch(social -> socialId.equals(social.getId()));
        if (!owned) {
            throw new ServiceException("取消授权失败");
        }
        remoteSocialService.deleteWithValidById(socialId);
    }

}
