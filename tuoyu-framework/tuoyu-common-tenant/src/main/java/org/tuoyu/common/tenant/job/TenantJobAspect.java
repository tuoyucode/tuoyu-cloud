package org.tuoyu.common.tenant.job;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.tuoyu.common.tenant.core.TenantDirectory;
import org.tuoyu.common.tenant.core.TenantHelper;

import java.util.List;

/**
 * 执行 {@link TenantJob}：依次在每个租户中执行
 *
 * @author tuoyu
 */
@Slf4j
@Aspect
public class TenantJobAspect {

    private final ObjectProvider<TenantDirectory> directory;

    public TenantJobAspect(ObjectProvider<TenantDirectory> directory) {
        this.directory = directory;
    }

    @Around("@annotation(org.tuoyu.common.tenant.job.TenantJob)")
    public Object runPerTenant(ProceedingJoinPoint point) throws Throwable {
        if (!TenantHelper.isEnabled()) {
            return point.proceed();
        }
        TenantDirectory tenants = directory.getIfAvailable();
        if (tenants == null) {
            throw new IllegalStateException("@TenantJob 需要租户清单（TenantDirectory），请引入 tuoyu-common-service-impl");
        }
        List<String> tenantIds = TenantHelper.ignore(tenants::activeTenantIds);
        int failed = 0;
        for (String tenantId : tenantIds) {
            try {
                TenantHelper.dynamic(tenantId, () -> {
                    try {
                        return point.proceed();
                    } catch (RuntimeException | Error e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new IllegalStateException(e);
                    }
                });
            } catch (RuntimeException e) {
                failed++;
                log.error("定时任务 {} 在租户 {} 中执行失败", point.getSignature().toShortString(), tenantId, e);
            }
        }
        log.info("定时任务 {} 按租户执行完成：共 {} 个租户，失败 {} 个", point.getSignature().toShortString(), tenantIds.size(), failed);
        return null;
    }

}
