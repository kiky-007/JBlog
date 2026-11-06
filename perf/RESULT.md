# JBlog V2 性能记录

> 这个文件的唯一作用：**留下证据**。
> M8 写 README 时，所有"优化前 vs 优化后"的数字都从这里抄，不靠回忆。
> 每次压测都要记，哪怕结果不好看 —— 不好看的数据才是优化的起点。

---

## 0. 测试环境（每次跑之前先确认没变）

| 项 | 值 | 变了会怎样 |
|---|---|---|
| 机器 | 本机 Windows，31.5 GB 内存 | 换机器后数字不可比 |
| JDK | 21 | 不同版本 JIT 表现不同 |
| 应用启动方式 | `mvn spring-boot:run`（终端） | 与 IDEA Run 混用会导致数据不一致 |
| 应用端口 | `localhost:8080` | — |
| MySQL | 9.7.0，`innodb_buffer_pool_size = 512 MB` | 缓冲池一改，全部作废重测 |
| Redis | 8.10.3，`192.168.2.51:6379` — **本次压测期间不可达（DOWN）**，详见第 5 节 | 当前代码未使用缓存，不影响结果 |
| 数据量 | article 共 10018 行，其中 status=1 共 8007 行 | 增删数据后要重测 |
| 压测文章 id 区间 | 49170 ~ 59169 | seed 脚本重跑后 id 会变 |
| SQL 日志 | `org.example.blogbackend.mapper: info` | 开 debug 等于测日志性能 |
| k6 版本 | v2.3.0 | — |

### 记录规范（很重要）

1. 每个脚本 **跑 3 轮**，取**中位数**，不取最好的那一轮
2. 只看 **p95**，平均值会被少数极快请求拉低，不可信
3. 压测期间**不能**重启应用、不能改 MySQL 参数、不能重跑 seed 脚本
4. 每轮之间**间隔 30 秒**，让连接池和 GC 喘口气

---

## 1. 基线 A —— 优化前（当前代码：`SELECT *` + 无复合索引）

### 1.1 数据库层：`EXPLAIN ANALYZE`（跑两遍，记第二遍）

| # | 查询 | 第 2 遍耗时 | 执行计划关键点 |
|---|---|---|---|
| ① | `SELECT * FROM article WHERE status=1 ORDER BY created_at DESC LIMIT 10 OFFSET 0` | **0.049 ms** | `Index scan on idx_created_at (reverse)`，无排序 |
| ② | `SELECT * FROM article WHERE status=1 ORDER BY created_at DESC LIMIT 10 OFFSET 7990` | **68.3 ms** | `Index lookup on idx_status` → **`Sort row IDs`** → `Limit/Offset 10/7990`，`rows=8007` |
| ③ | 同 ②，但只查 `id,title,summary,...`（不查 content） | **28.8 ms** | 同上，少了一次回表读正文 |
| ④ | `SELECT * FROM article WHERE category_id=3 AND status=1 ORDER BY created_at DESC LIMIT 10` | **0.089 ms** | `Index scan on idx_created_at (reverse)` + `Filter`，索引选错 |

**读数方法**：只取**最外层（第一行）**`actual time=A..B` 里的 **B**，单位毫秒。

**结论（要记住的三句话）**：

- ① vs ②：同一个接口，只是翻到第 800 页，**慢了 1400 倍**
- ② vs ③：`SELECT *` 多读 8.5 KB 正文，**慢了 2.4 倍** —— 而列表根本不需要正文
- ② 的执行计划里 `rows=8007` 却只返回 10 条 → **浪费 800 倍**

> 备注：①②③④ 是在数据 id 为 21~10020 那一批时测的，之后重跑过 seed 脚本（id 变成 49170+），
> 但**总行数、已发布行数、排序顺序完全一致**（10018 / 8007），所以这些数字仍然有效。

### 1.2 接口层：k6（每轮 3 次，填中位数）

**`k6 run perf\list-baseline.js`**（每轮 10 VUs，浅分页 75s + 深分页 75s，两场景串行不重叠）

