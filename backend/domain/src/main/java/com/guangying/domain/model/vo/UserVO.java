package com.guangying.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户视图对象
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserVO implements Serializable {

    private Long id;
    private String account;
    private String userNick;
    private String userHeadImg;
    private String token;
    private Integer points;
}
