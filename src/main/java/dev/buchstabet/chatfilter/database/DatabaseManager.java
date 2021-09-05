package dev.buchstabet.chatfilter.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
  private final HikariDataSource hikariDataSource;

  public DatabaseManager(int poolSize, String host, int port, String database, String user, String password) {
    HikariConfig hikariConfig = new HikariConfig();

    hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&verifyServerCertificate=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Berlin");
    hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
    hikariConfig.setUsername(user);
    hikariConfig.setPassword(password);
    hikariConfig.setConnectionTimeout(3000);
    hikariConfig.setMaximumPoolSize(poolSize);
    hikariDataSource = new HikariDataSource(hikariConfig);
  }


  public Connection getConnection() {
    try {
      return hikariDataSource.isClosed() ? null : hikariDataSource.getConnection();
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public void closeConnect(Connection connection) {
    hikariDataSource.evictConnection(connection);
  }

  public void shutdownDatasource() {
    hikariDataSource.close();
  }

}
