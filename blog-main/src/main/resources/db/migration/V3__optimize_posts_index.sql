-- 优化文章列表查询：列表页固定按 is_deleted=0 AND status=? 过滤并按 create_time 倒序分页
-- 等值条件列（is_deleted, status）在前、排序列（create_time）压轴：
-- status 过滤在索引层完成，避免每扫一行都回表判断 status 的回表放大
-- 不带 status 条件的查询仍走 idx_is_deleted_create_time，两个索引各服务一种查询形态，互不冗余
CREATE INDEX idx_is_deleted_status_create_time ON posts (is_deleted, status, create_time);
