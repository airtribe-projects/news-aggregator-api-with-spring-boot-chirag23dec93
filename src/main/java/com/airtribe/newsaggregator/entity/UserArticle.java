package com.airtribe.newsaggregator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_articles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "article_id"}))
public class UserArticle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "article_id", nullable = false)
    private String articleId;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String url;

    private String source;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "is_read")
    private boolean read;

    @Column(name = "is_favorite")
    private boolean favorite;

    @Column(name = "interaction_date")
    private LocalDateTime interactionDate;
}
