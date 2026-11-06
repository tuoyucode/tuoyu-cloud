package org.tuoyu.common.mybatis.core.page;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.sql.SqlUtil;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页查询实体类
 *
 * @author Lion Li
 */
@Data
@NoArgsConstructor
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分页大小
     */
    private Integer pageSize;

    /**
     * 当前页数
     */
    private Integer pageNum;

    /**
     * 排序列
     */
    private String orderByColumn;

    /**
     * 排序的方向desc或者asc
     */
    private String isAsc;

    /**
     * 当前记录起始索引 默认值
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 每页显示记录数 默认值（未传 pageSize 或 pageSize 不合法时使用）
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 每页显示记录数 上限的默认值，可通过 tuoyu.page.max-size 调整
     */
    public static final int DEFAULT_MAX_PAGE_SIZE = 500;

    /**
     * 每页显示记录数 上限。超过时按上限处理，防止一次请求查出整张表
     */
    private static volatile int maxPageSize = DEFAULT_MAX_PAGE_SIZE;

    /**
     * 设置每页显示记录数的上限，启动时由分页配置调用
     */
    public static void setMaxPageSize(int maxPageSize) {
        if (maxPageSize <= 0) {
            throw new IllegalArgumentException("tuoyu.page.max-size 必须大于 0：" + maxPageSize);
        }
        PageQuery.maxPageSize = maxPageSize;
    }

    public static int getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * 实际使用的每页记录数：未传或不合法时取默认值，超过上限时取上限
     */
    public int resolvePageSize() {
        Integer size = getPageSize();
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, maxPageSize);
    }

    /**
     * 构建分页对象
     */
    public <T> Page<T> build() {
        Integer pageNum = ObjectUtil.defaultIfNull(getPageNum(), DEFAULT_PAGE_NUM);
        int pageSize = resolvePageSize();
        if (pageNum <= 0) {
            pageNum = DEFAULT_PAGE_NUM;
        }
        Page<T> page = new Page<>(pageNum, pageSize);
        List<OrderItem> orderItems = buildOrderItem();
        if (CollUtil.isNotEmpty(orderItems)) {
            page.addOrder(orderItems);
        }
        return page;
    }

    /**
     * 只用来统计行数的分页对象（例如导出前先统计行数）。
     * <p>
     * 分页插件会先执行优化后的 COUNT 语句（去掉 ORDER BY，各数据库都能执行），租户与数据权限条件照常生效；
     * 有数据时再最多取 1 行。直接 selectCount 会把查询条件中的 ORDER BY 带进 COUNT，PostgreSQL 会报错。
     * 用法：{@code mapper.selectVoPage(PageQuery.countOnly(), wrapper).getTotal()}
     *
     * @return 第 1 页、每页 1 行、统计总数的分页对象
     */
    public static <T> Page<T> countOnly() {
        return new Page<>(DEFAULT_PAGE_NUM, 1, true);
    }

    /**
     * 构建排序
     * <p>
     * 支持的用法如下:
     * {isAsc:"asc",orderByColumn:"id"} order by id asc
     * {isAsc:"asc",orderByColumn:"id,createTime"} order by id asc,create_time asc
     * {isAsc:"desc",orderByColumn:"id,createTime"} order by id desc,create_time desc
     * {isAsc:"asc,desc",orderByColumn:"id,createTime"} order by id asc,create_time desc
     */
    private List<OrderItem> buildOrderItem() {
        if (StringUtils.isBlank(orderByColumn) || StringUtils.isBlank(isAsc)) {
            return List.of();
        }
        String orderBy = SqlUtil.escapeOrderBySql(orderByColumn);
        orderBy = StringUtils.toUnderScoreCase(orderBy);

        // 兼容前端排序类型
        String orderDirection = StringUtils.replaceEach(isAsc, new String[]{"ascending", "descending"}, new String[]{"asc", "desc"});

        String[] orderByArr = orderBy.split(StringUtils.SEPARATOR);
        String[] isAscArr = orderDirection.split(StringUtils.SEPARATOR);
        if (isAscArr.length != 1 && isAscArr.length != orderByArr.length) {
            throw new ServiceException("排序参数有误");
        }

        List<OrderItem> list = new ArrayList<>();
        // 每个字段各自排序
        for (int i = 0; i < orderByArr.length; i++) {
            String orderByStr = orderByArr[i];
            String isAscStr = isAscArr.length == 1 ? isAscArr[0] : isAscArr[i];
            if ("asc".equals(isAscStr)) {
                list.add(OrderItem.asc(orderByStr));
            } else if ("desc".equals(isAscStr)) {
                list.add(OrderItem.desc(orderByStr));
            } else {
                throw new ServiceException("排序参数有误");
            }
        }
        return list;
    }

    /**
     * 获取当前页起始行号。
     *
     * @return 起始行号
     */
    @JsonIgnore
    public Integer getFirstNum() {
        Integer currentPageNum = ObjectUtil.defaultIfNull(getPageNum(), DEFAULT_PAGE_NUM);
        int currentPageSize = resolvePageSize();
        if (currentPageNum <= 0) {
            currentPageNum = DEFAULT_PAGE_NUM;
        }
        return (currentPageNum - 1) * currentPageSize;
    }

    /**
     * 构造分页查询对象。
     *
     * @param pageSize 分页大小
     * @param pageNum  当前页码
     */
    public PageQuery(Integer pageSize, Integer pageNum) {
        this.pageSize = pageSize;
        this.pageNum = pageNum;
    }

}