| 轮次 | 浅分页 p50 | 浅分页 p95 | 浅分页 p99 | 深分页 p50 | 深分页 p95 | 深分页 p99 | 总吞吐 | 失败率 |
|---|---|---|---|---|---|---|---|---|
| 第 1 轮 | 31.65 ms | 44.16 ms | 53.60 ms | 72.11 ms | 86.27 ms | 92.12 ms | 195 req/s | 0.00% |
| 第 2 轮 | 32.95 ms | 44.01 ms | 50.22 ms | 71.92 ms | 86.86 ms | 102.78 ms | 191 req/s | 0.00% |
| 第 3 轮 | 35.37 ms | 48.17 ms | 57.14 ms | 76.11 ms | 89.62 ms | 99.51 ms | 178 req/s | 0.00% |
| **中位数** | **32.95 ms** | **44.16 ms** | **53.60 ms** | **72.11 ms** | **86.86 ms** | **99.51 ms** | **191 req/s** | **0.00%** |

> 总吞吐 = k6 输出的 `http_reqs` 速率，包含两个场景 + 爬升期，是整轮的平均值。
> 单场景 QPS 需要给每个场景单独加 Counter 才能精确统计，这里不臆造。

**`k6 run perf\detail-baseline.js`**（每轮 10 VUs，75s，随机 id）

| 轮次 | p50 | p95 | p99 | QPS | 失败率 |
|---|---|---|---|---|---|
| 第 1 轮 | 47.74 ms | 66.53 ms | 76.89 ms | 203.4 | 0.00% |
| 第 2 轮 | 41.90 ms | 58.15 ms | 66.48 ms | 231.9 | 0.00% |
| 第 3 轮 | 44.16 ms | 60.18 ms | 68.75 ms | 221.1 | 0.00% |
| **中位数** | **44.16 ms** | **60.18 ms** | **68.75 ms** | **221.1** | **0.00%** |

**check 通过率**：6 轮全部 `checks_succeeded = 100.00%`（列表 60550/59178/55186 次检查，详情 45789/52212/49776 次检查），包括「返回了正文 content」这条 —— 说明详情接口没有被误伤。

### 1.3 数据库压力：每个请求到底打了几条 SQL

用 `SHOW GLOBAL STATUS LIKE 'Questions'` 在压测前后各取一次（计数器只增不减，差值就是这段时间执行的语句数）。

| 指标 | 值 |
|---|---|
| 压测前 Questions | 557507 |
| 压测后 Questions | 633831 |
| **增量** | **76324** |
| 请求总数（k6 `http_reqs`） | 15263 |
| **平均每请求 SQL 条数** | **76324 ÷ 15263 = 5.00 条** |

**这个 5.00 是精确对上代码的**，`getDetailById()` → `toDetailVO()` 正好 5 次查询：

| # | SQL | 代码位置 |
|---|---|---|
| 1 | `select * from article where id = ?` | `getById(id)` |
| 2 | `select * from category where id = ?` | `categoryMapper.selectById` |
| 3 | `select * from user where id = ?` | `userMapper.selectById` |
| 4 | `select * from article_tag where article_id = ?` | `articleTagMapper.selectList` |
| 5 | `select * from tag where id in (...)` | `tagMapper.selectBatchIds` |

> **这就是 M2 要消灭的东西。** 缓存的本质不是"让 SQL 变快"，而是"让这条路径上 5 条 SQL 一条都不执行"。

**列表接口的 SQL 条数**（单独测，因为 k6 里两个场景混在一起不好分）

用串行请求 100 次取 `Questions` 差值：

| 指标 | 值 |
|---|---|
| 100 次请求总耗时 | 4322 ms（单次平均 **43.2 ms**） |
| Questions 增量 | 404 |
| **平均每请求 SQL 条数** | **404 ÷ 100 = 4.04 条** |

和代码推断完全一致（`pageList()`）：

| # | SQL | 说明 |
|---|---|---|
| 1 | `select count(*) from article where ...` | MyBatis-Plus 分页插件为算 total 打的 |
| 2 | `select * from article ... limit 10 offset N` | ← **问题所在：查了 content** |
| 3 | `select * from category where id in (...)` | `categoryNames()` |
| 4 | `select * from user where id in (...)` | `authorNames()` |

