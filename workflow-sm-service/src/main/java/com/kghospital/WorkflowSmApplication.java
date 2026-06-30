package com.kghospital.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorkflowSmApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowSmApplication.class, args);
    }
}
