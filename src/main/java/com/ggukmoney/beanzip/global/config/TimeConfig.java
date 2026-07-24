package com.ggukmoney.beanzip.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId businessZoneId(@Value("${app.business-time-zone:Asia/Seoul}") String zoneId) {
        return ZoneId.of(zoneId);
    }
}
