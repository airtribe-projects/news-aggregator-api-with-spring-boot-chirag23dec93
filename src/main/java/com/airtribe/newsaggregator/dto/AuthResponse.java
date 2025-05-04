package com.airtribe.newsaggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String message;
    private String status;
    private Object data;

    public static AuthResponse success(String message, Object data) {
        return new AuthResponse(message, "success", data);
    }

    public static AuthResponse error(String message) {
        return new AuthResponse(message, "error", null);
    }
}
