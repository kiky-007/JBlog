package org.example.blogbackend.cache;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
/*
* 统一键名规范
* */
public final class CacheKeys {                 //final:不能被继承，防止别人改行为

    /** 缓存穿透防护用的哨兵值。数据库查不到时也缓存这个值，避免每次都打数据库。 */
    public static final String NULL_SENTINEL = "__NULL__";

    private static final String P = "blog:v1:";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // 20261026

    private CacheKeys() {}                 //私有构造器，禁止 new,这个类只能当工具类用，不会被实例化。

    public static String articleDetail(long id) {          //文章详情缓存
        return P + "article:detail:" + id;
    }

    public static String rebuildLock(long id) {           //重建缓存的分布式锁，防缓存击穿
        return P + "lock:article:rebuild:" + id;
    }

    public static String viewDelta() {                   //浏览量增量（先攒在 Redis，再批量落库）
        return P + "article:views:delta";
    }

    public static String viewDedupe(LocalDate day, long articleId) {                  //同一天同一篇文章的浏览去重
        return P + "article:views:dedupe:" + DAY.format(day) + ":" + articleId;
    }

    public static String hotRank(LocalDate day) {                                   //每日热度排行榜（通常对应 Redis ZSet）
        return P + "rank:hot:" + DAY.format(day);
    }

    public static String mergedRank(int days, int limit) {                        //多日榜单合并结果、热点列表缓存
        return P + "rank:hot:merged:" + days + ":" + limit;
    }

    public static String hotList(int days, int limit) {                          //多日榜单合并结果、热点列表缓存
        return P + "hot:list:" + days + ":" + limit;
    }
}