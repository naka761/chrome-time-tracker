package com.example.chrometimetracker.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.chrometimetracker.model.UsageCategory;

/**
 * ドメイン名を利用カテゴリーへ分類する。
 *
 * 最初はJava内の固定ルールで管理する。
 */
@Component
public class SiteCategoryClassifier {

    public UsageCategory classify(String rawSite) {
        if (rawSite == null || rawSite.isBlank()) {
            return UsageCategory.OTHER;
        }

        String site = rawSite
                .strip()
                .toLowerCase(Locale.ROOT);

        /*
         * AI
         */
        if (matchesAny(
                site,
                "chatgpt.com",
                "claude.ai",
                "gemini.google.com",
                "perplexity.ai",
                "copilot.microsoft.com",
                "poe.com",
                "deepseek.com"
        )) {
            return UsageCategory.AI;
        }

        /*
         * 開発・学習
         */
        if (matchesAny(
                site,
                "github.com",
                "gitlab.com",
                "stackoverflow.com",
                "stackexchange.com",
                "spring.io",
                "adoptium.net",
                "docs.oracle.com",
                "developer.chrome.com",
                "developer.mozilla.org",
                "mvnrepository.com",
                "aws.amazon.com",
                "console.aws.amazon.com"
        )) {
            return UsageCategory.DEVELOPMENT;
        }

        /*
         * 動画・音楽
         */
        if (matchesAny(
                site,
                "youtube.com",
                "youtu.be",
                "twitch.tv",
                "nicovideo.jp",
                "niconico.jp",
                "spotify.com",
                "netflix.com"
        )) {
            return UsageCategory.VIDEO;
        }

        /*
         * SNS
         */
        if (matchesAny(
                site,
                "x.com",
                "twitter.com",
                "reddit.com",
                "instagram.com",
                "facebook.com",
                "threads.net",
                "tiktok.com"
        )) {
            return UsageCategory.SNS;
        }

        /*
         * 検索・情報収集
         *
         * gemini.google.comは先にAIとして判定済み。
         */
        if (matchesAny(
                site,
                "google.com",
                "bing.com",
                "duckduckgo.com",
                "wikipedia.org",
                "yahoo.co.jp"
        )) {
            return UsageCategory.SEARCH;
        }

        return UsageCategory.OTHER;
    }

    private boolean matchesAny(
            String site,
            String... domains
    ) {
        for (String domain : domains) {
            if (matchesDomain(site, domain)) {
                return true;
            }
        }

        return false;
    }

    /**
     * example.comと、そのサブドメインを一致扱いにする。
     */
    private boolean matchesDomain(
            String site,
            String domain
    ) {
        return site.equals(domain)
                || site.endsWith("." + domain);
    }
}