-- 点赞关联表
CREATE TABLE post_likes
(
    post_id     INT NOT NULL COMMENT '文章ID',
    user_id     INT NOT NULL COMMENT '用户ID',
    create_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL COMMENT '点赞时间',
    PRIMARY KEY (post_id, user_id)
) COMMENT '点赞关联表';

-- 文章表新增点赞数
ALTER TABLE posts ADD COLUMN like_count INT DEFAULT 0 NOT NULL COMMENT '点赞数' AFTER is_deleted;

-- 用户表新增获赞数
ALTER TABLE users ADD COLUMN total_likes INT DEFAULT 0 NOT NULL COMMENT '获赞总数' AFTER email;

-- 文章表新增浏览量
ALTER TABLE posts ADD COLUMN view_count INT DEFAULT 0 NOT NULL COMMENT '浏览量' AFTER like_count;

-- 用户表新增获浏览量
ALTER TABLE users ADD COLUMN total_views INT DEFAULT 0 NOT NULL COMMENT '获浏览总量' AFTER total_likes;