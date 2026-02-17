package com.photomini.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ParseRequest {
    @NotBlank(message = "URL不能为空")
    private String url;
}
