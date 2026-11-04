package org.example.blogbackend.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
/*可观测性*/
@Component     //注册Bean
public class CacheMetrics {

    private final Counter hit;             //缓存命中
    private final Counter miss;            //缓存未命中
    private final Counter error;           //出错
    private final Counter disabled;        //缓存总开关被人为关闭
    private final Counter bypassed;        //因为熔断跳过了 Redis

    /** 1表示正在熔断，0表示正常，供 Gauge-对象当前值指标暴露 */
    private final AtomicInteger bypassFlag = new AtomicInteger(0);   //AtomicInteger(0或1)

    public CacheMetrics(MeterRegistry registry) {        //指标注册中心，负责收集和暴露
        this.hit = counter(registry, "hit");
        this.miss = counter(registry, "miss");
        this.error = counter(registry, "error");
        this.disabled = counter(registry, "disabled");
        this.bypassed = counter(registry, "bypassed");

        /** Gauge 是“瞬时值”指标，表示采集那一刻某个量的当前值。它可升、可降**/
        Gauge.builder("blog.cache.bypass", bypassFlag, AtomicInteger::get)
                .description("1 = 熔断中（跳过 Redis）")
                .register(registry);   // 返回Gauge
    }

    /**抓取累计次数，只增不减，你主动 increment()**/
    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("blog.cache.requests")
                .tag("result", result)
                .description("缓存访问次数")
                .register(registry);         // 返回Counter
    }

    public void hit()           { hit.increment(); }
    public void miss()          { miss.increment(); }
    public void error()         { error.increment(); }
    public void disabled()      { disabled.increment(); }
    public void bypassed()      { bypassed.increment(); }

    public void bypassStarted() { bypassFlag.set(1); }
    public void bypassEnded()   { bypassFlag.set(0); }
}