package org.example.blogbackend.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/*验证 Spring 装配：yml 里的 host/db 有没有真正生效 */
@SpringBootTest
@EnabledIfSystemProperty(named = "it.redis", matches = "true")
class CacheStoreWiringTest {

    @Autowired CacheStore cacheStore;
    @Autowired StringRedisTemplate redis;

    @Test
    void 配置里的Redis真的连上了() {
        String key = "blog:v1:test:wiring";
        cacheStore.set(key, "ok", Duration.ofSeconds(30));

        // 用注入的 StringRedisTemplate 反查，证明是同一个连接工厂、同一个库
        assertThat(redis.opsForValue().get(key)).isEqualTo("ok");
        cacheStore.delete(key);
    }
}