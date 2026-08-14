package com.pos.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.pos.payment", "com.pos.common"})
@EnableJpaAuditing
public class PaymentGatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentGatewayServiceApplication.class, args);
    }
}
