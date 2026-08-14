package com.pos.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.pos.admin", "com.pos.common"})
@EnableJpaAuditing
public class AdminPanelServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminPanelServiceApplication.class, args);
    }
}
