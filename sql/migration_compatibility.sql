ALTER TABLE `table_info`
    ADD COLUMN `data_granularity` VARCHAR(500) DEFAULT NULL
        COMMENT '数据粒度（一行代表的业务实体或事件）'
        AFTER `table_description`;

ALTER TABLE `column_info`
    ADD COLUMN `semantic_type` VARCHAR(32) DEFAULT NULL
        COMMENT '语义类型：IDENTIFIER/DIMENSION/MEASURE/TIME/LOCATION'
        AFTER `column_description`;
