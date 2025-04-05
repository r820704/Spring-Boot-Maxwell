FROM mariadb:5.5.64

# 設定環境變數 (確保與 MariaDB 5.5.64 相容)
ENV MYSQL_ROOT_PASSWORD=root \
    MYSQL_DATABASE=mydb \
    MYSQL_USER=user \
    MYSQL_PASSWORD=password

# 複製調整後的 MariaDB 配置檔案
COPY my.cnf /etc/mysql/conf.d/

# 設定並初始化資料庫的資料卷
VOLUME /var/lib/mysql

# 暴露 MariaDB 預設連接埠
EXPOSE 3306

# 調整健康檢查命令
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} || exit 1