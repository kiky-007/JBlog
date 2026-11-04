package org.example.blogbackend.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

/*
 * 缓存抽象接口
 */
public interface CacheStore {

    Optional<String> get(String key);        //读缓存。业务未命中返回 empty

    void set(String key, String value, Duration ttl);      //查库后回填缓存，写缓存并社过期。静默失败，就靠 TTL 兜底

    boolean setIfAbsent(String key, String value, Duration ttl);    //缓存重建锁、防击穿，原子“不存在才写”，抢锁。故障时返回 true，放行查库

    void delete(String key);           //删单个 key。静默失败，就靠 TTL 兜底

    void deleteAll(Collection<String> keys);   //批量删除。故障时，静默失败
}