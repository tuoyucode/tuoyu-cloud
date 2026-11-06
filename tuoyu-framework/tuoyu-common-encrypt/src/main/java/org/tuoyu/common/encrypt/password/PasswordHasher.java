package org.tuoyu.common.encrypt.password;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;

/**
 * 用户密码的哈希与比对（BCrypt）。所有保存密码的地方都必须经过它，计算强度由 {@code tuoyu.auth.password.bcrypt-cost} 统一配置。
 * <p>
 * 比对已有哈希时使用哈希中记录的强度，所以调整强度后旧密码仍可登录，修改密码后按新强度保存。
 *
 * @author tuoyu
 */
public class PasswordHasher {

    /**
     * BCrypt 算法允许的最小强度
     */
    public static final int MIN_COST = 4;

    /**
     * BCrypt 算法允许的最大强度
     */
    public static final int MAX_COST = 31;

    /**
     * 生产环境要求的最低强度，低于它时启动自检 P8 拒绝启动
     */
    public static final int RECOMMENDED_MIN_COST = 10;

    public static final int DEFAULT_COST = 10;

    private final int cost;

    /**
     * 不对应任何账号的随机哈希，与正常密码强度相同，首次使用时生成
     */
    private volatile String dummyHash;

    public PasswordHasher(int cost) {
        if (cost < MIN_COST || cost > MAX_COST) {
            throw new IllegalArgumentException("tuoyu.auth.password.bcrypt-cost 取值范围为 " + MIN_COST + " ~ " + MAX_COST
                + "（生产环境至少 " + RECOMMENDED_MIN_COST + "），当前为 " + cost);
        }
        this.cost = cost;
    }

    /**
     * 计算密码的哈希，用于保存
     */
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(cost));
    }

    /**
     * 比对密码。hash 为空（账号不存在或未设置密码）时与随机哈希比对，耗时与正常比对一致且必然失败，响应时间不暴露账号是否存在；
     * hash 不是合法的 BCrypt 哈希时按不匹配处理
     */
    public boolean matches(String rawPassword, String hash) {
        boolean usable = hash != null && !hash.isBlank();
        try {
            boolean matched = BCrypt.checkpw(rawPassword == null ? "" : rawPassword, usable ? hash : dummyHash());
            return usable && matched;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public int cost() {
        return cost;
    }

    private String dummyHash() {
        String value = dummyHash;
        if (value == null) {
            value = hash(IdUtil.fastSimpleUUID());
            dummyHash = value;
        }
        return value;
    }

}
