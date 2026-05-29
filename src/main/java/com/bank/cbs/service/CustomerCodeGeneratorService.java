package com.bank.cbs.service;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.bank.cbs.repository.jpa.CustomerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerCodeGeneratorService {
    private final CustomerRepository customerRepository;

    public String generate() {
        String code;
        int year = LocalDate.now().getYear();
        do {
            int random = ThreadLocalRandom.current().nextInt(100000, 999999);
            code = "CBS" + year + random;
        } while (customerRepository.existsByCustomerCode(code));
        return code;
    }
}
