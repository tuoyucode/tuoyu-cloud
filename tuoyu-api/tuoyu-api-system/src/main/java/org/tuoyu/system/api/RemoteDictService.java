package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.vo.RemoteDictDataVo;
import org.tuoyu.system.api.domain.vo.RemoteDictTypeVo;

import java.util.List;

/**
 * 字典服务
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteDictService {

    /**
     * 按类型编码查询字典类型
     *
     * @param dictType 字典类型编码
     * @return 字典类型；不存在时为 null
     */
    RemoteDictTypeVo selectDictTypeByType(String dictType);

    /**
     * 按类型编码查询该类型的全部字典数据（按排序号升序）
     *
     * @param dictType 字典类型编码
     * @return 字典数据；没有时为空列表
     */
    List<RemoteDictDataVo> selectDictDataByType(String dictType);

}
