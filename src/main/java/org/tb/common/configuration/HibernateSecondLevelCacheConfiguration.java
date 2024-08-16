package org.tb.common.configuration;

import org.hibernate.cache.jcache.ConfigSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.io.IOException;
import java.net.URI;


@Configuration
public class HibernateSecondLevelCacheConfiguration {

    @Bean
    public HibernatePropertiesCustomizer hibernateSecondLevelCacheCustomizer(ResourceLoader resourceLoader,
                                                                             @Value("${spring.jpa.properties.hibernate.cache.use_second_level_cache}") boolean useSecondLevelCache,
                                                                             @Value("${salat.cache.max-entries}") String maxCacheEntries,
                                                                             @Value("${salat.cache.expiry-tti}") String expiryTti) {
        return (properties) -> {
            if(!useSecondLevelCache) return;
            try {
                URI uri = resourceLoader.getResource("classpath:ehcache.xml").getURI();
                CachingProvider cachingProvider = Caching.getCachingProvider();
                System.setProperty("EHCACHE_EXPIRY_TTI", expiryTti);
                System.setProperty("EHCACHE_RESOURCES_HEAP_ENTRIES", maxCacheEntries);
                CacheManager cacheManager = cachingProvider.getCacheManager(uri, cachingProvider.getDefaultClassLoader());
                properties.put(ConfigSettings.CACHE_MANAGER, cacheManager);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
