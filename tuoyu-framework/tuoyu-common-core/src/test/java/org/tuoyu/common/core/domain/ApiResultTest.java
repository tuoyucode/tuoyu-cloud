package org.tuoyu.common.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("接口响应 ApiResult 与分页结果 PageResult")
class ApiResultTest {

    @Test
    @DisplayName("成功响应：code 为 0，HTTP 200，默认提示“操作成功”")
    void ok() {
        ApiResult<Void> empty = ApiResult.ok();
        ApiResult<String> withData = ApiResult.ok("payload");
        ApiResult<String> withMessage = ApiResult.ok("已保存", "payload");

        assertThat(empty.getCode()).isEqualTo(ApiResult.SUCCESS).isZero();
        assertThat(empty.getMessage()).isEqualTo(ApiResult.DEFAULT_SUCCESS_MESSAGE);
        assertThat(empty.getData()).isNull();
        assertThat(empty.httpStatus()).isEqualTo(200);
        assertThat(withData.getData()).isEqualTo("payload");
        assertThat(withMessage.getMessage()).isEqualTo("已保存");
        assertThat(withMessage.getData()).isEqualTo("payload");
        assertThat(withMessage.getTraceId()).isNull();
    }

    @Test
    @DisplayName("失败响应：code 在 400 ~ 599 之间时就是 HTTP 状态，业务自定义错误码对应 400；code 不能为 0")
    void fail() {
        assertThat(ApiResult.fail(401, "请先登录").httpStatus()).isEqualTo(401);
        assertThat(ApiResult.fail(429, "请求过于频繁").httpStatus()).isEqualTo(429);
        assertThat(ApiResult.fail(503, "服务暂不可用").httpStatus()).isEqualTo(503);
        assertThat(ApiResult.fail(10001, "库存不足").httpStatus()).isEqualTo(400);
        assertThat(ApiResult.fail(-1, "未知").httpStatus()).isEqualTo(400);
        assertThat(ApiResult.fail(399, "边界").httpStatus()).isEqualTo(400);
        assertThat(ApiResult.fail(600, "边界").httpStatus()).isEqualTo(400);
        assertThat(ApiResult.fail(400, "x").getData()).isNull();
        assertThatThrownBy(() -> ApiResult.fail(ApiResult.SUCCESS, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("字段可读写（反序列化与 traceId 填入使用），toString 不带数据")
    void accessors() {
        ApiResult<List<String>> result = new ApiResult<>();
        result.setCode(400);
        result.setMessage("参数错误");
        result.setData(List.of("secret"));
        result.setTraceId("4bf92f3577b34da6a3ce929d0e0e4736");

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("参数错误");
        assertThat(result.getData()).containsExactly("secret");
        assertThat(result.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(result.toString()).contains("400", "参数错误", "4bf92f3577b34da6a3ce929d0e0e4736").doesNotContain("secret");
    }

    @Test
    @DisplayName("分页结果：items 与 total；null 列表按空列表处理；不分页的列表 total 为长度")
    void pageResult() {
        PageResult<String> page = PageResult.of(List.of("a", "b"), 12);
        assertThat(page.getItems()).containsExactly("a", "b");
        assertThat(page.getTotal()).isEqualTo(12);

        PageResult<String> nullItems = PageResult.of(null, 3);
        assertThat(nullItems.getItems()).isNotNull().isEmpty();
        assertThat(nullItems.getTotal()).isEqualTo(3);

        PageResult<String> all = PageResult.of(List.of("a", "b", "c"));
        assertThat(all.getTotal()).isEqualTo(3);
        assertThat(PageResult.<String>of(null).getTotal()).isZero();

        PageResult<String> empty = PageResult.empty();
        assertThat(empty.getItems()).isEmpty();
        assertThat(empty.getTotal()).isZero();
    }

    @Test
    @DisplayName("分页结果的元素转换保留总数；setter 不接受 null 列表")
    void pageResultMapAndSetters() {
        PageResult<Integer> lengths = PageResult.of(List.of("a", "bb"), 9).map(String::length);
        assertThat(lengths.getItems()).containsExactly(1, 2);
        assertThat(lengths.getTotal()).isEqualTo(9);

        PageResult<String> page = new PageResult<>();
        page.setItems(null);
        page.setTotal(5);
        assertThat(page.getItems()).isNotNull().isEmpty();
        page.setItems(new ArrayList<>(List.of("x")));
        assertThat(page.getItems()).containsExactly("x");
        assertThat(page.getTotal()).isEqualTo(5);
    }

}
