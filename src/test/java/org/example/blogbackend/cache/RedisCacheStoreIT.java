package org.example.blogbackend.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/*验证 TTL 真的生效（连 VM 的 db 15）
 * 需要真实 Redis。默认跳过，运行方式：
 * mvn test -Dtest=RedisCacheStoreIT -Dit.redis=true \
 *   -Dit.redis.host=192.168.56.101 -Dit.redis.password=你的密码
 */
@EnabledIfSystemProperty(named = "it.redis", matches = "true")
class RedisCacheStoreIT {

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private RedisCacheStore store;

    @BeforeEach
    void setUp() {
        String host = System.getProperty("it.redis.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("it.redis.port", "6379"));
        String password = System.getProperty("it.redis.password", "");

        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        cfg.setDatabase(15);                       // 只用 15 库，绝不碰 0 库
        if (!password.isBlank()) {
            cfg.setPassword(RedisPassword.of(password));
        }
        factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();

        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();

        CacheProperties props = new CacheProperties(true, 5, Duration.ofSeconds(30),
                Duration.ofMinutes(30), Duration.ofSeconds(300),
                Duration.ofMinutes(2), Duration.ofSeconds(3),
                65536, Duration.ofHours(25), Duration.ofDays(8), Duration.ofSeconds(60));
        store = new RedisCacheStore(redis, props, new CacheMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void tearDown() {
        factory.destroy();
    }

    @Test
    void 写入后能读到且TTL正确() {
        String key = CacheKeys.articleDetail(1L);
        store.set(key, "{\"id\":1}", Duration.ofSeconds(100));

        assertThat(store.get(key)).contains("{\"id\":1}");
        assertThat(redis.getExpire(key)).isBetween(1L, 100L);
    }

    @Test
    void 删除后读不到() {
        String key = CacheKeys.articleDetail(2L);
        store.set(key, "x", Duration.ofMinutes(1));
        store.delete(key);

        assertThat(store.get(key)).isEmpty();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void setIfAbsent第二次返回false() {
        String key = CacheKeys.rebuildLock(3L);
        assertThat(store.setIfAbsent(key, "token-a", Duration.ofSeconds(3))).isTrue();
        assertThat(store.setIfAbsent(key, "token-b", Duration.ofSeconds(3))).isFalse();
    }
}