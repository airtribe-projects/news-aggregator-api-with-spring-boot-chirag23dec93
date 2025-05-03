package com.airtribe.newsaggregator.service;

import com.airtribe.newsaggregator.dto.NewsPreferenceDto;
import com.airtribe.newsaggregator.entity.NewsPreference;
import com.airtribe.newsaggregator.exception.NewsApiException;
import com.airtribe.newsaggregator.exception.InvalidSearchParametersException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class NewsService {

    private final WebClient webClient;
    private final NewsPreferenceService newsPreferenceService;

    public NewsService(
            @Value("${news.api.key}") String apiKey,
            @Value("${news.api.base-url}") String baseUrl,
            WebClient.Builder webClientBuilder,
            NewsPreferenceService newsPreferenceService) {

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Api-Key", apiKey)
                .build();
        
        this.newsPreferenceService = newsPreferenceService;
        
        log.info("NewsService initialized with base URL: {}", baseUrl);
    }

    public NewsPreferenceDto getPreferences(String username) {
        log.info("Fetching preferences for user: {}", username);
        try {
            return newsPreferenceService.getPreferences(username);
        } catch (Exception e) {
            log.error("Error fetching preferences for user: {}", username, e);
            throw new RuntimeException("Failed to fetch preferences: " + e.getMessage());
        }
    }

    @CacheEvict(value = "newsCache", key = "#username")
    public NewsPreferenceDto updatePreferences(String username, NewsPreferenceDto preferencesDto) {
        log.info("Updating preferences for user: {}. New categories: {}", username, preferencesDto.getCategories());
        try {
            log.info("Evicting news cache for user {} due to preference update", username);
            return newsPreferenceService.updatePreferences(username, preferencesDto);
        } catch (Exception e) {
            log.error("Error updating preferences for user: {}", username, e);
            throw new RuntimeException("Failed to update preferences: " + e.getMessage());
        }
    }

    @Cacheable(value = "newsCache", key = "#username", unless = "#result == null")
    public Map<String, Object> getNews(String username) {
        long startTime = System.currentTimeMillis();
        log.info("Cache MISS - Fetching fresh news from API for user: {}", username);
        
        NewsPreference preferences = newsPreferenceService.getPreferencesByUsername(username);
        log.debug("User preferences - Country: {}, Language: {}, Categories: {}", 
                preferences.getCountry(), preferences.getLanguage(), preferences.getCategories());
        
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> {
                        var uri = uriBuilder
                                .path("/top-headlines")
                                .queryParam("country", preferences.getCountry())
                                .queryParam("language", preferences.getLanguage())
                                .queryParamIfPresent("category", preferences.getCategories().stream().findFirst())
                                .build();
                        log.debug("Making request to News API: {}", uri);
                        return uri;
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(throwable -> {
                                if (throwable instanceof WebClientResponseException) {
                                    WebClientResponseException ex = (WebClientResponseException) throwable;
                                    log.error("API request failed: Status={}, Body={}, Headers={}", 
                                            ex.getStatusCode(), ex.getResponseBodyAsString(), 
                                            ex.getHeaders());
                                    return ex.getStatusCode().is5xxServerError();
                                }
                                return false;
                            }))
                    .block();
            
            long endTime = System.currentTimeMillis();
            log.info("Successfully fetched fresh news for user: {}, took {}ms", 
                    username, (endTime - startTime));
            
            validateNewsApiResponse(response);
            return response;
        } catch (WebClientResponseException e) {
            log.error("News API error for user: {}. Status: {}, Body: {}, Headers: {}", 
                    username, e.getStatusCode(), e.getResponseBodyAsString(), e.getHeaders(), e);
            
            if (e.getStatusCode().value() == 401) {
                throw new NewsApiException("Unauthorized: Invalid API key or authentication failed");
            }
            
            throw new NewsApiException(String.format("News API error (%s): %s", 
                    e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Error fetching news for user: {}", username, e);
            throw new NewsApiException("Failed to fetch news: " + e.getMessage(), e);
        }
    }

    @Cacheable(value = "searchCache", key = "{#keyword, #page, #pageSize}", unless = "#result == null")
    public Map<String, Object> searchNews(String keyword, int page, int pageSize) {
        validateSearchParameters(keyword, page, pageSize);
        
        long startTime = System.currentTimeMillis();
        log.info("Searching news for keyword: {}, page: {}, pageSize: {}", keyword, page, pageSize);

        // Get date 30 days ago for better search results
        String fromDate = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_DATE);
        log.debug("Using from date: {} for search", fromDate);
        
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> {
                        var uri = uriBuilder
                                .path("/everything")
                                .queryParam("q", keyword)
                                .queryParam("from", fromDate)
                                .queryParam("sortBy", "popularity")
                                .queryParam("language", "en")
                                .queryParam("page", page)
                                .queryParam("pageSize", Math.min(pageSize, 100))
                                .build();
                        log.debug("Making request to News API: {}", uri);
                        return uri;
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(throwable -> {
                                if (throwable instanceof WebClientResponseException) {
                                    WebClientResponseException ex = (WebClientResponseException) throwable;
                                    log.error("API request failed: Status={}, Body={}, Headers={}", 
                                            ex.getStatusCode(), ex.getResponseBodyAsString(), 
                                            ex.getHeaders());
                                    return ex.getStatusCode().is5xxServerError();
                                }
                                return false;
                            }))
                    .block();
            
            long endTime = System.currentTimeMillis();
            log.info("Successfully searched news for keyword: {}, took {}ms", 
                    keyword, (endTime - startTime));
            
            validateNewsApiResponse(response);
            return response;
        } catch (WebClientResponseException e) {
            log.error("News API error while searching for keyword: {}. Status: {}, Body: {}, Headers: {}", 
                    keyword, e.getStatusCode(), e.getResponseBodyAsString(), e.getHeaders(), e);
            
            if (e.getStatusCode().value() == 401) {
                throw new NewsApiException("Unauthorized: Invalid API key or authentication failed");
            }
            
            throw new NewsApiException(String.format("News API error (%s): %s", 
                    e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Error searching news for keyword: {}", keyword, e);
            throw new NewsApiException("Failed to search news: " + e.getMessage(), e);
        }
    }

    private void validateSearchParameters(String keyword, int page, int pageSize) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new InvalidSearchParametersException("Search keyword cannot be empty");
        }
        
        if (keyword.length() < 2) {
            throw new InvalidSearchParametersException("Search keyword must be at least 2 characters long");
        }
        
        if (page < 1) {
            throw new InvalidSearchParametersException("Page number must be greater than 0");
        }
        
        if (pageSize < 1 || pageSize > 100) {
            throw new InvalidSearchParametersException("Page size must be between 1 and 100");
        }
    }

    private void validateNewsApiResponse(Map<String, Object> response) {
        if (response == null) {
            throw new NewsApiException("Received null response from News API");
        }
        
        if (response.containsKey("status") && "error".equals(response.get("status"))) {
            String errorMessage = response.containsKey("message") ? 
                    response.get("message").toString() : "Unknown error from News API";
            String errorCode = response.containsKey("code") ? 
                    response.get("code").toString() : "UNKNOWN_ERROR";
            throw new NewsApiException(String.format("News API error (%s): %s", errorCode, errorMessage));
        }
    }

    @Scheduled(fixedRate = 900000) // 15 minutes
    @CacheEvict(value = {"newsCache", "searchCache"}, allEntries = true)
    public void evictAllCaches() {
        log.info("Evicting all news caches");
    }
}
