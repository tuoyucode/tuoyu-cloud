package org.tuoyu.resource.domain.vo;

import java.util.List;

/**
 * 上传限制：前端据此设置可选的文件类型，并在上传前提示超限
 *
 * @param allowedExtensions 允许上传的扩展名（小写，不带点），与服务端的白名单相同
 * @param maxFileSize       单个文件的上限（字节）；负数表示不限制
 * @author tuoyu
 */
public record SysOssUploadConfigVo(List<String> allowedExtensions, long maxFileSize) {
}
