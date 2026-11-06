package org.tuoyu.auth.support;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpUtil;

import java.util.UUID;

/**
 * 测试中使用与生产相同的 Sa-Token 模式（jwt-simple，令牌扩展信息写在 JWT 中），存储为内存、请求为模拟上下文。
 * JWT 密钥每次运行随机生成，不写入仓库。
 */
public final class SaTokenTestSupport {

    private SaTokenTestSupport() {
    }

    /**
     * 进入模拟的 Sa-Token 上下文（每个测试前调用）
     */
    public static synchronized void enter() {
        if (!(StpUtil.getStpLogic() instanceof StpLogicJwtForSimple)) {
            SaManager.getConfig().setJwtSecretKey(UUID.randomUUID().toString());
            SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
            StpUtil.setStpLogic(new StpLogicJwtForSimple());
        }
        SaTokenContextMockUtil.setMockContext();
    }

    /**
     * 离开模拟上下文（每个测试后调用）
     */
    public static void exit() {
        SaTokenContextMockUtil.clearContext();
    }

}
