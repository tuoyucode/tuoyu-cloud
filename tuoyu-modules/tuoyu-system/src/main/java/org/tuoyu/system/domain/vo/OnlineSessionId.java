package org.tuoyu.system.domain.vo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 在线会话编号：令牌的 SHA-256（十六进制小写）。
 * <p>
 * 管理界面只看到会话编号，不看到令牌；强退时按编号在现有令牌中查找。
 *
 * @author tuoyu
 */
public final class OnlineSessionId {

    private OnlineSessionId() {
    }

    /**
     * @param token 令牌
     * @return 会话编号；令牌为空时返回 null
     */
    public static String of(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return HexFormat.of().formatHex(sha256(token));
    }

    /**
     * 在令牌中查找会话编号对应的那一个
     *
     * @param tokens    候选令牌
     * @param sessionId 会话编号
     * @return 对应的令牌
     */
    public static Optional<String> findToken(Collection<String> tokens, String sessionId) {
        if (tokens == null || sessionId == null || sessionId.length() != 64) {
            return Optional.empty();
        }
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(sessionId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        for (String token : tokens) {
            if (token != null && !token.isEmpty() && MessageDigest.isEqual(expected, sha256(token))) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    private static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

}
