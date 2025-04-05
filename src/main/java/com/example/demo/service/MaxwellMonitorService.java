package com.example.demo.service;

import com.zendesk.maxwell.MaxwellConfig;
import com.zendesk.maxwell.MaxwellContext;
import com.zendesk.maxwell.MaxwellMysqlStatus;
import com.zendesk.maxwell.bootstrap.BootstrapController;
import com.zendesk.maxwell.producer.AbstractProducer;
import com.zendesk.maxwell.producer.MaxwellOutputConfig;
import com.zendesk.maxwell.recovery.Recovery;
import com.zendesk.maxwell.recovery.RecoveryInfo;
import com.zendesk.maxwell.replication.BinlogConnectorReplicator;
import com.zendesk.maxwell.replication.Position;
import com.zendesk.maxwell.replication.Replicator;
import com.zendesk.maxwell.row.HeartbeatRowMap;
import com.zendesk.maxwell.schema.MysqlPositionStore;
import com.zendesk.maxwell.schema.MysqlSchemaStore;
import com.zendesk.maxwell.schema.SchemaStoreSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class MaxwellMonitorService implements Runnable {
  protected MaxwellConfig config;
  protected MaxwellContext context;
  protected CustomProducer producer;
  protected Replicator replicator;

  static final Logger LOGGER = LoggerFactory.getLogger(MaxwellMonitorService.class);

  public MaxwellMonitorService(MaxwellConfig config, MaxwellContext context, CustomProducer producer) {
    this.config = config;
    this.context = context;
    this.producer = producer;
  }

  public void run() {
    try {
      Thread.sleep(20 * 1000);
      start();
    } catch (Exception e) {
      LOGGER.error("maxwell encountered an exception", e);
    }
  }

  public void terminate() {
    LOGGER.info("starting shutdown");
    try {
      // send a final heartbeat through the system
      context.heartbeat();
      Thread.sleep(100);

      if (this.replicator != null) {
        // 使用 StoppableTask 接口中的 requestStop() 方法
        replicator.requestStop();
      }
    } catch (Exception e) {
      LOGGER.error("Failed to terminate gracefully", e);
    }

    if (this.context != null)
      context.terminate();

    replicator = null;
    context = null;
  }

  private Position attemptMasterRecovery() throws Exception {
    Position recovered = null;
    MysqlPositionStore positionStore = this.context.getPositionStore();
    RecoveryInfo recoveryInfo = positionStore.getRecoveryInfo(config);

    if (recoveryInfo != null) {
      Recovery masterRecovery = new Recovery(
              config.replicationMysql,
              config.databaseName,
              this.context.getReplicationConnectionPool(),
              this.context.getCaseSensitivity(),
              recoveryInfo
      );

      // 從HeartbeatRowMap獲取Position
      HeartbeatRowMap recoveredMap = masterRecovery.recover();

      if (recoveredMap != null) {
        // 從HeartbeatRowMap獲取下一個Position
        recovered = recoveredMap.getNextPosition();

        // 使用新的MysqlSchemaStore建構式
        MysqlSchemaStore oldServerSchemaStore = new MysqlSchemaStore(
                this.context.getMaxwellConnectionPool(),
                this.context.getReplicationConnectionPool(),
                this.context.getSchemaConnectionPool(),
                recoveryInfo.serverID,
                recoveryInfo.position,
                this.context.getCaseSensitivity(),
                this.config.filter,
                false
        );

        oldServerSchemaStore.clone(context.getServerID(), recovered);

        // 清理舊的復原資訊
        positionStore.cleanupOldRecoveryInfos();
      }
    }
    return recovered;
  }

  protected Position getInitialPosition() throws Exception {
    /* first method: do we have a stored position for this server? */
    Position initial = this.context.getInitialPosition();

    /* second method: are we recovering from a master swap? */
    if (initial == null && config.masterRecovery)
      initial = attemptMasterRecovery();

    /* third method: capture the current master position. */
    if (initial == null) {
      try (Connection c = context.getReplicationConnection()) {
        initial = Position.capture(c, config.gtidMode);
      }
    }
    return initial;
  }

  public String getMaxwellVersion() {
    String packageVersion = getClass().getPackage().getImplementationVersion();
    if (packageVersion == null)
      return "??";
    else
      return packageVersion;
  }

  static String bootString = "Maxwell v%s is booting (%s), starting at %s";
  private void logBanner(AbstractProducer producer, Position initialPosition) {
    String producerName = producer.getClass().getSimpleName();
    LOGGER.info(String.format(bootString, getMaxwellVersion(), producerName, initialPosition.toString()));
  }

  protected void onReplicatorStart() {}

  public void start() throws Exception {
    try (Connection connection = this.context.getReplicationConnection();
         Connection rawConnection = this.context.getRawMaxwellConnection()) {
      MaxwellMysqlStatus.ensureReplicationMysqlState(connection);
      MaxwellMysqlStatus.ensureMaxwellMysqlState(rawConnection);
      if (config.gtidMode) {
        MaxwellMysqlStatus.ensureGtidMysqlState(connection);
      }

      SchemaStoreSchema.ensureMaxwellSchema(rawConnection, this.config.databaseName);

      try (Connection schemaConnection = this.context.getMaxwellConnection()) {
        SchemaStoreSchema.upgradeSchemaStoreSchema(schemaConnection);
      }

    } catch (SQLException e) {
      LOGGER.error("SQLException: " + e.getLocalizedMessage());
      LOGGER.error(e.getLocalizedMessage());
      return;
    }

    Position initPosition = getInitialPosition();
    logBanner(producer, initPosition);
    this.context.setPosition(initPosition);

    // 創建 MysqlSchemaStore
    MysqlSchemaStore mysqlSchemaStore = new MysqlSchemaStore(this.context, initPosition);
    // 觸發加載 schema
    mysqlSchemaStore.getSchema();

    // 獲取 BootstrapController (可能為 null)
    BootstrapController bootstrapController = null;
    try {
      // 獲取當前 schema ID
      Long schemaID = mysqlSchemaStore.getSchemaID();
      bootstrapController = this.context.getBootstrapController(schemaID);
    } catch (Exception e) {
      LOGGER.warn("Unable to get bootstrap controller, continuing without bootstrap support", e);
    }

    try {
      // 創建一個適配器，將 CallMonitorProducer 轉換為 AbstractProducer
      AbstractProducer abstractProducer = createProducerAdapter(producer);

      // 獲取或設定MaxwellOutputConfig
      MaxwellOutputConfig outputConfig = this.context.getConfig().outputConfig;
      if (outputConfig == null) {
        outputConfig = new MaxwellOutputConfig();
      }

      // 使用正確的參數類型創建 BinlogConnectorReplicator
      this.replicator = new BinlogConnectorReplicator(
              mysqlSchemaStore,                       // SchemaStore
              abstractProducer,                       // AbstractProducer
              bootstrapController,                    // BootstrapController
              this.config.replicationMysql,           // MaxwellMysqlConfig
              this.context.getServerID(),             // replicaServerID
              this.config.databaseName,               // maxwellSchemaDatabaseName
              this.context.getMetrics(),              // Metrics
              initPosition,                           // Position (start position)
              false,                                  // stopOnEOF
              this.config.clientID,                   // clientID
              this.context.getHeartbeatNotifier(),    // HeartbeatNotifier
              null,                                   // Scripting (no script support)
              this.context.getFilter(),               // Filter
              false,                                  // ignoreMissingSchema
              outputConfig,                           // MaxwellOutputConfig
              0.75f,                                  // bufferMemoryUsage (使用預設值)
              5,                                      // replicationReconnectionRetries (使用預設值)
              BinlogConnectorReplicator.BINLOG_QUEUE_SIZE // binlogEventQueueSize (使用類別常數)
      );
    } catch (Exception e) {
      LOGGER.error("Failed to create BinlogConnectorReplicator", e);
      throw new RuntimeException("Failed to create BinlogConnectorReplicator: " + e.getMessage(), e);
    }

    // 將 replicator 設置到 context
    this.context.setReplicator(this.replicator);

    this.context.start();
    this.onReplicatorStart();

    // 啟動複製器
    replicator.runLoop();
  }

  /**
   * 創建一個 AbstractProducer 適配器，將 CallMonitorProducer 轉換為 AbstractProducer
   * 如果 CallMonitorProducer 已經是 AbstractProducer 的子類，則直接返回
   */
  private AbstractProducer createProducerAdapter(CustomProducer producer) {
    if (producer instanceof AbstractProducer) {
      return (AbstractProducer) producer;
    } else {
      // 如果 CallMonitorProducer 不是 AbstractProducer 的子類
      // 需要創建一個適配器類來包裝 CallMonitorProducer
      throw new RuntimeException("CallMonitorProducer is not an instance of AbstractProducer, an adapter is required");
    }
  }

  // 定義一個接口供實現了setFilter能力的Replicator實現
  public interface FilterableReplicator {
    void setFilter(com.zendesk.maxwell.filtering.Filter filter);
  }
}