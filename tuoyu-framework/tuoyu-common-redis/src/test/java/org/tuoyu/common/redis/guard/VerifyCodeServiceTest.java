package org.tuoyu.common.redis.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.GlobalConstants;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("验证码一次性使用与错误次数上限")
class VerifyCodeServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final InMemoryGuardStore store = new InMemoryGuardStore();

    private final VerifyCodeService service = new VerifyCodeService(store);

    @Test
    @DisplayName("图形验证码：只能校验一次，答错也立即作废；忽略大小写")
    void captchaIsSingleUse() {
        service.saveCaptcha("u1", "AbCd", TTL);
        assertEquals(VerifyResult.PASSED, service.checkCaptcha("u1", " abcd "));
        assertEquals(VerifyResult.EXPIRED, service.checkCaptcha("u1", "abcd"));

        service.saveCaptcha("u2", "7", TTL);
        assertEquals(VerifyResult.MISMATCH, service.checkCaptcha("u2", "8"));
        assertEquals(VerifyResult.EXPIRED, service.checkCaptcha("u2", "7"));

        assertEquals(VerifyResult.EXPIRED, service.checkCaptcha(null, "7"));
        assertEquals(VerifyResult.EXPIRED, service.checkCaptcha("x".repeat(65), "7"));
        service.saveCaptcha("u3", "7", TTL);
        assertEquals(VerifyResult.MISMATCH, service.checkCaptcha("u3", null));
    }

    @Test
    @DisplayName("图形验证码过期后不可用")
    void captchaExpires() {
        service.saveCaptcha("u1", "7", Duration.ofMinutes(2));
        store.advance(Duration.ofMinutes(2));
        assertEquals(VerifyResult.EXPIRED, service.checkCaptcha("u1", "7"));
    }

    @Test
    @DisplayName("短信验证码：校验成功后立即删除，不能再次使用")
    void codeIsDeletedAfterSuccess() {
        service.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", TTL);
        assertEquals(VerifyResult.MISMATCH, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "000000", 3));
        assertEquals(VerifyResult.PASSED, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", 3));
        assertEquals(VerifyResult.EXPIRED, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", 3));
        assertFalse(store.exists(GlobalConstants.VERIFY_ATTEMPT_KEY + "sms:13800000000"));
    }

    @Test
    @DisplayName("连续输错达到上限后作废，正确的验证码也不再可用")
    void codeIsInvalidatedAfterMaxAttempts() {
        service.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", TTL);
        assertEquals(VerifyResult.MISMATCH, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "1", 3));
        assertEquals(VerifyResult.MISMATCH, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "2", 3));
        assertEquals(VerifyResult.EXHAUSTED, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "3", 3));
        assertEquals(VerifyResult.EXPIRED, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", 3));
    }

    @Test
    @DisplayName("重新发送会覆盖旧验证码并清零尝试次数")
    void resendResetsAttempts() {
        service.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "111111", TTL);
        service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "0", 3);
        service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "0", 3);
        service.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "222222", TTL);
        assertEquals(VerifyResult.MISMATCH, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "111111", 3));
        assertEquals(VerifyResult.PASSED, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "222222", 3));
    }

    @Test
    @DisplayName("场景隔离；邮箱忽略大小写；验证码过期")
    void scenesAndExpiry() {
        service.saveCode(VerifyCodeService.SCENE_EMAIL, "A@Example.com", "654321", TTL);
        assertEquals(VerifyResult.EXPIRED, service.checkCode(VerifyCodeService.SCENE_SMS, "a@example.com", "654321", 3));
        assertEquals(VerifyResult.PASSED, service.checkCode(VerifyCodeService.SCENE_EMAIL, "a@example.com", "654321", 3));

        service.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", TTL);
        store.advance(TTL);
        assertEquals(VerifyResult.EXPIRED, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", 3));
        assertThrows(IllegalArgumentException.class, () -> service.checkCode(VerifyCodeService.SCENE_SMS, " ", "1", 3));
        assertThrows(IllegalArgumentException.class, () -> service.checkCode(VerifyCodeService.SCENE_SMS, "1", "1", 0));
    }

    @Test
    @DisplayName("并发猜测不会超出次数上限；并发提交正确验证码只有一个成功")
    void concurrency() throws Exception {
        service.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", TTL);
        List<VerifyResult> guesses = runConcurrently(50, i -> service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", String.format("%06d", i), 3));
        // 只有前两次尝试返回“错误可重试”，第三次起验证码作废；其余请求在占用次数时即被拒绝或读不到验证码
        assertEquals(2, guesses.stream().filter(r -> r == VerifyResult.MISMATCH).count());
        assertEquals(0, guesses.stream().filter(r -> r == VerifyResult.PASSED).count());
        assertEquals(VerifyResult.EXPIRED, service.checkCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", 3));

        service.saveCode(VerifyCodeService.SCENE_SMS, "13900000000", "123456", TTL);
        List<VerifyResult> results = runConcurrently(20, i -> service.checkCode(VerifyCodeService.SCENE_SMS, "13900000000", "123456", 100));
        assertEquals(1, results.stream().filter(r -> r == VerifyResult.PASSED).count());
    }

    private interface IndexedTask {
        VerifyResult run(int index);
    }

    private static List<VerifyResult> runConcurrently(int count, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<VerifyResult>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                Callable<VerifyResult> callable = () -> {
                    start.await();
                    return task.run(index);
                };
                futures.add(executor.submit(callable));
            }
            start.countDown();
            List<VerifyResult> results = new ArrayList<>();
            for (Future<VerifyResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

}
