package org.tuoyu.resource.controller;


import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.config.RequestSizeLimitConfiguration;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.resource.domain.SysOssExt;
import org.tuoyu.resource.domain.bo.SysOssBo;
import org.tuoyu.resource.config.UploadProperties;
import org.tuoyu.resource.domain.vo.SysOssUploadConfigVo;
import org.tuoyu.resource.domain.vo.SysOssUploadVo;
import org.tuoyu.resource.domain.vo.SysOssVo;
import org.tuoyu.resource.service.ISysOssService;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传 控制层
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/oss")
public class SysOssController {

    private final ISysOssService ossService;
    private final UploadProperties uploadProperties;
    private final Environment environment;

    /**
     * 查询OSS对象存储列表
     */
    @SaCheckPermission("system:oss:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysOssVo>> list(SysOssBo bo, PageParam pageParam) {
        return ApiResult.ok(ossService.queryPageList(bo, pageParam));
    }

    /**
     * 查询OSS对象基于id串
     *
     * @param ossIds OSS对象ID串
     */
    @SaCheckPermission("system:oss:query")
    @GetMapping("/listByIds/{ossIds}")
    public ApiResult<List<SysOssVo>> listByIds(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ossIds) {
        List<SysOssVo> list = ossService.listByIds(Arrays.asList(ossIds));
        return ApiResult.ok(list);
    }

    /**
     * 上传限制：当前生效的扩展名白名单（随配置刷新）与单个文件的上限。前端的可选类型与上传前提示以它为准，
     * 只在服务端维护一份
     */
    @SaCheckPermission("system:oss:upload")
    @GetMapping("/upload-config")
    public ApiResult<SysOssUploadConfigVo> uploadConfig() {
        List<String> extensions = uploadProperties.validator().allowedExtensions().stream().sorted().toList();
        return ApiResult.ok(new SysOssUploadConfigVo(extensions, RequestSizeLimitConfiguration.uploadLimit(environment)));
    }

    /**
     * 上传OSS对象存储
     *
     * @param file 文件
     */
    @SaCheckPermission("system:oss:upload")
    @OperationLog(module = "OSS对象存储", action = AuditAction.CREATE)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<SysOssUploadVo> upload(@RequestPart("file") MultipartFile file, @RequestParam(value = "ossExt", required = false) String ossExtJson) {
        SysOssVo oss = ossService.upload(file, JsonUtils.parseObject(ossExtJson, SysOssExt.class));
        SysOssUploadVo uploadVo = new SysOssUploadVo();
        uploadVo.setUrl(oss.getUrl());
        uploadVo.setFileName(oss.getOriginalName());
        uploadVo.setOssId(oss.getOssId().toString());
        return ApiResult.ok(uploadVo);
    }

    /**
     * 下载OSS对象
     *
     * @param ossId OSS对象ID
     * @throws IOException IO 异常
     */
    @SaCheckPermission("system:oss:download")
    @GetMapping("/download/{ossId}")
    public ResponseEntity<byte[]> download(@PathVariable Long ossId) throws IOException {
        return ossService.download(ossId);
    }

    /**
     * 删除OSS对象存储
     *
     * @param ossIds OSS对象ID串
     */
    @SaCheckPermission("system:oss:remove")
    @OperationLog(module = "OSS对象存储", action = AuditAction.DELETE)
    @DeleteMapping("/{ossIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ossIds) {
        ossService.deleteWithValidByIds(Arrays.asList(ossIds), true);
        return ApiResult.ok();
    }

}
