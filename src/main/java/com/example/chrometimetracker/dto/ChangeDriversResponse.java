package com.example.chrometimetracker.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 直近7日と前7日の増減要因。
 */
public record ChangeDriversResponse(
        boolean available,

        LocalDate currentStartDate,
        LocalDate currentEndDate,

        LocalDate previousStartDate,
        LocalDate previousEndDate,

        List<UsageChangeResponse> categoryChanges,
        List<UsageChangeResponse> siteChanges
) {
}