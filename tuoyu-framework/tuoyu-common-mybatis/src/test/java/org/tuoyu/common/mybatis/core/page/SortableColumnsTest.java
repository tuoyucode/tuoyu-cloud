package org.tuoyu.common.mybatis.core.page;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("可排序属性：排除口令密钥类、不查询、不下发的字段")
class SortableColumnsTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "sortFixture"),
            SortFixture.class);
    }

    @Test
    @DisplayName("只保留普通字段：主键、普通列、@JsonIgnore(false) 的字段")
    void onlyPlainColumns() {
        Map<String, String> columns = SortableColumns.of(SortFixture.class);
        assertThat(columns).containsOnlyKeys("id", "userName", "remark", "createTime");
        assertThat(columns).containsEntry("userName", "user_name");
        assertThat(SortableColumns.of(SortFixture.class)).isSameAs(columns);
    }

    @Test
    @DisplayName("敏感属性排序时按不支持的属性拒绝（400）")
    void rejectSensitiveSort() {
        for (String property : List.of("password", "clientSecret", "accessKey", "apiToken", "phoneNumber",
            "email", "hidden")) {
            PageParam param = new PageParam();
            param.setSort(List.of(property + ",desc"));
            assertThatThrownBy(() -> param.toPage(SortFixture.class))
                .as(property)
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(400));
        }
        PageParam allowed = new PageParam();
        allowed.setSort(List.of("createTime,desc"));
        assertThat(allowed.toPage(SortFixture.class).orders()).hasSize(1);
    }

    @Test
    @DisplayName("口令、密钥类名称不区分大小写")
    void secretNames() {
        assertThat(SortableColumns.hasSecretName("password")).isTrue();
        assertThat(SortableColumns.hasSecretName("oldPassword")).isTrue();
        assertThat(SortableColumns.hasSecretName("secretKey")).isTrue();
        assertThat(SortableColumns.hasSecretName("AccessKey")).isTrue();
        assertThat(SortableColumns.hasSecretName("refreshToken")).isTrue();
        assertThat(SortableColumns.hasSecretName("privateKey")).isTrue();
        assertThat(SortableColumns.hasSecretName("userName")).isFalse();
        assertThat(SortableColumns.hasSecretName("createTime")).isFalse();
    }

    @Test
    @DisplayName("没有表信息的类不能按实体属性排序")
    void requiresTableInfo() {
        assertThatThrownBy(() -> SortableColumns.of(String.class)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SortableColumns.of(null)).isInstanceOf(IllegalStateException.class);
    }

    @TableName("sort_fixture")
    static class SortFixture {

        @TableId("id")
        private Long id;

        private String userName;

        private String password;

        private String clientSecret;

        private String accessKey;

        private String apiToken;

        /**
         * Vo 中 @JsonIgnore
         */
        private String phoneNumber;

        /**
         * 实体上 @JsonIgnore
         */
        @JsonIgnore
        private String email;

        /**
         * Vo 中 @JsonIgnore(false)，仍可排序
         */
        private String remark;

        @TableField(select = false)
        private String hidden;

        private LocalDateTime createTime;

        public Long getId() {
            return id;
        }

        public String getUserName() {
            return userName;
        }

        public String getPassword() {
            return password;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public String getApiToken() {
            return apiToken;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public String getEmail() {
            return email;
        }

        public String getRemark() {
            return remark;
        }

        public String getHidden() {
            return hidden;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

    }

}
