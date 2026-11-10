package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.excel.ImportFields;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.enums.MaterialType;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 主数据服务共用的小工具：批量填充关联名称、默认值与上级校验
 *
 * @author tuoyu
 */
final class MasterdataSupport {

    private MasterdataSupport() {
    }

    /**
     * 按关联 ID 批量查询关联对象，再逐行回填（例如车间列表回填工厂名称），只查询一次
     *
     * @param items    要回填的对象
     * @param idOf     从对象中取关联 ID
     * @param mapper   关联对象的 Mapper
     * @param entityId 取关联对象 ID 的方法
     * @param setter   回填（关联对象不存在时不调用）
     */
    static <V, E> void fill(Collection<V> items, Function<V, Long> idOf, BaseMapper<E> mapper,
                            Function<E, Long> entityId, BiConsumer<V, E> setter) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, E> related = MesLookups.byIds(mapper, MesLookups.ids(items, idOf), entityId);
        for (V item : items) {
            Long id = idOf.apply(item);
            E entity = id == null ? null : related.get(id);
            if (entity != null) {
                setter.accept(item, entity);
            }
        }
    }

    /**
     * 未填写状态时为正常
     */
    static String statusOrNormal(String status) {
        return status == null || status.isEmpty() ? SystemConstants.NORMAL : status;
    }

    /**
     * 未填写排序号时为 0
     */
    static int sortOrZero(Integer sortNo) {
        return sortNo == null ? 0 : sortNo;
    }

    /**
     * 查询结果为空时抛出带原因的业务异常
     */
    static <T> T require(T entity, String message) {
        if (entity == null) {
            throw new ServiceException(message);
        }
        return entity;
    }

    /**
     * 上级由下级带出：前端填了上级且与实际不一致时拒绝，否则取实际的上级
     *
     * @param given   前端提交的上级 ID（可为空）
     * @param actual  按下级查出的上级 ID
     * @param message 不一致时的提示
     * @return 实际的上级 ID
     */
    static Long derive(Long given, Long actual, String message) {
        if (given != null && !given.equals(actual)) {
            throw new ServiceException(message);
        }
        return actual;
    }

    /**
     * 导入时按编码取产品：必须存在，且是成品或半成品
     *
     * @param what 建立的对象，用于提示，例如“物料清单”
     */
    static MesMaterial importProduct(ImportFields fields, String value, Map<String, MesMaterial> materials, String what) {
        String code = fields.text("产品编码", value, 64, true);
        if (code == null) {
            return null;
        }
        MesMaterial product = materials.get(MesLookups.codeKey(code));
        if (product == null) {
            fields.error("产品编码 " + code + " 不存在");
            return null;
        }
        if (!CodeEnum.in(product.getMaterialType(), MaterialType.PRODUCT, MaterialType.SEMI)) {
            fields.error("物料 " + code + "（" + product.getMaterialName() + "）不是成品或半成品，不能建立" + what);
            return null;
        }
        return product;
    }

}
