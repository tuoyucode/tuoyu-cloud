package org.tuoyu.ai.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aizuda.snail.ai.common.execption.SnailAiException;
import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiUserRegisterRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiUserVO;
import com.aizuda.snail.ai.openapi.client.core.api.OpenApiUserClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.ApiResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Snail AI OpenAPI 控制器
 *
 * @author opensnail
 * @date 2026-04-25
 */
@Slf4j
@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "snail-ai.open-api", name = "enabled", havingValue = "true")
public class SnailAiController {

    /**
     * Snail AI 成功状态码。
     */
    private static final int SNAIL_AI_SUCCESS = 1;
    private final OpenApiUserClient userClient;

    /**
     * 注册当前登录用户并返回 OpenAPI 用户信息。
     * <p>
     * 仅登录：注册的是当前用户自己（以当前用户 ID 作为外部标识），不涉及其他用户的数据，不需要菜单权限
     */
    @SaCheckLogin
    @PostMapping("/user/register")
    public ApiResult<OpenApiUserVO> registerCurrentUser() {
        return ApiResult.ok(ensureOpenApiUser());
    }

    /**
     * 确保当前登录用户已注册为 OpenAPI 用户。
     */
    private OpenApiUserVO ensureOpenApiUser() {
        CurrentUser user = UserContext.current().orElse(null);
        if (user == null || user.getUserId() == null) {
            throw new SnailAiException("当前登录用户为空");
        }

        OpenApiUserRegisterRequest registerRequest = new OpenApiUserRegisterRequest();
        registerRequest.setExternalId(String.valueOf(user.getUserId()));
        registerRequest.setNickname(user.getNickname());
        Result<OpenApiUserVO> registerResult = userClient.register(registerRequest);
        if (registerResult == null) {
            throw new SnailAiException("注册 OpenAPI 用户失败，返回为空");
        }
        if (registerResult.getStatus() != SNAIL_AI_SUCCESS) {
            throw new SnailAiException(registerResult.getMessage());
        }
        if (registerResult.getData() == null) {
            throw new SnailAiException("注册 OpenAPI 用户失败，返回为空");
        }
        return registerResult.getData();
    }
}
