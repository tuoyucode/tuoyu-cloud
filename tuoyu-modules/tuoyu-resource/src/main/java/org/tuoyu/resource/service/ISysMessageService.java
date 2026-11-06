package org.tuoyu.resource.service;

import org.tuoyu.common.push.dto.PushPayloadDTO;
import org.tuoyu.resource.domain.vo.SysMessageBoxVo;

import java.util.List;

/**
 * 消息记录服务接口
 *
 * @author Lion Li
 */
public interface ISysMessageService {

    /**
     * 查询当前用户消息盒子数据
     * 按系统消息、通知公告、工作流消息分类返回
     *
     * @param userId 用户ID
     * @return 消息盒子数据
     */
    SysMessageBoxVo queryMessageBox(Long userId);

    /**
     * 存储全局广播消息到数据库
     *
     * @param payload 消息推送体
     * @return 回填消息ID后的消息体
     */
    PushPayloadDTO storeAll(PushPayloadDTO payload);

    /**
     * 存储指定用户消息到数据库
     *
     * @param userIds 用户ID集合
     * @param payload 消息推送体
     * @return 回填消息ID后的消息体
     */
    PushPayloadDTO storeUsers(List<Long> userIds, PushPayloadDTO payload);
}
