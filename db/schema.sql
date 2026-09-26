create table if not exists article
(
    id bigint auto_increment comment '主键'
    primary key,
    title varchar(200) not null comment '标题',
    content longtext not null comment '文章',
    summary varchar(500) null comment '摘要',
    status tinyint default 0 null comment '默认草稿为0，发布为1',
    category_id bigint not null comment '类别id',
    author_id bigint not null comment '作者id',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
    )
    comment '文章表' collate=utf8mb4_general_ci;

create index idx_category_id
    on article (category_id);

create index idx_created_at
    on article (created_at);

create index idx_status
    on article (status);

create table if not exists article_tag
(
    article_id bigint not null comment '文章id',
    tag_id bigint not null comment '标签id',
    primary key (article_id, tag_id)
    )
    comment '文章-标签关系表' collate=utf8mb4_general_ci;

create index idx_tag_id
    on article_tag (tag_id);

create table if not exists category
(
    id bigint auto_increment comment '主键'
    primary key,
    name varchar(50) not null comment '标签名',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_name
    unique (name) comment '索引'
    )
    comment '分类表' collate=utf8mb4_general_ci;

create table if not exists tag
(
    id bigint auto_increment comment '主键'
    primary key,
    name varchar(50) not null comment '标签名',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_name
    unique (name)
    )
    comment '标签表' collate=utf8mb4_general_ci;

create table if not exists user
(
    id bigint auto_increment comment '主键'
    primary key,
    username varchar(50) not null comment '用户名',
    password varchar(225) not null comment '密码',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_username
    unique (username) comment '索引'
    )
    comment '用户表' collate=utf8mb4_general_ci;

