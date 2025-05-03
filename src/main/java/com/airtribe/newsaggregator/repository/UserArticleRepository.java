package com.airtribe.newsaggregator.repository;

import com.airtribe.newsaggregator.entity.User;
import com.airtribe.newsaggregator.entity.UserArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserArticleRepository extends JpaRepository<UserArticle, Long> {
    Optional<UserArticle> findByUserAndArticleId(User user, String articleId);
    List<UserArticle> findByUserAndReadTrue(User user);
    List<UserArticle> findByUserAndFavoriteTrue(User user);
    boolean existsByUserAndArticleId(User user, String articleId);
}
