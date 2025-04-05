package com.example.demo.service;

import com.djdch.log4j.StaticShutdownCallbackRegistry;
import com.zendesk.maxwell.MaxwellConfig;
import com.zendesk.maxwell.MaxwellContext;
import com.zendesk.maxwell.MaxwellMysqlConfig;
import com.zendesk.maxwell.filtering.Filter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Config.class)
public class MaxwellAutoMonitorConfig {

  @Bean
  public MaxwellContext maxwellContext(MaxwellConfig maxwellConfig) {
    try {
      // 在 1.43.2 版本，MaxwellContext 建構子會自動進行連線測試
      MaxwellContext context = new MaxwellContext(maxwellConfig);
      return context;
    } catch (Throwable e) {
      throw new RuntimeException("Unable to create MaxwellContext instance.", e);
    }
  }

  @Bean
  public MaxwellMonitorService maxwellMonitorService(MaxwellConfig config, MaxwellContext context, CustomProducer producer) {
    try {
      final MaxwellMonitorService maxwell = new MaxwellMonitorService(config, context, producer);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          maxwell.terminate();
          StaticShutdownCallbackRegistry.invoke();
        }
      });
      // maxwell.start();
      Thread thread = new Thread(maxwell);
      thread.start();
      return maxwell;
    } catch (Exception e) {
      throw new RuntimeException("Unable to create MaxwellMonitorService instance.", e);
    }
  }

  @Bean
  public MaxwellConfig maxwellConfig(Config config) {
    try {
      // 資料庫設定
      MaxwellMysqlConfig mysqlConfig = new MaxwellMysqlConfig();
      mysqlConfig.host = config.getHost();
      mysqlConfig.port = config.getPort();
      mysqlConfig.user = config.getUser();
      mysqlConfig.password = config.getPassword();
      mysqlConfig.database = config.getDatabase();
      mysqlConfig.setJDBCOptions("useSSL=false&characterEncoding=utf-8");

      // maxwell config
      MaxwellConfig maxwellConfig = new MaxwellConfig();
      maxwellConfig.maxwellMysql = mysqlConfig;
      maxwellConfig.replicationMysql = mysqlConfig;

      // 建立過濾條件字串
      StringBuilder filterBuilder = new StringBuilder();

      // 處理資料庫過濾條件
      if (config.getIncludeDatabases() != null && !config.getIncludeDatabases().isEmpty()) {
        // 格式: include: database.table, database2.table2
        filterBuilder.append("include:");

        String[] databases = config.getIncludeDatabases().split(",");
        String[] tables = config.getIncludeTables() != null ?
                config.getIncludeTables().split(",") :
                new String[]{"*"};

        boolean first = true;
        for (String database : databases) {
          for (String table : tables) {
            if (!first) {
              filterBuilder.append(",");
            }
            filterBuilder.append(database.trim()).append(".").append(table.trim());
            first = false;
          }
        }
      }

      String filterString = filterBuilder.toString();

      if (!filterString.isEmpty()) {
        try {
          // 使用正確的建構式建立 Filter
          maxwellConfig.filter = new Filter(filterString);
        } catch (Exception e) {
          throw new RuntimeException("Invalid filter configuration: " + e.getMessage(), e);
        }
      }

      // 設定資料庫名稱
      maxwellConfig.databaseName = config.getDatabase();

      // 驗證設定
      maxwellConfig.validate();

      return maxwellConfig;
    } catch (Throwable e) {
      throw new RuntimeException("Unable to create MaxwellConfig instance: " + e.getMessage(), e);
    }
  }
}