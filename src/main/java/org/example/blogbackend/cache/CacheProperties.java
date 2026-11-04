package org.example.blogbackend.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/*所有缓存策略可配置*/
@ConfigurationProperties(prefix = "blog.cache")
public record CacheProperties(
        @DefaultValue("true") boolean enabled,                     //缓存总开关，出问题可以一键关掉。关掉为false
        @DefaultValue("5") int failureThreshold,                   //连续失败几次触发熔断、
        @DefaultValue("30s") Duration bypassDuration,              //熔断多久。
        @DefaultValue("30m") Duration detailTtl,                  //详情缓存 30 分钟，
        @DefaultValue("300s") Duration detailJitter,              //再加最多 300 秒随机抖动，防止缓存雪崩。
        @DefaultValue("2m") Duration nullTtl,                    //
        @DefaultValue("3s") Duration lockTtl,                    //重建锁 3 秒。
        @DefaultValue("65536") int maxPayloadBytes,              //限制缓存值大小，防止大对象撑爆 Redis。
        @DefaultValue("25h") Duration viewDedupeTtl,             //浏览去重过期时间
        @DefaultValue("8d") Duration hotRankTtl,                 //榜单过期时间
        @DefaultValue("60s") Duration hotListTtl                //热点过期时间
) {}