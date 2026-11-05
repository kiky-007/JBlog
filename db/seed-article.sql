SET SESSION cte_max_recursion_depth = 200000;

INSERT INTO article (title, content, summary, status, category_id, author_id, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10000      -- 先跑 1 万，验证通过再改 100000
)
SELECT
    CONCAT('压测文章 ', n),
    REPEAT('这是用来测试缓存与索引性能的正文内容。', 150),        -- 约 5KB
    CONCAT('摘要 ', n),
    IF(n % 5 = 0, 0, 1),                                        -- 20% 草稿 / 80% 发布

    -- 分类：在真实 id {1,3,4} 里轮流取
    CASE n % 3
        WHEN 0 THEN 1
        WHEN 1 THEN 3
        ELSE 4
    END,

    -- 作者：在真实 id {3,4,5} 里轮流取
    3 + (n % 3),                                                -- 3,4,5 正好连续，可以直接算

    NOW() - INTERVAL n MINUTE,
    NOW() - INTERVAL n MINUTE
FROM seq;
INSERT IGNORE INTO article_tag (article_id, tag_id)
SELECT a.id,
       4 + (a.id % 6)                 -- tag id 4~9
FROM article a
WHERE a.title LIKE '压测文章 %'
LIMIT 30000;


-- 查数据
SELECT table_name,
       ROUND(data_length / 1024 / 1024, 1) AS data_mb,
       ROUND(index_length / 1024 / 1024, 1) AS index_mb,
       table_rows
FROM information_schema.tables
WHERE table_schema = 'blog' AND table_name IN ('article', 'article_tag');


-- 缓存池分配大小
SELECT @@innodb_buffer_pool_size / 1024 / 1024 AS pool_mb;


EXPLAIN ANALYZE
SELECT * FROM article WHERE status=1 ORDER BY created_at DESC LIMIT 10 OFFSET 0;

EXPLAIN ANALYZE
SELECT * FROM article WHERE status=1 ORDER BY created_at DESC LIMIT 10 OFFSET 7990;

EXPLAIN ANALYZE
SELECT id,title,summary,status,category_id,author_id,created_at,updated_at
FROM article WHERE status=1 ORDER BY created_at DESC LIMIT 10 OFFSET 7990;

EXPLAIN ANALYZE
SELECT * FROM article WHERE category_id=3 AND status=1 ORDER BY created_at DESC LIMIT 10;