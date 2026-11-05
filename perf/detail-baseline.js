// perf/detail-baseline.js —— 文章详情接口压测（基线 A / B 共用）
//   模拟多个用户同时点开不同的文章详情页，测出 p50 / p95 / p99 和 QPS。

// 详情接口值得单独压
//   看 ArticleServiceImpl.getDetailById() → toDetailVO()，一次请求要打 5 条 SQL：
//     ① select * from article where id = ?              （还要读 8.5KB 正文）
//     ② select * from category where id = ?             （查分类名）
//     ③ select * from user where id = ?                 （查作者名）
//     ④ select * from article_tag where article_id = ?  （查标签 id 列表）
//     ⑤ select * from tag where id in (...)             （查标签详情）
//
//   这就是 M2 要上缓存的地方：目标是把这个接口的 5 条 SQL 降到 0 条。
//   所以现在必须先测出"没有缓存时有多慢"，否则 M2 做完就没法证明效果。
//
//   随机 id
//   如果所有人压同一个 id，MySQL 缓冲池里那一行永远是热的，测出来会偏乐观；
//   而且缓存上线后还会出现"100% 命中同一个 key"的假象。
//   随机 id 更接近真实用户行为（各看各的文章）。
// 【前提】
//   应用必须已经在 localhost:8080 跑起来，而且压测期间不能重启
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 压测文章的 id 区间（对应你库里 id 49170 ~ 59169 那 10000 篇）
// 写成 env 变量是为了以后数据变了不用改代码
const ID_MIN = parseInt(__ENV.ID_MIN || '49170', 10);
const ID_MAX = parseInt(__ENV.ID_MAX || '59169', 10);

// ---------------------------------------------------------------------------
// 自定义指标
// ---------------------------------------------------------------------------
const detailTrend = new Trend('detail_ms', true);  // 详情接口耗时
const bizError = new Rate('detail_biz_error');     // 业务失败率

// ---------------------------------------------------------------------------
// options：压测配置
// ---------------------------------------------------------------------------
export const options = {
    // constant-vus：并发用户数固定，比 ramping 更简单，适合"只看接口本身有多快"
    // 和列表脚本的 ramping-vus 形成对比：
    //   想看"系统能扛多少" → ramping（找拐点）
    //   想看"这一版比上一版快多少" → constant（固定条件对比）
    scenarios: {
        detail: {
            executor: 'constant-vus',
            exec: 'detail',
            vus: 10,
            duration: '75s',
            gracefulStop: '5s',
            tags: { scenario: 'detail' },
        },
    },

    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],

    thresholds: {
        'http_req_failed': ['rate<0.01'],
        'detail_biz_error': ['rate<0.01'],
        // 额外给详情接口定一个"期望值"，超过就标红提醒
        // 注意：这是提醒，不是失败，压测会继续
        'detail_ms': ['p(95)<500'],
    },
};


// 在 [ID_MIN, ID_MAX] 里随机取一个 id
function randomId() {
    return Math.floor(Math.random() * (ID_MAX - ID_MIN + 1)) + ID_MIN;
}

// 主逻辑：每个虚拟用户循环执行这个函数
export function detail() {
    const id = randomId();
    const res = http.get(`${BASE_URL}/articles/${id}`, {
        tags: { name: 'GET /articles/{id}' },
    });

    let code = null;
    try {
        code = res.json('code');
    } catch (e) {
        code = null;
    }

    // 注意这里比列表脚本多一条：正文不能为空
    // 因为 M1 之后我们会给列表查询加 .select() 排除 content，
    // 万一误伤到详情接口，这条断言会立刻抓到
    const ok = check(res, {
        'HTTP 状态码是 200': (r) => r.status === 200,
        '业务 code 是 200': () => code === 200,
        '返回了正文 content': (r) => {
            try {
                return r.json('data.content') !== null && r.json('data.content').length > 0;
            } catch (e) {
                return false;
            }
        },
    });

    bizError.add(!ok);
    detailTrend.add(res.timings.duration);
}
