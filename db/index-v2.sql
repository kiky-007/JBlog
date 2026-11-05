-- db/index-v2.sql —— M1：为 article 表补齐复合索引（只跑一次）
--   基线 A 的 EXPLAIN ANALYZE 暴露了两个问题：
--     ① 深分页：WHERE status=? ORDER BY created_at DESC
--        → MySQL 选择 idx_status 取 8007 行，再 Sort row IDs 排序
--        → 排序是额外成本，深分页 SQL 68.3 ms
--     ② 分类查询：WHERE category_id=? AND status=? ORDER BY created_at DESC
--        → MySQL 选择 idx_created_at 倒序扫，再用 Filter 一行行筛
--        → 数据量一大就退化成全表扫描
--
--
-- 为什么要删两个旧索引?
--   idx_status (status)                 完全被 idx_status_created 的最左前缀覆盖
--   idx_category_id (category_id)       完全被 idx_category_status_created 的最左前缀覆盖
--   留着它们只会：多占空间、拖慢 INSERT/UPDATE、让优化器多一个干扰选项。
--
--   idx_created_at 必须保留！
--      它是「不带 status 过滤、纯按时间倒序」查询的唯一可用索引。
--   不能重复执行。CREATE INDEX 不支持 IF NOT EXISTS，第二次跑会报
--   ERROR 1061 Duplicate key name。
--   需要重来时，先执行本文件底部的「回滚」段。

-- 加复合索引
CREATE INDEX idx_status_created
    ON article (status, created_at);

CREATE INDEX idx_category_status_created
    ON article (category_id, status, created_at);

-- 删掉被覆盖的冗余单列索引
--   放在建索引之后执行：万一建索引失败，旧索引还在，查询不会裸奔
DROP INDEX idx_status ON article;
DROP INDEX idx_category_id ON article;


-- 更新统计信息
--   不加这一步，EXPLAIN 里的 rows 是旧的估算值，可能误导优化器选错索引
ANALYZE TABLE article;

-- 验证
-- 1. 看现在有哪些索引（期望：PRIMARY / idx_created_at / idx_status_created
--     / idx_category_status_created / idx_tag_id 不在本表）
SHOW INDEX FROM article;

-- 2. 深分页：期望 plan 里 Sort 消失，type 走 idx_status_created
EXPLAIN ANALYZE
SELECT * FROM article WHERE status = 1 ORDER BY created_at DESC LIMIT 10 OFFSET 7990;

-- 3. 分类查询：期望走 idx_category_status_created，没有 Filter
EXPLAIN ANALYZE
SELECT * FROM article WHERE category_id = 3 AND status = 1 ORDER BY created_at DESC LIMIT 10;

-- 4. 纯时间倒序（验证 idx_created_at 还在、还能用）
EXPLAIN ANALYZE
SELECT * FROM article ORDER BY created_at DESC LIMIT 10;


-- 回滚脚本（只在需要撤销时手动执行，正常不要跑）
-- DROP INDEX idx_status_created ON article;
-- DROP INDEX idx_category_status_created ON article;
-- CREATE INDEX idx_status ON article (status);
-- CREATE INDEX idx_category_id ON article (category_id);
-- ANALYZE TABLE article;
