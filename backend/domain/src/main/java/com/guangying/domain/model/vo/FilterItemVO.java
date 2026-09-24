package com.guangying.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 筛选项视图对象 — 递归结构，用于品牌/厅型/服务/商区/地铁筛选
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilterItemVO implements Serializable {

    private String name;
    private Integer id;
    private Integer count;
    private List<FilterItemVO> subItems;

    public FilterItemVO(String name, List<FilterItemVO> subItems) {
        this.name = name;
        this.subItems = subItems;
    }

    public FilterItemVO(Integer id, String name, Integer count) {
        this.id = id;
        this.name = name;
        this.count = count;
    }
}
