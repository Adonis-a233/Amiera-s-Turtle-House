package com.example.community.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ContactVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;
    private String avatar;
    private String bio;
}
