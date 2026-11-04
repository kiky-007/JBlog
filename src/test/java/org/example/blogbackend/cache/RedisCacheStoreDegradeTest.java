package org.example.blogbackend.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
/*验证Redis 挂了不抛异常 + 会熔断 (不需要 Redis)*/
class RedisCacheStoreDegradeTest {

    private CacheProperties props() {
        return new CacheProperties(true, 5, Duration.ofSeconds(30),
                Duration.ofMinutes(30), Duration.ofSeconds(300),
                Duration.ofMinutes(2), Duration.ofSeconds(3),
                65536, Duration.ofHours(25), Duration.ofDays(8), Duration.ofSeconds(60));
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate brokenRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        return redis;
    }

    @Test
    void redis故障时get返回空而不是抛异常() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisCacheStore store = new RedisCacheStore(brokenRedis(), props(), new CacheMetrics(registry));

        Optional<String> result = store.get("blog:v1:article:detail:1");

        assertThat(result).isEmpty();
        assertThat(registry.get("blog.cache.requests").tag("result", "error")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 连续失败达到阈值后熔断_不再访问redis() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StringRedisTemplate redis = brokenRedis();
        RedisCacheStore store = new RedisCacheStore(redis, props(), new CacheMetrics(registry));

        for (int i = 0; i < 6; i++) {
            store.get("k" + i);
        }

        // 前 5 次真的打了 Redis，第 6 次已经熔断
        verify(redis, times(5)).opsForValue();
        assertThat(registry.get("blog.cache.bypass").gauge().value()).isEqualTo(1.0);
    }
}