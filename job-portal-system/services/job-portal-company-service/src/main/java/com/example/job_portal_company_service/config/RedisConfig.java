package com.example.job_portal_company_service.config;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.lang.reflect.Method;
import java.util.Collection;


@Slf4j
@Configuration
public class RedisConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator
                .builder()
                .allowIfBaseType(Object.class)
                .build();

        JsonMapper jsonMapper = JsonMapper.builder()
                .activateDefaultTypingAsProperty(
                    typeValidator,
                    DefaultTyping.NON_FINAL,
                    "@class"
                )
                .build();
    
        GenericJacksonJsonRedisSerializer jsonSerializer =
                new GenericJacksonJsonRedisSerializer(jsonMapper);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "companies", defaults.entryTtl(Duration.ofHours(24))
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults.entryTtl(Duration.ofHours(24)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * Generates a stable cache key where any Collection parameter is sorted first.
     */
    @Bean
    public KeyGenerator sortedListKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            StringBuilder key = new StringBuilder(method.getName()).append(':');
            for (Object param : params) {
                if (param instanceof Collection<?> col) {
                    key.append(col.stream()
                            .map(Object::toString)
                            .sorted()
                            .collect(Collectors.joining(",")));
                } else {
                    key.append(param);
                }
                key.append('|');
            }
            return key.toString();
        };
    }
}