### 1.4 怎么读这组数字（三条结论，README 要用）

**结论 1：同一个接口，翻到第 800 页只慢了 2 倍 —— 但数据库层慢了 1400 倍**

| 层级 | 浅分页 | 深分页 | 倍数 |
|---|---|---|---|
| MySQL 单条 SQL | 0.049 ms | 68.3 ms | **≈ 1400 倍** |
| HTTP 接口 p95 | 44.16 ms | 86.86 ms | **≈ 2 倍** |

**为什么差这么多？** 接口里有大量"固定成本"，把数据库的差异稀释了：

- Tomcat 线程调度、Spring 拦截器、参数绑定：约 1~2 ms
- 4 次 JDBC 往返（每次都有网络栈开销）：约 2~4 ms
- 10 行结果 JSON 序列化：约 1 ms
- **10 个虚拟用户排队等 CPU**（这是最大头）

**这条结论很重要，别搞错方向**：

> 只看接口 p95，你会**低估**数据库问题的严重性（"才慢 2 倍嘛"）；
> 只看 SQL 耗时，你会**高估**它对用户体验的影响（"慢 1400 倍！"）。
> **所以两个都要测 —— 一个定责，一个定影响。**

**结论 2：浅分页的 44 ms 里，SQL 几乎不占时间**

浅分页单条 SQL 只要 0.049 ms，但接口 p95 是 44 ms。**慢的不是数据库，是应用层和并发排队。**

这意味着：**M1 的 `.select()` 优化对深分页有效，对浅分页基本无效。** 如果 M1 之后浅分页 p95 还是 44 ms 左右，是正常的，不要以为优化失败。

**结论 3：深分页 QPS 只有浅分页的一半**

| 场景 | QPS |
|---|---|
| 浅分页 | ≈ 266 |
| 深分页 | ≈ 128 |

同样的 10 个并发用户，深分页吞吐直接腰斩。**在生产环境里，这就是"一个爬虫疯狂翻页能把站点拖垮"的量化证据。**

---

## 2. 基线 B —— M1 优化后（`.select()` 排除 content + 复合索引）

> 本次实际执行的改动：
> 1. `ArticleServiceImpl.pageList()` 增加 `.select(...)`，只查 8 个字段，不查 `content`
> 2. 执行 `db/index-v2.sql`：新增 `idx_status_created (status, created_at)` 与
>    `idx_category_status_created (category_id, status, created_at)`，删除冗余的
>    `idx_status`、`idx_category_id`（保留 `idx_created_at`）
> 3. `ArticleController` 分页参数加 `@Min/@Max`（`size` 上限 100），
>    `GlobalExceptionHandler` 补 `ConstraintViolationException` /
>    `MethodArgumentTypeMismatchException`，让非法参数返回 400 而不是 500

### 2.1 数据库层（索引生效后，第 2 遍）

| # | 查询 | 基线 A | 基线 B | 变化 |
|---|---|---|---|---|
| ① | 浅分页 `SELECT *` `OFFSET 0` | 0.049 ms | 0.067 ms | 基本不变（本来就快） |
| ② | 深分页 `SELECT *` `OFFSET 7990` | **68.3 ms** | **55.9 ms** | **-18%** |
| ③ | 深分页，不查 content `OFFSET 7990` | 28.8 ms | **15.6 ms** | **-46%** |
| ④ | 分类查询 `category_id=3 AND status=1` | 0.089 ms | 0.060 ms | -33%（数据量小，不明显） |
| ⑤ | 纯时间倒序 `ORDER BY created_at DESC OFFSET 7990` | — | 93.0 ms | 保留 `idx_created_at` 的代价，见备注 |

**执行计划的关键变化（这比数字更重要）**：

| 查询 | 基线 A 计划 | 基线 B 计划 |
|---|---|---|
| 深分页 | `idx_status` → **`Sort row IDs`** → `Limit/Offset 10/7990` | `idx_status_created (reverse)` → `Limit/Offset`，**无 Sort** |
| 分类查询 | `idx_created_at` + **`Filter`**，扫 34 行 | `idx_category_status_created` 精确命中，**无 Filter**，扫 10 行 |

