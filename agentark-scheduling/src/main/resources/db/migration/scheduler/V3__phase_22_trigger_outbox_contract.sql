-- Phase 22 根据真实 Kubernetes 性能演练修正 Trigger 创建事实与 Scheduler Outbox 数据库约束的漂移。

ALTER TABLE scheduler_outbox
    DROP CHECK ck_scheduler_outbox_aggregate;

ALTER TABLE scheduler_outbox
    MODIFY aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '聚合类型：trigger=Trigger 定义，job=Durable Job，dead_letter=Dead Letter，audit=管理审计',
    ADD CONSTRAINT ck_scheduler_outbox_aggregate
        CHECK (aggregate_type IN ('trigger', 'job', 'dead_letter', 'audit'));
