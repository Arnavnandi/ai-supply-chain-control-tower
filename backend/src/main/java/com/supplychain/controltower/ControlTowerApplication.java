package com.supplychain.controltower;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ControlTowerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlTowerApplication.class, args);
    }
}
