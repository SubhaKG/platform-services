package com.kghospital.tenantconfig.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CaffeineCacheCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * PLAT-005 §5.2 FR-10 — "Config values MUST be cached in-process per
 * resolution context with a TTL of 60 seconds. Explicit cache eviction
 * MUST occur on any config write." TTL handles the "no module restart
 * required" requirement passively; explicit eviction (see
 * ConfigValueService.evictCache()) handles the active case.
 *
 * §6.1 SLO: single resolve p99 < 5ms cache hit, batch (20 keys) < 15ms.
 * Caffeine in-process cache is the only way to meet sub-5ms latency —
 * a network round-trip to any external cache (Redis) would already
 * exceed the SLO before any other work happens.
 */
@EnableCaching
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("configResolve");
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(50_000));
        return manager;
    }
}
