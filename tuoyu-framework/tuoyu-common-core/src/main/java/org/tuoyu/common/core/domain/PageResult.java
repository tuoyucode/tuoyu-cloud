package org.tuoyu.common.core.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分页结果：{@code { "items": [...], "total": 123 }}。
 * <p>
 * 从 MyBatis-Plus 的分页结果转换用 tuoyu-common-mybatis 的 {@code PageResults}。
 *
 * @param <T> 元素类型
 * @author tuoyu
 */
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<T> items = new ArrayList<>();

    private long total;

    /**
     * 供反序列化使用；业务代码用静态工厂方法创建
     */
    public PageResult() {
    }

    private PageResult(List<T> items, long total) {
        this.items = items == null ? new ArrayList<>() : items;
        this.total = total;
    }

    /**
     * @param items 当前页的数据，为 null 时按空列表处理
     * @param total 符合条件的总条数
     */
    public static <T> PageResult<T> of(List<T> items, long total) {
        return new PageResult<>(items, total);
    }

    /**
     * 不分页的列表（例如内存中的数据）：总条数为列表长度
     *
     * @param items 全部数据，为 null 时按空列表处理
     */
    public static <T> PageResult<T> of(List<T> items) {
        return new PageResult<>(items, items == null ? 0 : items.size());
    }

    /**
     * @return 没有数据的分页结果
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(new ArrayList<>(), 0);
    }

    /**
     * 转换元素类型，总条数不变
     *
     * @param mapper 元素转换
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = new ArrayList<>(items.size());
        for (T item : items) {
            mapped.add(mapper.apply(item));
        }
        return new PageResult<>(mapped, total);
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

}
