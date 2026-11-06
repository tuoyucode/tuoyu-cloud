package org.tuoyu.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.enums.BusinessStatusEnum;
import org.tuoyu.common.core.enums.PushSourceEnum;
import org.tuoyu.common.core.enums.PushTypeEnum;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.ThreadUtils;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.resource.api.RemoteMailService;
import org.tuoyu.resource.api.RemoteMessageService;
import org.tuoyu.resource.api.RemoteSmsService;
import org.tuoyu.resource.api.domain.RemotePushPayLoad;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.common.enums.MessageTypeEnum;
import org.tuoyu.workflow.service.IFlwCommonService;
import org.tuoyu.workflow.service.IFlwTaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.tuoyu.workflow.common.constant.FlowConstant.PATH_MY_DOCUMENT;
import static org.tuoyu.workflow.common.constant.FlowConstant.PATH_TASK_WAITING;


/**
 * 工作流工具
 *
 * @author LionLi
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwCommonServiceImpl implements IFlwCommonService {

    private static final String DEFAULT_SUBJECT = "单据审批提醒";

    private final RemoteMessageService remoteMessageService;
    private final RemoteMailService remoteMailService;
    private final RemoteSmsService remoteSmsService;

    /**
     * 根据流程实例发送消息给当前处理人
     *
     * @param flowName    流程定义名称
     * @param instId      流程实例ID
     * @param messageType 消息类型列表
     * @param message     消息内容，为空则使用默认消息
     */
    @Override
    public void sendMessage(String flowName, Long instId, List<String> messageType, String message) {
        if (CollUtil.isEmpty(messageType)) {
            return;
        }
        IFlwTaskService flwTaskService = SpringUtils.getBean(IFlwTaskService.class);
        List<FlowTask> list = flwTaskService.selectByInstId(instId);
        if (CollUtil.isEmpty(list)) {
            return;
        }
        if (StringUtils.isBlank(message)) {
            message = "有新的【" + flowName + "】单据已经提交至您，请您及时处理。";
        }
        // 当前用户是上一节点的审批人，通常无权读取下一节点的任务，这里不能用带读取校验的 currentTaskAllUser
        List<RemoteUserVo> userList = flwTaskService.selectHandlersByTaskIds(StreamUtils.toList(list, FlowTask::getId));
        if (CollUtil.isEmpty(userList)) {
            return;
        }
        sendMessage(messageType, message, DEFAULT_SUBJECT, userList, PATH_TASK_WAITING);
    }

    /**
     * 发送消息给指定用户列表
     *
     * @param messageType 消息类型列表
     * @param message     消息内容
     * @param subject     邮件标题
     * @param userList    接收用户列表
     */
    @Override
    public void sendMessage(List<String> messageType, String message, String subject, List<RemoteUserVo> userList) {
        sendMessage(messageType, message, subject, userList, null);
    }

    @Override
    public void sendResultMessage(String flowName, BusinessStatusEnum status, List<String> messageType, List<RemoteUserVo> userList) {
        if (status == null || CollUtil.isEmpty(messageType) || CollUtil.isEmpty(userList)) {
            return;
        }
        String message = "您发起的【" + flowName + "】单据审批已" + status.getDesc() + "。";
        sendMessage(messageType, message, DEFAULT_SUBJECT, userList, PATH_MY_DOCUMENT);
    }

    @Override
    public void sendMessage(List<String> messageType, String message, String subject, List<RemoteUserVo> userList, String path) {
        if (CollUtil.isEmpty(messageType) || CollUtil.isEmpty(userList)) {
            return;
        }
        List<Long> userIds = new ArrayList<>(StreamUtils.toSet(userList, RemoteUserVo::getUserId));
        Set<String> emailSet = StreamUtils.toSet(userList, RemoteUserVo::getEmail);
        emailSet.removeIf(StringUtils::isBlank);
        String emails = StringUtils.joinComma(emailSet);

        Runnable[] sendTasks = messageType.stream()
            .map(code -> (Runnable) () -> sendMessageByType(code, message, subject, path, userIds, emails, userList.size()))
            .toArray(Runnable[]::new);
        ThreadUtils.virtualInvokeAll(sendTasks);
    }

    private void sendMessageByType(String code, String message, String subject, String path, List<Long> userIds, String emails, int userCount) {
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByCode(code);
        if (ObjectUtil.isEmpty(messageTypeEnum)) {
            return;
        }
        // 消息只是通知：发送失败不影响流程办理（统一降级，单体与微服务行为一致，ADR 0007）
        switch (messageTypeEnum) {
            case SYSTEM_MESSAGE -> {
                RemotePushPayLoad payload = RemotePushPayLoad.of(
                    PushTypeEnum.MESSAGE,
                    PushSourceEnum.WORKFLOW,
                    message, null, path
                );
                RemoteFallback.run("resource.message.workflow", () -> remoteMessageService.publishMessagePayload(userIds, payload));
            }
            case EMAIL_MESSAGE -> RemoteFallback.run("resource.mail.workflow", () -> remoteMailService.send(emails, subject, message));
            case SMS_MESSAGE -> {
//                    LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
//                    // 根据具体短信服务商参数用法传参
//                    map.put("code", "1234");
//                    remoteSmsService.sendMessage(phones, templateId, map);
                log.info("【短信发送 - TODO】用户数量={} 内容={}", userCount, message);
            }
            default -> log.warn("【消息发送】未处理的消息类型：{}", messageTypeEnum);
        }
    }

    /**
     * 申请人节点编码
     *
     * @param definitionId 流程定义id
     * @return 申请人节点编码
     */
    @Override
    public String applyNodeCode(Long definitionId) {
        List<Node> firstBetweenNode = FlowEngine.nodeService().getFirstBetweenNode(definitionId, new HashMap<>());
        if (CollUtil.isEmpty(firstBetweenNode)) {
            throw new ServiceException("流程定义缺少申请人节点，请检查流程定义配置");
        }
        return firstBetweenNode.getFirst().getNodeCode();
    }

}
