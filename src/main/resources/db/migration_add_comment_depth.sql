-- 给 comment 表增加 depth 列，用于在插入时记录嵌套深度，避免查询时逐层递归追溯。
-- depth 0-indexed：根评论=0，最大允许值=4（共5层），超出时由业务层重定向父节点。
-- DEFAULT 0 确保存量数据迁移无需 UPDATE（存量评论均视为 depth=0，不影响查询正确性）。

ALTER TABLE comment
    ADD COLUMN depth INT NOT NULL DEFAULT 0 COMMENT '嵌套深度 0-indexed，最大4';
