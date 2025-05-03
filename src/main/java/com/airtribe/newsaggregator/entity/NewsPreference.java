package com.airtribe.newsaggregator.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "news_preferences")
public class NewsPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ElementCollection
    private Set<String> categories = new HashSet<>();

    @ElementCollection
    private Set<String> sources = new HashSet<>();

    private String language = "en";

    private String country = "us";
}