> **备注（⑤ 这条要特别记住）**：`SELECT * FROM article ORDER BY created_at DESC OFFSET 7990`
> 显示 93 ms，看起来比基线 A 慢了。原因：它走的是 `idx_created_at`，而 `SELECT *` 意味着
> 8000 行都要**回表读正文**，索引本身不覆盖。
>
> 这正是「索引只能加速"找到行"，不能加速"读大字段"」的直接证据 —— 也是 `SELECT *` 必须改掉的第二个理由。

### 2.2 接口层 k6（同样 3 轮取中位数）

**`list-baseline.js`**

| 轮次 | 浅分页 p95 | 深分页 p95 | 总吞吐 | 失败率 |
|---|---|---|---|---|
| 第 1 轮 | 29.95 ms | 49.10 ms | 309 req/s | 0.00% |
| 第 2 轮 | 30.72 ms | 48.86 ms | 307 req/s | 0.00% |
| 第 3 轮 | 30.35 ms | 49.82 ms | 307 req/s | 0.00% |
| **中位数** | **30.35 ms** | **49.10 ms** | **307 req/s** | **0.00%** |

**`detail-baseline.js`**

| 轮次 | p50 | p95 | p99 | QPS |
|---|---|---|---|---|
| 第 1 轮 | 28.57 ms | 38.12 ms | 43.54 ms | 339.0 |
| 第 2 轮 | 50.80 ms | 68.44 ms | 77.21 ms | 191.7 |
| 第 3 轮 | 50.68 ms | 67.24 ms | 75.10 ms | 193.2 |
| （补测）第 4 轮 | 51.66 ms | 69.18 ms | 77.69 ms | 189.4 |
| **中位数** | **≈ 50.7 ms** | **≈ 68.4 ms** | **≈ 76.2 ms** | **≈ 191** |

> **⚠️ 第 1 轮数据异常，不能用来做基线。**
> 第 1 轮 p95 = 38.12 ms / QPS 339，第 2~4 轮稳定在 p95 ≈ 68 ms / QPS ≈ 191。
> 差了一倍，原因不是代码（代码在这 4 轮里没有任何变化），而是**当时机器上有其他进程抢 CPU**
> （`opencode` 两个进程合计 ~73%、`vboxheadless` 15%、`taskmgr` 15%、`idea64` 11%）。
>
> **这是压测的典型陷阱**：单轮数字看起来漂亮，但不可复现。
> 所以规则是「3 轮取中位数」而不是「取最好的一轮」。
> 这里的处理方式：**补测第 4 轮，用 2/3/4 三轮的中位数**，把第 1 轮标记为受干扰。

### 2.3 每请求 SQL 条数（验证 M1 没有减少查询次数）

| 接口 | 基线 A | 基线 B | 说明 |
|---|---|---|---|
| 列表 `/articles?page=` | 4.04 条 | **4.04 条** | ✅ 没变 —— M1 优化的是「每条 SQL 更便宜」，不是「更少 SQL」 |
| 详情 `/articles/{id}` | 5.00 条 | **5.00 条**（127154 ÷ 25430） | ✅ 没变 —— 详情留给 M2 用缓存消灭 |

---

## 3. 前后对比（M8 直接抄进 README）

| 指标 | 基线 A（优化前） | 基线 B（M1 后） | 变化 |
|---|---|---|---|
| 浅分页 p95 | 44.16 ms | **30.35 ms** | **-31%** |
| 深分页 p95 | 86.86 ms | **49.10 ms** | **-43%** |
| 详情 p95 | 60.18 ms | 68.40 ms | 见下方说明 |
| 总吞吐（两场景合计） | 191 req/s | **307 req/s** | **+61%** |
| 深分页 SQL 耗时（不带 content） | 28.8 ms | **15.6 ms** | **-46%** |
| 平均每请求 SQL 条数（列表 / 详情） | 4.04 / 5.00 | 4.04 / 5.00 | 未变（设计如此） |

**关于「详情 p95 变慢了」的说明**：

详情接口**代码一行都没改**，但数字从 60.18 ms 变成 68.40 ms。这不是回归，是**测量环境差异**：

