package org.tuoyu.common.mybatis.core.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.utils.BeanMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 把 MyBatis-Plus 的分页结果转换为接口返回的 {@link PageResult}。
 * <pre>{@code
 * Page<SysPost> page = postMapper.selectPage(pageParam.toPage(SysPost.class), wrapper);
 * return PageResults.of(page, SysPostVo.class);
 * }</pre>
 *
 * @author tuoyu
 */
public final class PageResults {

    private PageResults() {
    }

    /**
     * 元素类型不变（查询已经返回 Vo，例如手写 SQL 的 Mapper 方法）
     *
     * @param page 分页结果，为 null 时返回空结果
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        if (page == null) {
            return PageResult.empty();
        }
        return PageResult.of(page.getRecords(), page.getTotal());
    }

    /**
     * 逐个转换元素
     *
     * @param page   分页结果，为 null 时返回空结果
     * @param mapper 元素转换
     */
    public static <E, V> PageResult<V> of(IPage<E> page, Function<? super E, ? extends V> mapper) {
        if (page == null) {
            return PageResult.empty();
        }
        List<E> records = page.getRecords() == null ? List.of() : page.getRecords();
        List<V> items = new ArrayList<>(records.size());
        for (E record : records) {
            items.add(mapper.apply(record));
        }
        return PageResult.of(items, page.getTotal());
    }

    /**
     * 用 {@link BeanMapper} 把元素转换为 {@code voClass}
     *
     * @param page    分页结果，为 null 时返回空结果
     * @param voClass 目标类型
     */
    public static <E, V> PageResult<V> of(IPage<E> page, Class<V> voClass) {
        if (page == null) {
            return PageResult.empty();
        }
        return PageResult.of(BeanMapper.mapList(page.getRecords(), voClass), page.getTotal());
    }

}
