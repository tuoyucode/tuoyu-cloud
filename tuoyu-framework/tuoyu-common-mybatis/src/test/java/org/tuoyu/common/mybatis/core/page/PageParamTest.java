package org.tuoyu.common.mybatis.core.page;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;
import org.tuoyu.common.core.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("分页参数 PageParam：默认值与上限、排序属性白名单")
class PageParamTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "operLog"), OperLog.class);
    }

    /**
     * 按 Spring MVC 绑定查询参数的方式填充 PageParam（单个值与多个值的绑定方式不同）
     */
    private static PageParam bind(MockHttpServletRequest request) {
        PageParam param = new PageParam();
        DataBinder binder = new DataBinder(param);
        binder.setConversionService(new DefaultFormattingConversionService());
        MutablePropertyValues values = new ServletRequestParameterPropertyValues(request);
        binder.bind(values);
        return param;
    }

    private static List<String> orders(Page<?> page) {
        return page.orders().stream().map(o -> o.getColumn() + (o.isAsc() ? " asc" : " desc")).toList();
    }

    @Test
    @DisplayName("未传参数时第 1 页、每页 10 条、不排序")
    void defaults() {
        Page<Object> page = new PageParam().toPage(OperLog.class);
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(PageParam.DEFAULT_PAGE_SIZE).isEqualTo(10);
        assertThat(page.orders()).isEmpty();
        assertThat(new PageParam().hasSort()).isFalse();
    }

    @Test
    @DisplayName("页码小于 1 取 1；每页条数小于 1 取默认值，超过上限取上限；offset 按修正后的值计算")
    void normalize() {
        assertThat(new PageParam(0, 20).currentPage()).isEqualTo(1);
        assertThat(new PageParam(-3, 20).offset()).isZero();
        assertThat(new PageParam(3, 0).size()).isEqualTo(10);
        assertThat(new PageParam(3, -5).size()).isEqualTo(10);
        int max = PageParam.getMaxPageSize();
        assertThat(new PageParam(1, Integer.MAX_VALUE).size()).isEqualTo(max);
        assertThat(new PageParam(3, Integer.MAX_VALUE).offset()).isEqualTo(2L * max);
        assertThat(new PageParam(2, 20).toPage().getSize()).isEqualTo(20);
        assertThat(new PageParam(2, 20).toPage().getCurrent()).isEqualTo(2);
    }

    @Test
    @DisplayName("上限可配置，不接受非正数")
    void maxPageSize() {
        int original = PageParam.getMaxPageSize();
        try {
            PageParam.setMaxPageSize(50);
            assertThat(new PageParam(1, 200).toPage().getSize()).isEqualTo(50);
            assertThatThrownBy(() -> PageParam.setMaxPageSize(0)).isInstanceOf(IllegalArgumentException.class);
        } finally {
            PageParam.setMaxPageSize(original);
        }
    }

    @Test
    @DisplayName("只有一个 sort 参数（sort=operTime,desc，Spring 会按逗号拆成两项）时按属性与方向解析")
    void singleSortParameter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("page", "2");
        request.addParameter("pageSize", "20");
        request.addParameter("sort", "operTime,desc");
        PageParam param = bind(request);

        assertThat(param.getSort()).containsExactly("operTime", "desc");
        Page<Object> page = param.toPage(OperLog.class);
        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(orders(page)).containsExactly("oper_time desc");
    }

    @Test
    @DisplayName("多个 sort 参数：按顺序排序，方向缺省为升序，大小写不敏感")
    void multipleSortParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("sort", "loginTime,DESC", "userName", "operId,asc");
        PageParam param = bind(request);

        assertThat(param.getSort()).hasSize(3);
        assertThat(orders(param.toPage(OperLog.class)))
            .containsExactly("login_time desc", "user_name asc", "oper_id asc");
        assertThat(param.hasSort()).isTrue();

        PageParam inOne = new PageParam();
        inOne.setSort(List.of("loginTime,desc,operTime"));
        assertThat(orders(inOne.toPage(OperLog.class))).containsExactly("login_time desc", "oper_time asc");
    }

    @Test
    @DisplayName("联表查询按表别名排序；@TableField 指定的列名按实体登记的列名映射")
    void aliasAndMappedColumn() {
        PageParam param = new PageParam();
        param.setSort(List.of("title,desc"));
        assertThat(orders(param.toPage(OperLog.class, "o"))).containsExactly("o.oper_title desc");
        assertThat(orders(param.toPage(OperLog.class, " "))).containsExactly("oper_title desc");
        assertThatThrownBy(() -> param.toPage(OperLog.class, "o;drop")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("不在实体中的属性、非法的属性名与缺少属性的方向都拒绝（400）")
    void rejectUnknownSort() {
        PageParam unknown = new PageParam();
        unknown.setSort(List.of("password,desc"));
        assertThatThrownBy(() -> unknown.toPage(OperLog.class))
            .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(400));

        PageParam injection = new PageParam();
        injection.setSort(List.of("oper_time desc; drop table sys_user"));
        assertThatThrownBy(() -> injection.toPage(OperLog.class))
            .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(400));

        PageParam directionOnly = new PageParam();
        directionOnly.setSort(List.of("desc"));
        assertThatThrownBy(directionOnly::toPage).isInstanceOf(ServiceException.class);

        PageParam notEntity = new PageParam();
        notEntity.setSort(List.of("operTime"));
        assertThatThrownBy(() -> notEntity.toPage(String.class)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("不按实体查询的列表：不带排序时正常分页，带排序时拒绝；也可以给出显式的排序白名单")
    void withoutEntity() {
        PageParam param = new PageParam();
        param.setSort(List.of("createTime,desc"));
        assertThatThrownBy(param::toPage).isInstanceOf(ServiceException.class);

        Page<Object> page = param.toPage(Map.of("createTime", "t.create_time"));
        assertThat(orders(page)).containsExactly("t.create_time desc");
        assertThatThrownBy(() -> param.toPage(Map.of("name", "t.name"))).isInstanceOf(ServiceException.class);

        PageParam blank = new PageParam();
        blank.setSort(Arrays.asList(null, " ", ","));
        assertThat(blank.toPage().orders()).isEmpty();
    }

    @Test
    @DisplayName("没有传排序时使用默认排序，传了时不覆盖")
    void defaultSort() {
        PageParam none = new PageParam().defaultSort("operId,desc");
        assertThat(orders(none.toPage(OperLog.class))).containsExactly("oper_id desc");

        PageParam given = new PageParam();
        given.setSort(List.of("operTime"));
        given.defaultSort("operId,desc");
        assertThat(orders(given.toPage(OperLog.class))).containsExactly("oper_time asc");
    }

    @Test
    @DisplayName("只统计行数的分页对象：第 1 页、每页 1 行，执行 COUNT")
    void countOnly() {
        Page<Object> page = PageParam.countOnly();
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.searchCount()).isTrue();
        assertThat(page.orders()).isEmpty();
    }

    @TableName("sys_oper_log")
    static class OperLog {

        @TableId("oper_id")
        private Long operId;

        @TableField("oper_title")
        private String title;

        private String userName;

        private LocalDateTime operTime;

        private LocalDateTime loginTime;

        public Long getOperId() {
            return operId;
        }

        public String getTitle() {
            return title;
        }

        public String getUserName() {
            return userName;
        }

        public LocalDateTime getOperTime() {
            return operTime;
        }

        public LocalDateTime getLoginTime() {
            return loginTime;
        }

    }

}
