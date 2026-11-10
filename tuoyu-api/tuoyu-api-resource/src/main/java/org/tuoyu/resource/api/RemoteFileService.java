package org.tuoyu.resource.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.resource.api.domain.RemoteFile;

import java.util.List;

/**
 * 文件服务
 *
 * @author Lion Li
 */
@RemoteApi
public interface RemoteFileService {

    /**
     * 通过ossId查询对应的url
     *
     * @param ossIds ossId串逗号分隔
     * @return url串逗号分隔
     */
    String selectUrlByIds(String ossIds);

    /**
     * 通过ossId查询列表
     *
     * @param ossIds ossId串逗号分隔
     * @return 列表
     */
    List<RemoteFile> selectByIds(String ossIds);

    /**
     * 上传文件到默认的对象存储，并登记文件信息。按文件服务的上传白名单与文件头校验，不通过时抛出业务异常；
     * 调用方的限制（例如头像只接受图片、大小上限）由调用方先校验
     *
     * @param originalName 原始文件名（据此取扩展名）
     * @param content      文件内容
     * @return 文件信息（含 ossId 与访问地址）
     */
    RemoteFile upload(String originalName, byte[] content);
}
