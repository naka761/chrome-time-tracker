package com.example.chrometimetracker.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/health/database")
    public String databaseHealth() {
        return jdbcTemplate.queryForObject(
                "SELECT 'DB OK'",
                String.class
        );
    }
}