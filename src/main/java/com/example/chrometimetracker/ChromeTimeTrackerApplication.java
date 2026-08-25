package com.example.chrometimetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChromeTimeTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                ChromeTimeTrackerApplication.class,
                args
        );
    }
}