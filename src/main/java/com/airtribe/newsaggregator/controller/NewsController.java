package com.airtribe.newsaggregator.controller;

import com.airtribe.newsaggregator.dto.ApiResponse;
import com.airtribe.newsaggregator.dto.ArticleDto;
import com.airtribe.newsaggregator.dto.NewsPreferenceDto;
import com.airtribe.newsaggregator.exception.InvalidSearchParametersException;
import com.airtribe.newsaggregator.exception.NewsApiException;
import com.airtribe.newsaggregator.exception.PreferencesNotFoundException;
import com.airtribe.newsaggregator.exception.RateLimitExceededException;
import com.airtribe.newsaggregator.service.NewsCacheUpdaterService;
import com.airtribe.newsaggregator.service.NewsService;
import com.airtribe.newsaggregator.service.RateLimitService;
import com.airtribe.newsaggregator.service.UserArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final UserArticleService userArticleService;
    private final RateLimitService rateLimitService;
    private final NewsCacheUpdaterService newsCacheUpdaterService;

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<NewsPreferenceDto>> getPreferences(Authentication authentication) {
        String username = authentication.getName();
        log.info("Fetching news preferences for user: {}", username);
        try {
            NewsPreferenceDto preferences = newsService.getPreferences(username);
            return ResponseEntity.ok(ApiResponse.success("Preferences retrieved successfully", preferences));
        } catch (Exception e) {
            log.error("Error retrieving preferences for user: {}", username, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve preferences", "PREF_ERROR", e.getMessage()));
        }
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<NewsPreferenceDto>> updatePreferences(
            @Valid @RequestBody NewsPreferenceDto preferencesDto,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Updating news preferences for user: {}", username);
        try {
            NewsPreferenceDto updatedPreferences = newsService.updatePreferences(username, preferencesDto);
            return ResponseEntity.ok(ApiResponse.success("Preferences updated successfully", updatedPreferences));
        } catch (Exception e) {
            log.error("Error updating preferences for user: {}", username, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to update preferences", "PREF_UPDATE_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/news")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNews(Authentication authentication) {
        String username = authentication.getName();
        log.info("Fetching news for user: {}", username);
        try {
            rateLimitService.checkRateLimit(username);
            
            // Record user activity for cache updates
            newsCacheUpdaterService.recordUserAccess(username);
            
            Map<String, Object> news = newsService.getNews(username);
            return ResponseEntity.ok(ApiResponse.success("News fetched successfully", news));
        } catch (PreferencesNotFoundException e) {
            log.warn("News preferences not found for user: {}", username);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage(), "PREFERENCES_NOT_FOUND", 
                            "Please set your news preferences before fetching news"));
        } catch (NewsApiException e) {
            log.error("News API error for user: {}", username, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to fetch news", "NEWS_API_ERROR", e.getMessage()));
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for user: {}", username);
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-RateLimit-Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            return ResponseEntity.status(429)
                    .headers(headers)
                    .body(ApiResponse.error(e.getMessage(), "RATE_LIMIT_EXCEEDED", 
                            "Too many requests. Please try again later."));
        } catch (Exception e) {
            log.error("Unexpected error fetching news for user: {}", username, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error", "INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    @GetMapping("/news/search/{keyword}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchNews(
            @PathVariable String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("User: {} searching news with keyword: {}, page: {}, pageSize: {}", 
                username, keyword, page, pageSize);
        try {
            rateLimitService.checkRateLimit(username);
            
            // Record user activity for cache updates
            newsCacheUpdaterService.recordUserAccess(username);
            
            Map<String, Object> searchResults = newsService.searchNews(keyword, page, pageSize);
            
            int totalResults = searchResults.containsKey("totalResults") ? 
                    Integer.parseInt(searchResults.get("totalResults").toString()) : 0;
            int totalPages = (int) Math.ceil(totalResults / (double) pageSize);
            
            String message = String.format("Found %d results (page %d of %d)", 
                    totalResults, page, totalPages);

            // Add rate limit headers
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-RateLimit-Remaining", String.valueOf(rateLimitService.getRemainingTokens(username)));
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(ApiResponse.success(message, searchResults));
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for user: {} searching keyword: {}", username, keyword);
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-RateLimit-Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            return ResponseEntity.status(429)
                    .headers(headers)
                    .body(ApiResponse.error(e.getMessage(), "RATE_LIMIT_EXCEEDED", 
                            "Too many requests. Please try again later."));
        } catch (InvalidSearchParametersException e) {
            log.warn("Invalid search parameters from user: {}. Keyword: {}, Error: {}", 
                    username, keyword, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid search parameters", "INVALID_SEARCH", e.getMessage()));
        } catch (NewsApiException e) {
            log.error("News API error while searching for keyword: {}", keyword, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to search news", "NEWS_API_ERROR", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error searching news with keyword: {}", keyword, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error", "SEARCH_ERROR", "An unexpected error occurred"));
        }
    }

    @PostMapping("/news/{articleId}/read")
    public ResponseEntity<ApiResponse<ArticleDto>> markArticleAsRead(
            @PathVariable String articleId,
            @RequestBody Map<String, Object> articleData,
            Authentication authentication) {
        String username = authentication.getName();
        try {
            ArticleDto article = userArticleService.markArticleAsRead(username, articleId, articleData);
            return ResponseEntity.ok(ApiResponse.success("Article marked as read", article));
        } catch (Exception e) {
            log.error("Error marking article as read. User: {}, Article: {}", username, articleId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to mark article as read", "ARTICLE_UPDATE_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/news/{articleId}/favorite")
    public ResponseEntity<ApiResponse<ArticleDto>> markArticleAsFavorite(
            @PathVariable String articleId,
            @RequestBody Map<String, Object> articleData,
            Authentication authentication) {
        String username = authentication.getName();
        try {
            ArticleDto article = userArticleService.markArticleAsFavorite(username, articleId, articleData);
            return ResponseEntity.ok(ApiResponse.success("Article marked as favorite", article));
        } catch (Exception e) {
            log.error("Error marking article as favorite. User: {}, Article: {}", username, articleId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to mark article as favorite", "ARTICLE_UPDATE_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/news/read")
    public ResponseEntity<ApiResponse<List<ArticleDto>>> getReadArticles(Authentication authentication) {
        String username = authentication.getName();
        try {
            List<ArticleDto> articles = userArticleService.getReadArticles(username);
            return ResponseEntity.ok(ApiResponse.success("Read articles retrieved successfully", articles));
        } catch (Exception e) {
            log.error("Error retrieving read articles for user: {}", username, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve read articles", "ARTICLE_FETCH_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/news/favorites")
    public ResponseEntity<ApiResponse<List<ArticleDto>>> getFavoriteArticles(Authentication authentication) {
        String username = authentication.getName();
        try {
            List<ArticleDto> articles = userArticleService.getFavoriteArticles(username);
            return ResponseEntity.ok(ApiResponse.success("Favorite articles retrieved successfully", articles));
        } catch (Exception e) {
            log.error("Error retrieving favorite articles for user: {}", username, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve favorite articles", "ARTICLE_FETCH_ERROR", e.getMessage()));
        }
    }
}
