package com.gwamcc.nginxlog.model;

/**
 * 用户可配置的 MySQL 连接信息。
 */
public class DbConnectionSettings {

    private String host = "localhost";
    private int port = 3306;
    private String database = "nginx_log";
    private String username = "root";
    private String password = "";
    /** 额外 JDBC 参数（可选） */
    private String params =
            "useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci"
                    + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                    + "&rewriteBatchedStatements=true";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String toJdbcUrl() {
        String p = params == null ? "" : params.trim();
        if (p.startsWith("?")) {
            p = p.substring(1);
        }
        String base = "jdbc:mysql://" + host.trim() + ":" + port + "/" + database.trim();
        return p.isEmpty() ? base : (base + "?" + p);
    }

    public DbConnectionSettings copy() {
        DbConnectionSettings c = new DbConnectionSettings();
        c.host = this.host;
        c.port = this.port;
        c.database = this.database;
        c.username = this.username;
        c.password = this.password;
        c.params = this.params;
        return c;
    }

    /** 返回脱敏副本，用于接口回显 */
    public DbConnectionSettings masked() {
        DbConnectionSettings c = copy();
        if (c.password != null && !c.password.isEmpty()) {
            c.password = "******";
        }
        return c;
    }
}
