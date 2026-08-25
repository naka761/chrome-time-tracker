package com.example.chrometimetracker.model;

/**
 * Webサイトの利用カテゴリー。
 */
public enum UsageCategory {

    AI(
            "ai",
            "AI"
    ),

    DEVELOPMENT(
            "development",
            "開発・学習"
    ),

    VIDEO(
            "video",
            "動画・音楽"
    ),

    SEARCH(
            "search",
            "検索・情報収集"
    ),

    SNS(
            "sns",
            "SNS"
    ),

    OTHER(
            "other",
            "その他"
    );

    private final String key;
    private final String label;

    UsageCategory(
            String key,
            String label
    ) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }
}