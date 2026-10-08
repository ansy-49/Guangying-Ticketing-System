package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.guangying.domain.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserPO extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String account;

    /** 密码(BCrypt加密) */
    private String password;

    /** 用户昵称 */
    private String userNick;

    /** 头像URL */
    private String userHeadImg;

    /** 用户积分(1积分=1元) */
    private Integer points = 0;

    /** 角色：USER / ADMIN。管理接口必须由服务端根据该字段授权。 */
    private String role = "USER";
}
