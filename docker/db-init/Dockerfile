# 元数据库初始化镜像
#
# 在官方 mysql:8.0 之上叠加 sql/data_source.sql。MySQL 官方镜像会在首次启动
# （空数据卷）时执行自身 /docker-entrypoint-initdb.d/ 下的 .sql 文件，
# 因此把建表脚本放进该目录即可自动初始化元数据库，无需仓库源码即可启动。
#
# 用法见 docker/docker-compose.images.yaml。
FROM mysql:8.0

COPY sql/data_source.sql /docker-entrypoint-initdb.d/10-data_source.sql

# 附带构建信息，便于排查当前初始化脚本来自哪个版本。
RUN echo "data-agent db-init: schema from sql/data_source.sql" > /docker-entrypoint-initdb.d/.build-info
