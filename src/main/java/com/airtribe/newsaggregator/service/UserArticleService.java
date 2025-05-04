package com.airtribe.newsaggregator.service;

import com.airtribe.newsaggregator.dto.ArticleDto;
import com.airtribe.newsaggregator.entity.User;
import com.airtribe.newsaggregator.entity.UserArticle;
import com.airtribe.newsaggregator.repository.UserArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserArticleService {

    private final UserArticleRepository userArticleRepository;
    private final UserService userService;

    @Transactional
    public ArticleDto markArticleAsRead(String username, String articleId, Map<String, Object> articleData) {
        User user = userService.getCurrentUser(username);
        UserArticle userArticle = getUserArticle(user, articleId, articleData);
        userArticle.setRead(true);
        userArticle.setInteractionDate(LocalDateTime.now());
        userArticle = userArticleRepository.save(userArticle);
        return convertToDto(userArticle);
    }

    @Transactional
    public ArticleDto markArticleAsFavorite(String username, String articleId, Map<String, Object> articleData) {
        User user = userService.getCurrentUser(username);
        UserArticle userArticle = getUserArticle(user, articleId, articleData);
        userArticle.setFavorite(true);
        userArticle.setInteractionDate(LocalDateTime.now());
        userArticle = userArticleRepository.save(userArticle);
        return convertToDto(userArticle);
    }

    @Transactional(readOnly = true)
    public List<ArticleDto> getReadArticles(String username) {
        User user = userService.getCurrentUser(username);
        return userArticleRepository.findByUserAndReadTrue(user)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ArticleDto> getFavoriteArticles(String username) {
        User user = userService.getCurrentUser(username);
        return userArticleRepository.findByUserAndFavoriteTrue(user)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private UserArticle getUserArticle(User user, String articleId, Map<String, Object> articleData) {
        return userArticleRepository.findByUserAndArticleId(user, articleId)
                .orElseGet(() -> createUserArticle(user, articleId, articleData));
    }

    private UserArticle createUserArticle(User user, String articleId, Map<String, Object> articleData) {
        return UserArticle.builder()
                .user(user)
                .articleId(articleId)
                .title(getStringValue(articleData, "title"))
                .description(getStringValue(articleData, "description"))
                .url(getStringValue(articleData, "url"))
                .source(getSourceName(articleData))
                .publishedAt(parsePublishedDate(getStringValue(articleData, "publishedAt")))
                .interactionDate(LocalDateTime.now())
                .build();
    }

    private String getStringValue(Map<String, Object> data, String key) {
        return data.containsKey(key) ? String.valueOf(data.get(key)) : null;
    }

    private String getSourceName(Map<String, Object> articleData) {
        if (articleData.containsKey("source")) {
            Object source = articleData.get("source");
            if (source instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sourceMap = (Map<String, Object>) source;
                return sourceMap.containsKey("name") ? String.valueOf(sourceMap.get("name")) : null;
            }
        }
        return null;
    }

    private LocalDateTime parsePublishedDate(String dateStr) {
        try {
            return dateStr != null ? LocalDateTime.parse(dateStr.replace("Z", "")) : null;
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private ArticleDto convertToDto(UserArticle article) {
        return ArticleDto.builder()
                .id(article.getArticleId())
                .title(article.getTitle())
                .description(article.getDescription())
                .url(article.getUrl())
                .source(article.getSource())
                .publishedAt(article.getPublishedAt())
                .read(article.isRead())
                .favorite(article.isFavorite())
                .interactionDate(article.getInteractionDate())
                .build();
    }
}
