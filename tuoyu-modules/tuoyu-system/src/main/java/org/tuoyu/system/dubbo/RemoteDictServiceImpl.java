package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.system.api.RemoteDictService;
import org.tuoyu.system.api.domain.vo.RemoteDictDataVo;
import org.tuoyu.system.api.domain.vo.RemoteDictTypeVo;
import org.tuoyu.system.service.ISysDictTypeService;

import java.util.List;

/**
 * 字典服务的跨模块实现。经字典类型服务读取（带缓存），转换为跨模块对象
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemoteDictServiceImpl implements RemoteDictService {

    private final ISysDictTypeService dictTypeService;

    @Override
    public RemoteDictTypeVo selectDictTypeByType(String dictType) {
        return BeanMapper.map(dictTypeService.selectDictTypeByType(dictType), RemoteDictTypeVo.class);
    }

    @Override
    public List<RemoteDictDataVo> selectDictDataByType(String dictType) {
        return BeanMapper.mapList(dictTypeService.selectDictDataByType(dictType), RemoteDictDataVo.class);
    }

}
