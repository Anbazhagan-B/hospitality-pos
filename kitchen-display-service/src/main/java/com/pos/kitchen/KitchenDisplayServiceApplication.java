package com.pos.kitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.pos.kitchen", "com.pos.common"})
@EnableJpaAuditing
public class KitchenDisplayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(KitchenDisplayServiceApplication.class, args);
    }
}
