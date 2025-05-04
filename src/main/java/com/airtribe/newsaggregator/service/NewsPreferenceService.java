package com.airtribe.newsaggregator.service;

import com.airtribe.newsaggregator.dto.NewsPreferenceDto;
import com.airtribe.newsaggregator.entity.NewsPreference;
import com.airtribe.newsaggregator.entity.User;
import com.airtribe.newsaggregator.exception.PreferencesNotFoundException;
import com.airtribe.newsaggregator.repository.NewsPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsPreferenceService {

    private final NewsPreferenceRepository preferenceRepository;
    private final UserService userService;

    public NewsPreferenceDto getPreferences(String username) {
        log.info("Fetching preferences for user: {}", username);
        User user = userService.getCurrentUser(username);
        NewsPreference preferences = preferenceRepository.findByUser(user)
                .orElseGet(() -> {
                    NewsPreference preference = new NewsPreference();
                    preference.setUser(user);
                    return preference;
                });
        return convertToDto(preferences);
    }

    public NewsPreferenceDto updatePreferences(String username, NewsPreferenceDto preferencesDto) {
        log.info("Updating preferences for user: {}. New categories: {}", username, preferencesDto.getCategories());
        User user = userService.getCurrentUser(username);
        NewsPreference preferences = preferenceRepository.findByUser(user)
                .orElseGet(() -> {
                    NewsPreference preference = new NewsPreference();
                    preference.setUser(user);
                    return preference;
                });

        preferences.setCategories(preferencesDto.getCategories());
        preferences.setSources(Optional.ofNullable(preferencesDto.getSources()).orElse(preferences.getSources()));
        preferences.setLanguage(Optional.ofNullable(preferencesDto.getLanguage()).orElse(preferences.getLanguage()));
        preferences.setCountry(Optional.ofNullable(preferencesDto.getCountry()).orElse(preferences.getCountry()));

        preferences = preferenceRepository.save(preferences);
        return convertToDto(preferences);
    }

    public NewsPreference getPreferencesByUsername(String username) {
        User user = userService.getCurrentUser(username);
        return preferenceRepository.findByUser(user)
                .orElseThrow(() -> new PreferencesNotFoundException(username));
    }

    private NewsPreferenceDto convertToDto(NewsPreference preferences) {
        NewsPreferenceDto dto = new NewsPreferenceDto();
        dto.setCategories(preferences.getCategories());
        dto.setSources(preferences.getSources());
        dto.setLanguage(preferences.getLanguage());
        dto.setCountry(preferences.getCountry());
        return dto;
    }
}
