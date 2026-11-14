package org.tuoyu.common.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DictService 的默认方法")
class DictServiceTest {

    @Test
    @DisplayName("两参的 getDictLabel 按逗号分隔调用三参版本")
    void labelWithDefaultSeparator() {
        DictService service = new DictService() {
            @Override
            public String getDictLabel(String dictType, String dictValue, String separator) {
                return dictType + "|" + dictValue + "|" + separator;
            }

            @Override
            public String getDictValue(String dictType, String dictLabel, String separator) {
                return null;
            }

            @Override
            public Map<String, String> getAllDictByDictType(String dictType) {
                return Map.of();
            }
        };

        assertThat(service.getDictLabel("sys_user_sex", "0,1")).isEqualTo("sys_user_sex|0,1|,");
    }
}
