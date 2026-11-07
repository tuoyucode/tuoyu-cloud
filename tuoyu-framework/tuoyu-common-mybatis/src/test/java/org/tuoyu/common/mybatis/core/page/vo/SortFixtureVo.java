package org.tuoyu.common.mybatis.core.page.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * {@code SortableColumnsTest.SortFixture} 按约定对应的 Vo：接口中不下发的字段不能排序
 */
public class SortFixtureVo {

    private Long id;

    @JsonIgnore
    private String phoneNumber;

    @JsonIgnore(false)
    private String remark;

    public Long getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRemark() {
        return remark;
    }

}
