package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeedDataService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.supplychain.controltower.repository.CompanyRepository companyRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking system seed data...");

        com.supplychain.controltower.entity.Company demoCompany = companyRepository.findByCode("DEMO")
                .orElseGet(() -> companyRepository.save(com.supplychain.controltower.entity.Company.builder()
                        .name("Demo Supply Chain Corp")
                        .code("DEMO")
                        .status("ACTIVE")
                        .build()));

        // 1. Seed Users
        if (userRepository.count() == 0) {
            log.info("Seeding initial administrative users...");
            userRepository.save(User.builder()
                    .username("admin")
                    .email("admin@supplychain.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ROLE_ADMIN)
                    .company(demoCompany)
                    .build());

            userRepository.save(User.builder()
                    .username("manager")
                    .email("manager@supplychain.com")
                    .password(passwordEncoder.encode("manager123"))
                    .role(Role.ROLE_SUPPLY_CHAIN_MANAGER)
                    .company(demoCompany)
                    .build());
        }

        // Automatic operational record insertion disabled by default.
        // Companies must start with zero operational records unless explicitly imported via CSV.
        log.info("System startup check complete: Automatic operational data seeding disabled. All tenant data must be explicitly imported.");
    }
}

