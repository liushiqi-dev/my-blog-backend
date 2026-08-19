
create table categories
(
    id          int auto_increment comment '主键'
        primary key,
    name        varchar(20)                              not null comment '分类名称（2-20字符）',
    create_time datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    update_time datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '修改时间',
    constraint uk_name
        unique (name)
)
    comment '分类表';

create table post_categories
(
    post_id     int not null comment '文章ID',
    category_id int not null comment '分类ID',
    primary key (post_id, category_id)
)
    comment '文章-分类关联表';

create index idx_category_id
    on post_categories (category_id);

create table posts
(
    id          int auto_increment comment '主键'
        primary key,
    title       varchar(100)                             not null comment '标题100字符',
    content     text                                     not null comment '正文',
    summary     varchar(200)                             not null comment '正文前100字符',
    author_id   int                                      not null comment '作者ID',
    status      char(10)                                 not null comment '状态	PUBLISHED（发布）或 DRAFT（草稿）',
    is_deleted  tinyint(1)  default 0                    not null comment '0正常 1已删除',
    create_time datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    update_time datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '修改时间'
)
    comment '文章表';

create index idx_author_id
    on posts (author_id);

create index idx_is_deleted_create_time
    on posts (is_deleted, create_time);

create table users
(
    id          int auto_increment comment '主键'
        primary key,
    username    varchar(20)                              not null comment '用户名',
    password    varchar(255)                             not null comment 'BCrypt加密',
    role        char(10)    default 'USER'               not null comment '角色：ADMIN 或 USER',
    email       varchar(255)                             null comment '邮箱',
    create_time datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    update_time datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '修改时间',
    constraint uk_email
        unique (email),
    constraint uk_username
        unique (username)
)
    comment '用户表';

