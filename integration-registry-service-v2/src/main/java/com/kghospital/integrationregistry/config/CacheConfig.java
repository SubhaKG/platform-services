package com.kghospital.integrationregistry.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * PLAT-012 §6.3 FR-09 — "cached in-process with a TTL of 60 seconds...
 * keeps the Integration Engine off the critical DB path."
 * §7.1 SLO: lookup p99 < 10ms cache hit — Caffeine in-process, same
 * reasoning as tenant-config-service's CacheConfig.
 */
@EnableCaching
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("adapterLookup");
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(10_000));
        return manager;
    }
}
