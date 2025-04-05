FROM mysql:5.7

# 設定環境變數
ENV MYSQL_ALLOW_EMPTY_PASSWORD=no \
    MYSQL_ROOT_PASSWORD=root \
    MYSQL_DATABASE=mydb \
    MYSQL_USER=user \
    MYSQL_PASSWORD=password

# 複製自訂的MySQL配置檔案以啟用binlog
COPY my.cnf /etc/mysql/conf.d/

# 設定並初始化資料庫的資料卷
VOLUME /var/lib/mysql

# 暴露MySQL預設連接埠
EXPOSE 3306

# 健康檢查
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} || exit 1