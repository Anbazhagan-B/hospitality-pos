package com.pos.check;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.pos.check", "com.pos.common"})
@EnableJpaAuditing
public class CheckServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CheckServiceApplication.class, args);
    }
}
