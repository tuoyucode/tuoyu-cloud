package org.tuoyu.system.domain.convert;

import io.github.linpeilie.BaseMapper;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.domain.bo.SysSocialBo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 社交数据转换器
 *
 * @author Michelle.Chung
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSocialBoConvert extends BaseMapper<RemoteSocialBo, SysSocialBo> {
}
