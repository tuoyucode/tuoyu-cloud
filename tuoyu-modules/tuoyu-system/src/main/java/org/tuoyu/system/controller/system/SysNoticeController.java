package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.enums.PushSourceEnum;
import org.tuoyu.common.core.enums.PushTypeEnum;
import org.tuoyu.common.core.service.DictService;
import org.tuoyu.common.log.annotation.Log;
import org.tuoyu.common.log.enums.BusinessType;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.redis.annotation.RepeatSubmit;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.web.core.BaseController;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.resource.api.RemoteMessageService;
import org.tuoyu.resource.api.domain.RemotePushPayLoad;
import org.tuoyu.system.domain.bo.SysNoticeBo;
import org.tuoyu.system.domain.vo.SysNoticeVo;
import org.tuoyu.system.service.ISysNoticeService;
import org.tuoyu.system.service.ISysUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告 信息操作处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/notice")
public class SysNoticeController extends BaseController {

    /**
     * 推送公告的降级调用名称（日志与计数）
     */
    private static final String PUSH_FALLBACK_NAME = "resource.message.notice";

    private final ISysNoticeService noticeService;
    private final DictService dictService;

    private final RemoteMessageService remoteMessageService;
    private final ISysUserService userService;

    /**
     * 获取通知公告列表
     */
    @SaCheckPermission("system:notice:list")
    @GetMapping("/list")
    public R<PageResult<SysNoticeVo>> list(SysNoticeBo notice, PageQuery pageQuery) {
        return R.ok(noticeService.selectPageNoticeList(notice, pageQuery));
    }

    /**
     * 根据通知公告编号获取详细信息
     *
     * @param noticeId 公告ID
     */
    @SaCheckPermission("system:notice:query")
    @GetMapping(value = "/{noticeId}")
    public R<SysNoticeVo> getInfo(@PathVariable Long noticeId) {
        return R.ok(noticeService.selectNoticeById(noticeId));
    }

    /**
     * 新增通知公告
     */
    @SaCheckPermission("system:notice:add")
    @Log(title = "通知公告", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysNoticeBo notice) {
        int rows = noticeService.insertNotice(notice);
        if (rows <= 0) {
            return R.fail();
        }
        String type = dictService.getDictLabel("sys_notice_type", notice.getNoticeType());
        Map<String, Object> data = new HashMap<>(6);
        data.put("noticeType", notice.getNoticeType());
        data.put("noticeTypeLabel", type);
        data.put("noticeTitle", notice.getNoticeTitle());
        data.put("noticeId", notice.getNoticeId());
        data.put("noticeContent", notice.getNoticeContent());
        data.put("status", notice.getStatus());
        RemotePushPayLoad payload = RemotePushPayLoad.of(
            PushTypeEnum.NOTICE,
            PushSourceEnum.NOTICE,
            "[" + type + "] " + notice.getNoticeTitle(),
            data,
            "/system/notice?noticeId=" + notice.getNoticeId()
        );
        // 推送只是提示：公告已保存，推送失败不影响结果（统一降级，单体与微服务行为一致，ADR 0007）
        if (TenantHelper.isEnabled()) {
            // 开启多租户时只推送给本租户的用户（ADR 0006 第 6 节）；用户查询按租户隔离
            List<Long> userIds = userService.selectAllUserIds();
            RemoteFallback.run(PUSH_FALLBACK_NAME, () -> remoteMessageService.publishMessagePayload(userIds, payload));
        } else {
            RemoteFallback.run(PUSH_FALLBACK_NAME, () -> remoteMessageService.publishAllPayload(payload));
        }
        return R.ok();
    }

    /**
     * 修改通知公告
     */
    @SaCheckPermission("system:notice:edit")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysNoticeBo notice) {
        return toAjax(noticeService.updateNotice(notice));
    }

    /**
     * 删除通知公告
     *
     * @param noticeIds 公告ID串
     */
    @SaCheckPermission("system:notice:remove")
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public R<Void> remove(@PathVariable Long[] noticeIds) {
        return toAjax(noticeService.deleteNoticeByIds(noticeIds));
    }
}