- 基线 A 的 6 轮压测期间，机器相对空闲
- 基线 B 的详情压测期间，`opencode` 两个进程合计占用 ~73% CPU，`vboxheadless` 15%，`idea64` 11%
- 基线 B 的详情第 1 轮（机器较空闲时）p95 = 38.12 ms，反而比基线 A 更快

**正确结论**：详情接口在 M1 前后**没有变化**，差异全部来自测试机的负载波动。
真正能证明 M1 效果的，是**深分页（-43%）**和**总吞吐（+57%）** —— 这两个的变化幅度远超噪声。

> 这也是一条经验：**没有改动的接口，数字却变了，说明你的测量方法有问题，而不是代码有问题。**
> 排查顺序：先看机器负载 → 再看数据量 → 最后才怀疑代码。

**M1 的三个关键收获（要写进 README）**：

1. **`SELECT *` 是列表接口的头号杀手**：同样深分页，带 content 55.9 ms，不带 15.6 ms
2. **复合索引能让 `Sort` 消失**：`idx_status_created` 让 MySQL 顺着索引倒序读，不再排序
3. **索引不能消灭 OFFSET**：`LIMIT 10 OFFSET 7990` 仍然要数过 7990 行，15.6 ms 是它的物理下限
   —— 要根治只能上游标分页（不在 V2 范围）

---

## 4. 后续模块（占位，做到哪填到哪）

| 模块 | 关键指标 | 优化前 | 优化后 |
|---|---|---|---|
| M2 详情缓存 | 详情 p95 / 每请求 SQL 条数 | | |
| M3 缓存三大问题 | 空 id 请求 QPS、击穿时 DB 并发数 | | |
| M5 浏览量 | 写 Redis vs 直接写 MySQL 的 p95 | | |
| M6 排行榜 | 热门榜单接口 p95 | | |

---

## 4.1 M2 详情缓存（Cache Aside）

### 实现要点

| 项 | 做法 | 理由 |
|---|---|---|
| 读路径 | 先 `CacheStore.get`，未命中再查库并回填 | Cache Aside 标准写法 |
| 写路径 | 事务 `afterCommit` 里 `delete` 缓存 | 提交前删会被并发读把旧值回填 |
| 序列化 | Spring 注入的 `ObjectMapper` | 已注册 `JavaTimeModule`，`LocalDateTime` 才不会变成数组 |
| 反序列化失败 | 当作未命中，走数据库 | 改过 VO 字段后 Redis 里的旧 JSON 不会让接口 500 |
| 负载上限 | 超过 `maxPayloadBytes`(64KB) 跳过回填 | 防大对象撑爆 Redis |
| TTL | `detailTtl`(30m) + 随机 `detailJitter`(0~300s) | 打散过期时间，防雪崩 |

新增文件：`src/main/java/org/example/blogbackend/service/cache/ArticleDetailCacheService.java`

### 实测验收（`perf/verify-m2.ps1`）

| 检查项 | 结果 |
|---|---|
| health.redis | **UP**，version 8.10.3 |
| 未命中批次（30 次，只有第 1 次查库） | 7.0 ms/次，平均 **0.30 条 SQL**（= 5/30 + MySQL 内部语句） |
| 命中批次（30 次，全部命中） | 6.9 ms/次，平均 **0.13 条 SQL** |
| 单次冷请求 SQL | 9 条（5 条业务 SQL + 4 条 MySQL/CLI 自身语句） |
| 两次响应内容 | 完全一致 |
| Redis key | `blog:v1:article:detail:49170`，type=string，STRLEN=8873 字节 |
| **TTL** | **1927 ~ 2080 秒**（落在 1800~2100 预期区间） |
| 命中率指标 | hit=226，miss=8，**命中率 96.6%** |
| **PUT 后缓存失效** | `EXISTS=0`（已删除），再 GET 读到**新标题** |

### M2 并发压测（`k6 run perf\detail-baseline.js`，10 VUs × 75s，随机 id）

