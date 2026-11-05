// perf/list-baseline.js —— 列表接口压测（基线 A / B 共用同一个脚本）

// 这个脚本干什么
//   模拟多个用户同时刷文章列表，测出三个数：
//     1. p50 / p95 / p99 响应时间（毫秒）
//     2. 每秒能扛多少请求（QPS）
//     3. 失败率
//
// 测两个场景
//   浅分页 page=1    → 走 Index scan + LIMIT 10，正常情况应该很快
//   深分页 page=800  → MySQL 要扫 8000 行、排序、再丢掉 7990 行
//                      这是 M1 要优化的第一个真问题
//   两个数字放一起，才能证明"翻页深浅"对性能的影响有多大
//
// 【基线 A / 基线 B 是同一个脚本】
//   A = 改代码 + 加索引之前跑
//   B = 改完之后再跑同一份脚本
//   只有用同一个脚本、同样的参数，两个数字才有可比性
//
// 【前提】
//   应用必须已经在 localhost:8080 跑起来，而且压测期间不能重启

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// BASE_URL：应用地址。可以用 -e BASE_URL=... 覆盖，默认本机 8080
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';


// 自定义指标（k6 内置的 http_req_duration 会把所有请求混在一起，分不清是哪个场景）
//   第二个参数 true 表示"这是一个时间值（毫秒）"，k6 会按时间格式统计
const shallowTrend = new Trend('list_shallow_ms', true); // 浅分页耗时
const deepTrend = new Trend('list_deep_ms', true);       // 深分页耗时
const bizError = new Rate('list_biz_error');             // 业务失败率（HTTP 200 但 code != 200）

// options：k6 的"压测配置"
export const options = {
    scenarios: {
        // 场景 1：浅分页（第 1 页）
        // startTime 0s 开始，跑 75 秒
        shallow_page: {
            executor: 'ramping-vus', // 并发用户数可以渐变，比"一下子就 10 个"更接近真实流量
            exec: 'shallowPage',     // 这个场景执行下面哪个函数
            startVUs: 0,
            stages: [
                { duration: '10s', target: 10 }, // 10 秒内爬升到 10 个并发用户（预热）
                { duration: '60s', target: 10 }, // 保持 10 个并发用户 60 秒（正式测量）
                { duration: '5s', target: 0 },   // 5 秒内降到 0
            ],
            gracefulRampDown: '5s',
            tags: { scenario: 'shallow' }, // 给这个场景的所有请求打标签，方便过滤
        },

        // 场景 2：深分页（第 800 页）
        // startTime 80s 开始 —— 故意排在浅分页之后，避免两个场景同时抢 CPU / 连接池
        deep_page: {
            executor: 'ramping-vus',
            exec: 'deepPage',
            startVUs: 0,
            startTime: '80s',
            stages: [
                { duration: '10s', target: 10 },
                { duration: '60s', target: 10 },
                { duration: '5s', target: 0 },
            ],
            gracefulRampDown: '5s',
            tags: { scenario: 'deep' },
        },
    },

    // 汇总报告里要输出哪些统计值（默认只有 avg/min/med/max/p90/p95）
    // 这里显式列出 p99，写 README 时要用
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],

    // 阈值：只用来"判定这一轮是否合格"，不会中断压测
    // 失败率超过 1% 会标红，提醒你数据不可信
    thresholds: {
        'http_req_failed': ['rate<0.01'],
        'list_biz_error': ['rate<0.01'],
    },
};


// 请求 + 校验的公共逻辑
//   check() 不会中断压测，只是记录"这一次是否通过"
function requestAndCheck(path, metricName) {
    const res = http.get(`${BASE_URL}${path}`, {
        tags: { name: metricName }, // 让报告里显示成 GET /articles?page=1 这种可读名字
    });

    // 解析业务 code。注意：响应不是 JSON 时 res.json() 会抛异常，所以要 try/catch
    let code = null;
    try {
        code = res.json('code');
    } catch (e) {
        code = null;
    }

    const ok = check(res, {
        'HTTP 状态码是 200': (r) => r.status === 200,
        '业务 code 是 200': () => code === 200,
    });

    // 失败率 = 没通过的次数 / 总次数
    bizError.add(!ok);
    return res;
}

// 场景 1 的函数：浅分页 page=1&size=10
export function shallowPage() {
    const res = requestAndCheck('/articles?page=1&size=10', 'GET /articles page=1');
    shallowTrend.add(res.timings.duration);
    // 注意：这里没有 sleep()，就是"一个用户接一个用户地死命请求"，
    // 目的是测系统上限，不是模拟真人节奏
}

// 场景 2 的函数：深分页 page=800&size=10（等价于 SQL 里的 OFFSET 7990）
//   800 * 10 = 8000，恰好在你当前 8007 条已发布文章的有效范围内
export function deepPage() {
    const res = requestAndCheck('/articles?page=800&size=10', 'GET /articles page=800');
    deepTrend.add(res.timings.duration);
}
