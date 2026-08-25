package com.example.chrometimetracker.controller;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.chrometimetracker.dto.ContextRequest;
import com.example.chrometimetracker.dto.DailySummaryResponse;
import com.example.chrometimetracker.service.TrackerService;

/**
 * Chrome拡張と集計画面からのアクセスを受け付けるController。
 */
@RestController
@RequestMapping("/api")
public class TrackerController {

    private final TrackerService trackerService;

    public TrackerController(
            TrackerService trackerService
    ) {
        this.trackerService = trackerService;
    }

    /**
     * Chrome拡張から現在表示中のサイトを受信する。
     */
    @PostMapping("/context")
    public String updateContext(
            @RequestBody ContextRequest request
    ) {
        if (request.observedAt() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "observedAtは正のUnix時刻で指定してください。"
            );
        }

        try {
            trackerService.updateContext(
                    request.site(),
                    Instant.ofEpochMilli(
                            request.observedAt()
                    )
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception
            );
        }

        return "OK";
    }

    /**
     * 日別・サイト別の使用時間を返す。
     *
     * dateを省略した場合は今日を集計する。
     *
     * 例:
     * GET /api/summary
     * GET /api/summary?date=2026-08-25
     */
    @GetMapping("/summary")
    public DailySummaryResponse getDailySummary(
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

        return trackerService.getDailySummary(
                targetDate
        );
    }
}