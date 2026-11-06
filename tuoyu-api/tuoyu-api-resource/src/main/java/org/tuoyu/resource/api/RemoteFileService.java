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
}
