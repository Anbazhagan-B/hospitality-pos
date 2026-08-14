package com.pos.cook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.pos.cook", "com.pos.common"})
public class CookServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CookServiceApplication.class, args);
    }
}
