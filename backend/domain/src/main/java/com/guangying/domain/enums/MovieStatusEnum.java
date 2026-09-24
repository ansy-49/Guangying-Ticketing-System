package com.guangying.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 电影状态枚举
 */
@Getter
@AllArgsConstructor
public enum MovieStatusEnum {

    COMING(0, "即将上映"),
    HOT(1, "正在热映"),
    OFF(2, "已下映");

    private final int code;
    private final String desc;

    public static MovieStatusEnum of(int code) {
        for (MovieStatusEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }
}
