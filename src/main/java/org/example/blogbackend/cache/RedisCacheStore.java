package org.example.blogbackend.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheStore implements CacheStore {

    private final StringRedisTemplate redis;    //Spring Boot自动配置Bean
    private final CacheProperties props;        //@ConfigurationPropertiesScan配置Bean
    private final CacheMetrics metrics;

    /** 连续失败次数，成功一次即清零 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /** 熔断截止时间戳（毫秒） */
    private volatile long bypassUntil = 0L;

    @Override
    public Optional<String> get(String key) {
        if (skipped()) {
            return Optional.empty();
        }
        try {
            String value = redis.opsForValue().get(key);
            onSuccess();
            if (value == null) {
                metrics.miss();
                return Optional.empty();
            }
            metrics.hit();
            return Optional.of(value);
        } catch (RuntimeException e) {
            onFailure(key, e);
            return Optional.empty();          // ★ 故障降级成“未命中”
        }
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        if (skipped()) {
            return;
        }
        try {
            redis.opsForValue().set(key, value, ttl);
            onSuccess();
        } catch (RuntimeException e) {
            onFailure(key, e);
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        if (skipped()) {
            return true;                      // ★ 故障时放行，让调用方去查库
        }
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, value, ttl);
            onSuccess();
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            onFailure(key, e);
            return true;
        }
    }

    @Override
    public void delete(String key) {
        if (skipped()) {
            return;
        }
        try {
            redis.delete(key);
            onSuccess();
        } catch (RuntimeException e) {
            onFailure(key, e);                // 删失败靠 TTL 兜底，不上抛
        }
    }

    @Override
    public void deleteAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty() || skipped()) {
            return;
        }
        try {
            redis.delete(keys);
            onSuccess();
        } catch (RuntimeException e) {
            onFailure(String.join(",", keys), e);
        }
    }

    /* ---------- 降级与熔断 ----------*/

    /** true = 本次调用应直接跳过 Redis ，总闸门*/
    private boolean skipped() {
        if (!props.enabled()) {      //判断是否人为关闭了redis
            metrics.disabled();
            return true;
        }
        if (System.currentTimeMillis() < bypassUntil) {    //判断是否在熔断期间
            metrics.bypassed();
            return true;
        }
        return false;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        metrics.bypassEnded();                // 恢复正常，熄灭熔断标记
    }

    private void onFailure(String key, RuntimeException e) {
        metrics.error();
        // 只打 WARN，不打堆栈：Redis 持续挂 30 秒会把日志刷爆
        log.warn("Redis 操作失败，已降级 key={} type={} msg={}",
                key, e.getClass().getSimpleName(), e.getMessage());
        if (consecutiveFailures.incrementAndGet() >= props.failureThreshold()) {    //如果 N >= 阈值，就触发熔断
            bypassUntil = System.currentTimeMillis() + props.bypassDuration().toMillis();    //设置熔断截止时间
            consecutiveFailures.set(0);    //熔断计数清0
            metrics.bypassStarted();       //设置熔断状态：1
            log.error("Redis 连续失败 {} 次，熔断 {}，期间全部降级",
                    props.failureThreshold(), props.bypassDuration());
        }
    }
}