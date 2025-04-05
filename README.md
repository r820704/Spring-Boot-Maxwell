# Spring-Boot-Maxwell

Spring Boot 引入Maxwell依賴的測試

以下版本試驗後可行
```
Spring Boot 3.3 (Java 21)
MariaDB 5.5 (或MySQL 5.7、MySQL 8.0)
com.zendesk.maxwell 1.43.2
```

此測試驗證以下事項
- 用docker-compose目錄下可建立啟用binlog功能的各版本DB，並且用test.sql來新增測試資料
- App扮演MySQL Slave的角色將Master的binlog同步並轉為Json依序讀入
- 用Filter功能exclude以及include， binlog中特定的schema或table
- 用Maxwell的boostrap table，達到啟動時從特定時間點Replay的效果
