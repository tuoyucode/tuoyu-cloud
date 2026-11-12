package org.tuoyu.mes.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.springframework.dao.DuplicateKeyException;
import org.tuoyu.common.core.exception.ServiceException;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 用户填写的编码（工厂、物料、设备等主数据与编码规则）在租户内唯一、不区分大小写。
 * <p>
 * 唯一由数据库保证（迁移 V6 的唯一索引，只约束未删除的行）；服务层先按同样的规则查询，给出具体的提示。
 * 查询条件写成 {@code lower(编码列) = lower(值)}，与唯一索引的表达式相同：PostgreSQL 用上
 * {@code (tenant_id, lower(编码))} 的部分索引，MySQL 用上函数索引的 {@code (tenant_id, lower(编码))} 部分，
 * 两种数据库的比较结果都与排序规则无关。
 *
 * @author tuoyu
 */
public final class UniqueCodes {

    private UniqueCodes() {
    }

    /**
     * 编码相同（不区分大小写）的条件，用于 {@code QueryBuilder.lambda(...).func(...)}
     *
     * @param column 编码列
     * @param code   编码
     */
    public static <T> Consumer<LambdaQueryWrapper<T>> sameCode(SFunction<T, String> column, String code) {
        String name = column(column);
        return wrapper -> wrapper.apply("lower(" + name + ") = lower({0})", code);
    }

    /**
     * 编码在给定的一组之中（不区分大小写）的条件，用于导入时按编码批量查询；codes 不能为空
     *
     * @param column 编码列
     * @param codes  编码（不含 null）
     */
    public static <T> Consumer<LambdaQueryWrapper<T>> codeIn(SFunction<T, String> column, Collection<String> codes) {
        List<String> values = List.copyOf(codes);
        String placeholders = IntStream.range(0, values.size()).mapToObj(i -> "lower({" + i + "})")
            .collect(Collectors.joining(", "));
        String name = column(column);
        return wrapper -> wrapper.apply("lower(" + name + ") in (" + placeholders + ")", values.toArray());
    }

    /**
     * 执行写入（新增或修改），并发写入相同编码撞上唯一索引时，转为与服务层校验相同的提示
     *
     * @param write            写入数据库的操作
     * @param duplicateMessage 编码重复时的提示，例如“物料编码“M001”已存在”
     * @return 写入的结果（影响的行数等）
     */
    public static <R> R write(Supplier<R> write, String duplicateMessage) {
        try {
            return write.get();
        } catch (DuplicateKeyException e) {
            throw new ServiceException(duplicateMessage);
        }
    }

    /**
     * 编码列的列名（按实体的表信息解析，与条件构造器解析 Lambda 列的方式相同）
     */
    static <T> String column(SFunction<T, String> column) {
        LambdaMeta meta = LambdaUtils.extract(column);
        String property = PropertyNamer.methodToProperty(meta.getImplMethodName());
        ColumnCache cache = LambdaUtils.getColumnMap(meta.getInstantiatedClass()).get(LambdaUtils.formatKey(property));
        if (cache == null) {
            throw new IllegalStateException(meta.getInstantiatedClass().getSimpleName() + " 中找不到字段 " + property);
        }
        return cache.getColumn();
    }

}
