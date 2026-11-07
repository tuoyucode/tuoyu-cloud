package org.tuoyu.common.mybatis.core.page;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.exception.ServiceException;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 分页请求参数：{@code page}（从 1 开始）、{@code pageSize}、{@code sort}（可重复，每项 {@code 属性名,asc|desc}）。
 * <p>
 * 用 {@link #toPage(Class)} 转成 MyBatis-Plus 的分页对象：页码与每页条数按默认值与上限修正；排序属性只能是实体的属性
 * （按 MyBatis-Plus 的表信息映射为列名），其他一律拒绝，客户端无法借排序参数拼接 SQL。
 * 敏感属性（口令、密钥类，不下发、加密或脱敏的字段）不能排序，以免借排序结果推出看不到的值，规则见 {@link SortableColumns}；
 * 只要求登录的接口等需要更严格范围时，用 {@link #toPage(Map)} 给出显式白名单。
 *
 * @author tuoyu
 */
public class PageParam implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE = 1;

    /**
     * 未传 pageSize 时的每页条数
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 每页条数上限的默认值（配置项 {@code tuoyu.page.max-size}）
     */
    public static final int DEFAULT_MAX_PAGE_SIZE = 500;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    private static volatile int maxPageSize = DEFAULT_MAX_PAGE_SIZE;

    /**
     * 页码，从 1 开始；为空或小于 1 时取 1
     */
    private Integer page;

    /**
     * 每页条数；为空或小于 1 时取 {@value #DEFAULT_PAGE_SIZE}，超过上限时取上限
     */
    private Integer pageSize;

    /**
     * 排序，每项 {@code 属性名,asc|desc}，方向缺省为 asc
     */
    private List<String> sort;

    public PageParam() {
    }

    /**
     * @param page     页码
     * @param pageSize 每页条数
     */
    public PageParam(Integer page, Integer pageSize) {
        this.page = page;
        this.pageSize = pageSize;
    }

    /**
     * 只统计行数、不取数据的分页对象（导出前检查行数）：第 1 页、每页 1 行。
     * 分页插件执行的 COUNT 语句去掉 ORDER BY，租户与数据权限条件照常生效。
     * <p>
     * 用法：{@code mapper.selectPage(PageParam.countOnly(), wrapper).getTotal()}
     */
    public static <T> Page<T> countOnly() {
        return new Page<>(1, 1);
    }

    /**
     * @return 每页条数上限
     */
    public static int getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * 设置每页条数上限（由 MybatisPlusConfiguration 按 {@code tuoyu.page.max-size} 设置）
     *
     * @param max 上限，必须大于 0
     */
    public static void setMaxPageSize(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("每页条数上限必须大于 0：" + max);
        }
        maxPageSize = max;
    }

    /**
     * 没有传排序时使用的排序
     *
     * @param defaultSort 每项 {@code 属性名,asc|desc}
     * @return 本对象
     */
    public PageParam defaultSort(String... defaultSort) {
        if (!hasSort()) {
            this.sort = new ArrayList<>(Arrays.asList(defaultSort));
        }
        return this;
    }

    /**
     * @return 是否传了排序
     */
    public boolean hasSort() {
        return !parseSort().isEmpty();
    }

    /**
     * @return 修正后的页码
     */
    public long currentPage() {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    /**
     * @return 修正后的每页条数
     */
    public long size() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, maxPageSize);
    }

    /**
     * @return 本页第一条记录的偏移量（手写 LIMIT 时使用）
     */
    public long offset() {
        return (currentPage() - 1) * size();
    }

    /**
     * 不排序的分页对象。传了排序参数时拒绝（400）：查询结果没有可校验的排序属性
     */
    public <T> Page<T> toPage() {
        if (!parseSort().isEmpty()) {
            throw badSort("该列表不支持排序");
        }
        return new Page<>(currentPage(), size());
    }

    /**
     * 按实体的属性排序（敏感属性除外）
     *
     * @param entityClass 查询的实体（已由 MyBatis-Plus 登记表信息）
     */
    public <T> Page<T> toPage(Class<?> entityClass) {
        return toPage(entityClass, null);
    }

    /**
     * 按实体的属性排序，列名前加表别名（联表查询）
     *
     * @param entityClass 查询的实体（已由 MyBatis-Plus 登记表信息）
     * @param tableAlias  SQL 中该表的别名，为空时不加
     */
    public <T> Page<T> toPage(Class<?> entityClass, String tableAlias) {
        List<SortItem> items = parseSort();
        Page<T> result = new Page<>(currentPage(), size());
        if (items.isEmpty()) {
            return result;
        }
        String prefix = aliasPrefix(tableAlias);
        Map<String, String> columns = SortableColumns.of(entityClass);
        for (SortItem item : items) {
            String column = columns.get(item.property());
            if (column == null) {
                throw badSort("不支持按 " + item.property() + " 排序");
            }
            result.addOrder(item.asc() ? OrderItem.asc(prefix + column) : OrderItem.desc(prefix + column));
        }
        return result;
    }

    /**
     * 按显式给出的白名单排序（查询结果不是某个实体时使用）
     *
     * @param sortableColumns 属性名 → SQL 中的列（可以带表别名，由调用方保证是固定的列名）
     */
    public <T> Page<T> toPage(Map<String, String> sortableColumns) {
        List<SortItem> items = parseSort();
        Page<T> result = new Page<>(currentPage(), size());
        for (SortItem item : items) {
            String column = sortableColumns.get(item.property());
            if (column == null) {
                throw badSort("不支持按 " + item.property() + " 排序");
            }
            result.addOrder(item.asc() ? OrderItem.asc(column) : OrderItem.desc(column));
        }
        return result;
    }

    /**
     * 解析排序参数。
     * <p>
     * 只传一个 {@code sort=createTime,desc} 时 Spring 会按逗号拆成两项（{@code createTime} 与 {@code desc}），
     * 所以按“属性名后面可以跟一个方向”的顺序解析，两种形式结果相同
     */
    List<SortItem> parseSort() {
        List<SortItem> items = new ArrayList<>();
        if (sort == null) {
            return items;
        }
        String pending = null;
        for (String entry : sort) {
            if (entry == null) {
                continue;
            }
            for (String raw : entry.split(",")) {
                String token = raw.trim();
                if (token.isEmpty()) {
                    continue;
                }
                String lower = token.toLowerCase(Locale.ROOT);
                if ("asc".equals(lower) || "desc".equals(lower)) {
                    if (pending == null) {
                        throw badSort("排序方向前缺少属性名");
                    }
                    items.add(new SortItem(pending, "asc".equals(lower)));
                    pending = null;
                    continue;
                }
                if (!IDENTIFIER.matcher(token).matches()) {
                    throw badSort("排序属性不合法");
                }
                if (pending != null) {
                    items.add(new SortItem(pending, true));
                }
                pending = token;
            }
        }
        if (pending != null) {
            items.add(new SortItem(pending, true));
        }
        return items;
    }

    private static String aliasPrefix(String tableAlias) {
        if (tableAlias == null || tableAlias.isBlank()) {
            return "";
        }
        if (!IDENTIFIER.matcher(tableAlias).matches()) {
            throw new IllegalArgumentException("表别名不合法：" + tableAlias);
        }
        return tableAlias + ".";
    }

    private static ServiceException badSort(String message) {
        return new ServiceException(message, HttpStatus.BAD_REQUEST);
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public List<String> getSort() {
        return sort;
    }

    public void setSort(List<String> sort) {
        this.sort = sort;
    }

    /**
     * 一个排序项
     *
     * @param property 属性名
     * @param asc      是否升序
     */
    record SortItem(String property, boolean asc) {
    }

}
