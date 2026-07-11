package com.CampusToursLive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling registers the ScheduledAnnotationBeanPostProcessor that actually triggers
// @Scheduled methods (e.g. OccurrenceHorizonJob, CTL-54 Task 4) -- without it, a @Scheduled bean
// silently never runs.
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
