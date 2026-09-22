package com.gwamcc.nginxlog.config;

import com.gwamcc.nginxlog.model.DbConnectionSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可热切换目标库的 DataSource 包装。
 */
public class ReloadableDataSource implements DataSource {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ReloadableDataSource.class);

    private final AtomicReference<HikariDataSource> target = new AtomicReference<HikariDataSource>();
    private volatile DbConnectionSettings current;

    public synchronized void reload(DbConnectionSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("数据库配置不能为空");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.toJdbcUrl());
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword() == null ? "" : settings.getPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setPoolName("nginx-log-pool");
        config.setConnectionTimeout(10_000);

        HikariDataSource neu = new HikariDataSource(config);
        // 先验证
        try (Connection ignored = neu.getConnection()) {
            // ok
        } catch (SQLException ex) {
            neu.close();
            throw new IllegalStateException("连接 MySQL 失败: " + ex.getMessage(), ex);
        }

        HikariDataSource old = target.getAndSet(neu);
        current = settings.copy();
        log.info("数据源已切换 -> {}@{}:{}/{}",
                settings.getUsername(), settings.getHost(), settings.getPort(), settings.getDatabase());
        if (old != null) {
            try {
                old.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    public DbConnectionSettings currentSettings() {
        return current == null ? null : current.copy();
    }

    private HikariDataSource require() {
        HikariDataSource ds = target.get();
        if (ds == null || ds.isClosed()) {
            throw new IllegalStateException("数据源尚未初始化");
        }
        return ds;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return require().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return require().getConnection(username, password);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return require().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return require().isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return require().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        require().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        require().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return require().getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return require().getParentLogger();
    }
}
