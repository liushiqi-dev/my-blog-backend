package com.liushiqi.blogmain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Token响应DTO
 */
@Data
@AllArgsConstructor
public class TokenResponse {
    private String token;
}