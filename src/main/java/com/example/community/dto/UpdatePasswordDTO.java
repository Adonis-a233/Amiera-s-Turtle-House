package com.example.community.dto;

import lombok.Data;

/**
 * 修改密码DTO
 */
@Data
public class UpdatePasswordDTO {
    private String oldPassword;
    private String newPassword;
}
