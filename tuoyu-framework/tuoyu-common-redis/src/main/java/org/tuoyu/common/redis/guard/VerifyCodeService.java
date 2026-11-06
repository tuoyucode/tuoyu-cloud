package org.tuoyu.common.redis.guard;

import org.tuoyu.common.core.constant.GlobalConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * 验证码的保存与校验，认证服务与资源服务共用。
 * <ul>
 *     <li>图形验证码：只能校验一次，无论对错都立即作废；</li>
 *     <li>短信、邮箱验证码：校验通过后立即作废；连续输错达到上限后作废。
 *         先占用一次尝试次数再比对，并发请求也无法超出上限；通过时以“相等则删除”保证只有一个请求成功。</li>
 * </ul>
 *
 * @author tuoyu
 */
public class VerifyCodeService {

    /**
     * 场景：短信验证码
     */
    public static final String SCENE_SMS = "sms";

    /**
     * 场景：邮箱验证码
     */
    public static final String SCENE_EMAIL = "email";

    /**
     * 图形验证码标识的最大长度
     */
    private static final int MAX_UUID_LENGTH = 64;

    private final GuardStore store;

    public VerifyCodeService(GuardStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * 保存图形验证码的答案。
     *
     * @param uuid   验证码标识
     * @param answer 答案
     * @param ttl    有效期
     */
    public void saveCaptcha(String uuid, String answer, Duration ttl) {
        store.put(captchaKey(uuid), answer, ttl);
    }

    /**
     * 校验图形验证码（忽略大小写）。只能校验一次，无论对错都立即作废。
     *
     * @param uuid  验证码标识
     * @param input 用户输入
     * @return {@link VerifyResult#PASSED}、{@link VerifyResult#EXPIRED} 或 {@link VerifyResult#MISMATCH}
     */
    public VerifyResult checkCaptcha(String uuid, String input) {
        if (uuid == null || uuid.isBlank() || uuid.length() > MAX_UUID_LENGTH) {
            return VerifyResult.EXPIRED;
        }
        String answer = store.take(captchaKey(uuid));
        if (answer == null) {
            return VerifyResult.EXPIRED;
        }
        return input != null && answer.equalsIgnoreCase(input.trim()) ? VerifyResult.PASSED : VerifyResult.MISMATCH;
    }

    /**
     * 保存短信、邮箱等验证码。会覆盖同一接收方之前的验证码，并清零已尝试次数。
     *
     * @param scene  场景，例如 {@link #SCENE_SMS}
     * @param target 接收方（手机号、邮箱）
     * @param code   验证码
     * @param ttl    有效期
     */
    public void saveCode(String scene, String target, String code, Duration ttl) {
        String id = codeId(scene, target);
        store.delete(GlobalConstants.VERIFY_ATTEMPT_KEY + id);
        store.put(GlobalConstants.VERIFY_CODE_KEY + id, code, ttl);
    }

    /**
     * 校验短信、邮箱等验证码。
     *
     * @param scene       场景
     * @param target      接收方
     * @param input       用户输入
     * @param maxAttempts 同一个验证码最多可尝试的次数（至少 1）
     * @return 校验结果
     */
    public VerifyResult checkCode(String scene, String target, String input, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 至少为 1");
        }
        String id = codeId(scene, target);
        String codeKey = GlobalConstants.VERIFY_CODE_KEY + id;
        String attemptKey = GlobalConstants.VERIFY_ATTEMPT_KEY + id;
        String code = store.get(codeKey);
        if (code == null) {
            return VerifyResult.EXPIRED;
        }
        // 先占用一次尝试次数，再比对
        Duration ttl = store.timeToLive(codeKey);
        long attempt = store.increment(attemptKey, ttl.isZero() ? Duration.ofMinutes(10) : ttl);
        if (attempt > maxAttempts) {
            store.delete(codeKey);
            return VerifyResult.EXHAUSTED;
        }
        if (constantTimeEquals(code, input)) {
            if (store.removeIfEquals(codeKey, code)) {
                store.delete(attemptKey);
                return VerifyResult.PASSED;
            }
            // 并发请求已用掉了这个验证码，或刚刚重新发送了新的验证码
            return VerifyResult.EXPIRED;
        }
        if (attempt >= maxAttempts) {
            // 只删除验证码，保留尝试次数（随有效期过期）：并发中已读到旧验证码的请求继续累加，不会被重新计数
            store.delete(codeKey);
            return VerifyResult.EXHAUSTED;
        }
        return VerifyResult.MISMATCH;
    }

    private static String captchaKey(String uuid) {
        return GlobalConstants.CAPTCHA_CODE_KEY + uuid;
    }

    private static String codeId(String scene, String target) {
        if (scene == null || scene.isBlank() || target == null || target.isBlank()) {
            throw new IllegalArgumentException("验证码的场景与接收方不能为空");
        }
        return scene + ":" + target.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean constantTimeEquals(String expected, String input) {
        if (input == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), input.trim().getBytes(StandardCharsets.UTF_8));
    }

}