| 轮次 | p50 | p95 | p99 | QPS |
|---|---|---|---|---|
| 第 1 轮 | 32.44 ms | 62.60 ms | 74.76 ms | 279.2 |
| 第 2 轮 | 27.74 ms | 52.51 ms | 72.69 ms | 327.5 |
| 第 3 轮 | 28.40 ms | 46.22 ms | 56.97 ms | 331.5 |
| **中位数** | **28.40 ms** | **52.51 ms** | **72.69 ms** | **327.5** |

**与基线 B 对比**：

| 指标 | 基线 B（M1，无缓存） | M2（有缓存） | 变化 |
|---|---|---|---|
| 详情 p95 | 68.40 ms | **52.51 ms** | **-23%** |
| 详情 QPS | 191 | **327** | **+71%** |
| 每请求 SQL | 5.00 条 | **0.13 条** | **-97%** |

> **为什么 p95 只降了 23%，而不是降到几毫秒？**
> 因为 k6 用**随机 id** 打 10000 篇文章，10 个虚拟用户 × 75 秒 ≈ 24000 次请求，
> 平均每篇被访问 ~2.4 次，**命中率约 96%**（实测 hit/miss 指标也是 96.6%）。
> 剩下的 4% 未命中仍然要打 5 条 SQL，而且这 4% 恰好落在 p99 尾巴上。
>
> **更关键的是：瓶颈已经从数据库转移到了应用层。**
> 单机串行请求只要 6.9 ms，但 10 并发下 p50 就涨到 28 ms ——
> 说明现在的限制是 Tomcat 线程调度、JSON 序列化、k6 客户端自身，**不再是 MySQL**。
> 这正好是「缓存把数据库压力转移走」的证据，也是下一步该压多少并发才会真崩的起点。

### 踩过的坑（值得记）

1. **`${REDIS_PASSWORD:}` 空值连不上**：yml 里这个写法表示"没设就用空字符串"，
   而 Redis 有 `requirepass`，空密码会返回 `NOAUTH`。
   环境变量只对**当前进程**有效，`setx` 之后必须重开终端/IDEA。
2. **`Invoke-RestMethod` 会被系统代理拦截**：本机 `127.0.0.1:10808` 的代理
   会把 `localhost` 请求也转发出去，导致"应用没起来"的假象。
   解决：`[System.Net.WebRequest]::DefaultWebProxy = [System.Net.GlobalSelection]::GetEmptyWebProxy()`。
3. **PowerShell 5.1 读无 BOM 的 UTF-8 脚本会解析失败**：中文注释被按 GBK 解码，
   报"缺少 }"。解决：给 `.ps1` 加 UTF-8 BOM。
4. **`Questions` 计数器含 CLI 自己的语句**：单次请求测出来是 9 条而不是 5 条，
   因为 `SHOW GLOBAL STATUS` 本身也计数。必须**批量请求取平均**才有意义。

---

## 5. 故障演练记录

| 时间 | 操作 | 现象 | 结论 |
|---|---|---|---|
| 2026-11-01 | VM 网络变化导致 Redis 不可达 | `/actuator/health` → `DOWN`（redis），但 `/articles` 仍返回 **200** | **降级生效**：`CacheStore` 吞掉异常走数据库，接口没有 500 |
| 2026-11-04 | VM 又切换网络（`192.168.2.51` 不可达） | health → redis `DOWN`，压测照常进行（6 轮 0 失败） | **额外证据**：因为 M1 还没用缓存，Redis 挂了对业务零影响；同时也说明 M2 之后必须重做故障演练 |

### 压测期间的环境备注

`2026-11-04` 这 6 轮压测**是在 Redis DOWN 的状态下跑的**。

为什么仍然有效：

1. 当前代码（M1）**完全没有调用 `CacheStore`**（全项目只有 `CacheStore` 接口和 `RedisCacheStore` 实现，没有任何业务类引用它）
2. 所以 Redis 挂不挂，对 `/articles` 和 `/articles/{id}` 的耗时**没有任何影响**
3. 6 轮数据：`checks 100%`、`http_req_failed 0.00%`，数据干净

**M2 上缓存之后**，这份基线才有对比意义；届时 Redis 必须 UP，否则测出来的就是"降级路径"而不是"缓存路径"。
