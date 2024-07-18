package org.tb.common.configuration;

import org.hibernate.cache.jcache.ConfigSettings;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class HibernateSecondLevelCacheConfiguration {

    @Bean
    public JCacheCacheManager cacheManager() {
        CachingProvider cachingProvider = Caching.getCachingProvider();
        CacheManager cacheManager = cachingProvider.getCacheManager();
        return new JCacheCacheManager(cacheManager);
    }

    @Bean
    public HibernatePropertiesCustomizer hibernateSecondLevelCacheCustomizer(JCacheCacheManager cacheManager) {
        return (properties) -> properties.put(ConfigSettings.CACHE_MANAGER, cacheManager.getCacheManager());
    }
}
