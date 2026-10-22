package com.suivicontrats.front;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FrontApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrontApplication.class, args);
    }
}
