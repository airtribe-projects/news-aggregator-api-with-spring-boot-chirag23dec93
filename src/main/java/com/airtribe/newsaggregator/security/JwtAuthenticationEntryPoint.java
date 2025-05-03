package com.airtribe.newsaggregator.security;

import com.airtribe.newsaggregator.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException {
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String message = "Authentication failed: ";
        if (authException.getMessage() != null && !authException.getMessage().isEmpty()) {
            message += authException.getMessage();
        } else {
            message += "Invalid or expired JWT token";
        }

        ApiResponse<?> errorResponse = ApiResponse.error(
            message,
            "UNAUTHORIZED",
            "Please provide valid authentication credentials"
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
