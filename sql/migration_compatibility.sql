ALTER TABLE `table_info`
    ADD COLUMN `data_granularity` VARCHAR(500) DEFAULT NULL
        COMMENT '数据粒度（一行代表的业务实体或事件）'
        AFTER `table_description`;
