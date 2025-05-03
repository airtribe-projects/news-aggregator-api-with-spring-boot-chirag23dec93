package com.airtribe.newsaggregator.exception;

public class PreferencesNotFoundException extends RuntimeException {
    public PreferencesNotFoundException(String username) {
        super("News preferences not found for user: " + username + ". Please set your preferences first.");
    }
}
