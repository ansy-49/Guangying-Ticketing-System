package com.guangying.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求DTO
 */
@Data
public class UserRegisterDTO implements Serializable {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 32, message = "账号长度3-32个字符")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度6-64个字符")
    private String password;

    @Size(max = 32, message = "昵称不超过32个字符")
    private String userNick;

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
