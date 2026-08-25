package com.example.chrometimetracker.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.chrometimetracker.dto.AnalyticsResponse;
import com.example.chrometimetracker.service.AnalyticsService;

/**
 * 分析画面用API。
 */
@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(
            AnalyticsService analyticsService
    ) {
        this.analyticsService = analyticsService;
    }

    /**
     * 例:
     * GET /api/analytics
     * GET /api/analytics?date=2026-08-25
     */
    @GetMapping("/analytics")
    public AnalyticsResponse getAnalytics(
            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate date
    ) {
        LocalDate targetDate =
                date == null
                        ? LocalDate.now()
                        : date;

        return analyticsService.getAnalytics(
                targetDate
        );
    }
}