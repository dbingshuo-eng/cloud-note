package com.clouddisk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
public class ShareConfiguration {

    @Bean
    PasswordEncoder sharePasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
