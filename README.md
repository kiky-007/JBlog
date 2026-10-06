# JBlog 后端（V1）

个人技术博客的后端 V1，基于 Spring Boot + MySQL 实现 **article / category / tag / user** 的 CRUD 闭环，
涵盖统一响应、全局异常、参数校验、分页、事务与数据库设计。

## 技术栈

| 组件 | 版本 / 说明 |
|---|---|
| JDK | 21 |
| Spring Boot | 3.5.15（web / validation / test） |
| MyBatis-Plus | 3.5.17（+ mybatis-plus-jsqlparser 分页） |
| MySQL | 9.x |
| Lombok / Jackson | 简化代码 / JSON |
| spring-security-crypto | BCrypt 密码哈希（仅加密库） |
| Maven | 3.9.x |

## 功能范围

- **Category / Tag**：增删改查；名称唯一；被引用时禁止删除
- **User**：创建 / 查询 / 删除；用户名唯一；密码 BCrypt 哈希后存储；响应绝不返回密码
- **Article**：增删改查 + 分页；多标签（多对多）；分类/作者逻辑外键校验；写入使用事务
- V1 **不含**：前端、登录鉴权、Redis、Docker、微服务等

## 目录结构

```
src/main/java/org/example/blogbackend/
├── BlogBackendApplication.java   启动类
├── common/
│   ├── result/                   Result / ResultCode / PageResult
│   └── exception/                BusinessException / GlobalExceptionHandler
├── config/                       MybatisPlusConfig（@MapperScan + 分页插件）/ PasswordConfig
├── controller/                   对外接口
├── service/ + service/impl/      业务逻辑
├── mapper/                       持久层（BaseMapper）
├── entity/                       与表一一映射
├── dto/                          入参对象
└── vo/                           出参对象
src/main/resources/
├── application.example.yml       配置模板（真实 application.yml 已被 .gitignore 忽略）
└── mapper/                       复杂查询的 XML（如需）
db/schema.sql                     建表脚本
```

## 数据库设计

| 表 | 关键字段 | 说明 |
|---|---|---|
| user | id, username(唯一), password, created_at, updated_at | 用户 |
| category | id, name(唯一), created_at, updated_at | 分类 |
| tag | id, name(唯一), created_at, updated_at | 标签 |
| article | id, title, content, summary, status(0草稿/1发布), category_id, author_id, created_at, updated_at | 文章；索引 `idx_category_id / idx_status / idx_created_at` |
| article_tag | article_id, tag_id，主键(article_id, tag_id)，索引 idx_tag_id | 文章-标签多对多中间表 |

- **关系**：分类/作者 与文章是 1:N（外键列在 `article`）；文章与标签是 M:N（中间表 `article_tag`）。
- **逻辑外键**：不建物理 FOREIGN KEY，由 Service 层校验引用是否存在，并通过删除策略控制占用。
- **时间字段**：由数据库 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` 维护，Java 侧不赋值。

## 接口一览

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| Category | POST | `/categories` | 创建 |
| | GET | `/categories` | 列表 |
| | GET | `/categories/{id}` | 详情 |
| | PUT | `/categories/{id}` | 更新 |
| | DELETE | `/categories/{id}` | 删除 |
| Tag | POST | `/tags` | 创建 |
| | GET | `/tags` | 列表 |
| | GET | `/tags/{id}` | 详情 |
| | PUT | `/tags/{id}` | 更新 |
| | DELETE | `/tags/{id}` | 删除 |
| User | POST | `/users` | 创建 |
| | GET | `/users/{id}` | 详情 |
| | DELETE | `/users/{id}` | 删除 |
| Article | POST | `/articles` | 创建（含 tagIds） |
| | GET | `/articles` | 分页列表 `?page=&size=&categoryId=&status=` |
| | GET | `/articles/{id}` | 详情（含分类名/作者名/标签） |
| | PUT | `/articles/{id}` | 更新（重设标签） |
| | DELETE | `/articles/{id}` | 删除（级联删 article_tag） |

## 统一响应与错误码

```json
{ "code": 200, "message": "操作成功", "data": { } }
```

| code | 含义 | 场景 |
|---|---|---|
| 200 | 成功 | |
| 400 | 参数错误 | 校验失败、请求体格式错误 |
| 404 | 资源不存在 | 查询/更新/删除不存在的资源，引用的外键不存在 |
| 409 | 数据冲突 | 重名、删除被引用的资源 |
| 500 | 系统异常 | 未捕获异常（内部细节只记日志，不返回前端） |

## 快速开始

1. 准备 JDK 21、Maven、MySQL。
2. 建库并建表：执行 `db/schema.sql`。
3. 复制配置模板：将 `src/main/resources/application.example.yml` 复制为 `src/main/resources/application.yml`，填入数据库账号密码。
4. 启动：运行 `BlogBackendApplication`，默认 `http://localhost:8080`。
5. 接口测试：使用 Apifox / Postman（见「接口一览」）。

## 设计要点

- **分层**：Controller → Service → Mapper → DB；Entity 与 DTO/VO 分离，接口不直接暴露 Entity。
- **统一响应 + 全局异常**：`@RestControllerAdvice` 统一处理业务 / 校验 / 请求体 / 404 / 兜底异常。
- **逻辑外键**：不依赖数据库外键，Service 层校验引用存在性，删除时校验是否被占用。
- **事务**：Article 的创建 / 更新 / 删除使用 `@Transactional`，保证 `article` 与 `article_tag` 的一致性。
- **分页**：MyBatis-Plus `PaginationInnerInterceptor`，统一返回 `PageResult`。
- **密码安全**：BCrypt 哈希存储；`UserVO` 不含 password 字段。

## 项目总结

**成果**：完成 V1 后端闭环——4 个模块 CRUD、统一响应、全局异常、参数校验、分页、事务、逻辑外键、密码哈希。

**主要知识点**：分层架构与包结构；Spring 注解与反射（IOC/DI、`@RestControllerAdvice`）；MyBatis-Plus（`BaseMapper`/`IService`/`ServiceImpl`、`LambdaQueryWrapper`、分页插件）；DTO/VO 分离；事务；逻辑外键；BCrypt；Git 工作流。

**踩坑记录**：实体字段驼峰与列名下划线不一致；`BaseMapper` 裸类型导致 SQL 未注入；`Result<void>` 非法；JSON 尾逗号；insert 只回填主键需重查；`@MapperScan` 包名要用点分隔等。
