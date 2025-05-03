package com.airtribe.newsaggregator.repository;

import com.airtribe.newsaggregator.entity.NewsPreference;
import com.airtribe.newsaggregator.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsPreferenceRepository extends JpaRepository<NewsPreference, Long> {
    Optional<NewsPreference> findByUser(User user);
}
