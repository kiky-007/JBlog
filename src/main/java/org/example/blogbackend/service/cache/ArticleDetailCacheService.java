package org.example.blogbackend.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.blogbackend.cache.CacheKeys;
import org.example.blogbackend.cache.CacheProperties;
import org.example.blogbackend.cache.CacheStore;
import org.example.blogbackend.vo.ArticleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 文章详情的缓存读写（Cache Aside 旁路缓存）
 *
 * 为什么单独一个类，而不是塞进 ArticleServiceImpl：
 *   1. ArticleServiceImpl 现在 189 行，已经承担了增删改查 + 关联组装
 *   2. M3 要往这里加「空值哨兵」和「互斥锁重建」，塞在 Service 里会不可读
 *   3. 缓存逻辑可以被单测单独覆盖，不依赖 MyBatis
 *
 * 设计要点：
 *   - 序列化用 Spring 注入的 ObjectMapper（已注册 JavaTimeModule，LocalDateTime 才能正常输出）
 *   - 反序列化失败 → 当作未命中，走数据库（改过 VO 字段后 Redis 里的旧 JSON 不会让接口 500）
 *   - 超过 maxPayloadBytes 的负载直接跳过（防止大对象撑爆 Redis）
 *   - TTL = detailTtl + 随机抖动，打散过期时间，防雪崩
 *   - 失效必须等事务提交之后（afterCommit），否则并发读会把旧值回填缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleDetailCacheService {

    private final CacheStore cacheStore;
    private final CacheProperties props;
    private final ObjectMapper objectMapper;   // Spring Boot 自动配置的那个，不能 new

    /**
     * 读缓存。未命中（或缓存不可用）返回 empty，调用方照常查数据库。
     */
    public Optional<ArticleVO> get(long id) {
        String key = CacheKeys.articleDetail(id);
        return cacheStore.get(key).flatMap(json -> deserialize(key, json));
    }

    /**
     * 查库之后回填缓存。
     * 注意：CacheStore.set 是静默失败的，这里不会抛异常，写不进去就算了（有 TTL 和数据库兜底）。
     */
    public void put(long id, ArticleVO vo) {
        String key = CacheKeys.articleDetail(id);
        String json;
        try {
            json = objectMapper.writeValueAsString(vo);    //把 ArticleVO 对象转成 JSON 字符串
        } catch (JsonProcessingException e) {
            log.warn("缓存序列化失败，跳过回填 key={} msg={}", key, e.getMessage());
            return;
        }

        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > props.maxPayloadBytes()) {
            log.warn("缓存值过大跳过回填 key={} bytes={} limit={}", key, bytes, props.maxPayloadBytes());
            return;
        }

        cacheStore.set(key, json, ttlWithJitter());
    }


    /**
     * 事务提交之后再删除。
     *
     * 为什么不能直接删：
     *   线程 A 更新文章（事务未提交）
     *   线程 B 读缓存 → 未命中 → 查库读到【旧值】
     *   线程 A 删缓存（此时提交还没发生）
     *   线程 A 事务提交（库变成新值）
     *   线程 B 把【旧值】写回缓存  ← 脏数据一直留到 TTL 过期
     *
     * 延迟到 afterCommit 删除，可以保证「提交前读到旧值并回填的线程」写进去的脏数据被清掉。
     * 极端并发下仍可能不一致，所以 TTL 是最后一道防线。
     */
    public void evictAfterCommit(long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {    //判断当前有无事务
            // 没有事务（或未开启同步）时退化为立即删除，保证功能正确性
            evict(id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {    //重写TransactionSynchronization方法，事务提交之后回调删除缓存的旧内容
                evict(id);
            }
        });
    }

    /**
     * 立即删除（无事务场景用，例如定时任务、手工重建）。
     */
    public void evict(long id) {
        cacheStore.delete(CacheKeys.articleDetail(id));
    }

    /**
     * 30 分钟 + 0~300 秒随机抖动。
     * 不加抖动的话，同一批写入的 key 会在同一秒集体过期 → 缓存雪崩。
     */
    private Duration ttlWithJitter() {      //Duration 时间量数据类型，表示时长
        long jitterSeconds = props.detailJitter().toSeconds();  //从配置获取抖动时间上限然后转成秒单位
        long extra = jitterSeconds <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterSeconds + 1); //ThreadLocalRandom线程独立,没有锁竞争
        return props.detailTtl().plusSeconds(extra);
    }

    private Optional<ArticleVO> deserialize(String key, String json) {
        // 空值哨兵
        if (CacheKeys.NULL_SENTINEL.equals(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ArticleVO.class));
        } catch (JsonProcessingException e) {
            // 改了 VO 字段后，Redis 里的旧 JSON 结构不匹配 —— 当作未命中，别让接口 500
            log.warn("缓存反序列化失败，按未命中处理 key={} msg={}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
