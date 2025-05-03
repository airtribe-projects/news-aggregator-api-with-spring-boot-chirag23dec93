package com.airtribe.newsaggregator.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

@Data
public class NewsPreferenceDto {
    @NotEmpty(message = "At least one category is required")
    private Set<String> categories;
    
    private Set<String> sources;
    private String language;
    private String country;
}
