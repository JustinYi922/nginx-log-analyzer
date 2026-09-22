package com.gwamcc.nginxlog.service;

import com.gwamcc.nginxlog.config.DbSettingsStore;
import com.gwamcc.nginxlog.config.ReloadableDataSource;
import com.gwamcc.nginxlog.model.DbConnectionSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DbConfigService {

    private static final Logger log = LoggerFactory.getLogger(DbConfigService.class);

    private final ReloadableDataSource dataSource;
    private final DbSettingsStore store;
    private final NginxLogImportService importService;

    public DbConfigService(ReloadableDataSource dataSource,
                           DbSettingsStore store,
                           NginxLogImportService importService) {
        this.dataSource = dataSource;
        this.store = store;
        this.importService = importService;
    }

    public Map<String, Object> current() {
        DbConnectionSettings cur = dataSource.currentSettings();
        if (cur == null) {
            cur = new DbConnectionSettings();
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("settings", cur.masked());
        out.put("jdbcUrl", cur.toJdbcUrl());
        out.put("configFile", store.getFile().toString());
        out.put("hasSavedFile", store.load() != null);
        out.put("passwordSet", StringUtils.hasText(cur.getPassword()));
        return out;
    }

    public Map<String, Object> test(DbConnectionSettings input) {
        DbConnectionSettings settings = mergeWithCurrent(input);
        validate(settings);
        long started = System.currentTimeMillis();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(
                    settings.toJdbcUrl(), settings.getUsername(),
                    settings.getPassword() == null ? "" : settings.getPassword());
                 Statement st = conn.createStatement()) {
                st.execute("SELECT 1");
            }
            Map<String, Object> ok = new HashMap<String, Object>();
            ok.put("ok", true);
            ok.put("message", "连接成功");
            ok.put("costMs", System.currentTimeMillis() - started);
            ok.put("jdbcUrl", settings.toJdbcUrl());
            return ok;
        } catch (Exception ex) {
            Map<String, Object> fail = new HashMap<String, Object>();
            fail.put("ok", false);
            fail.put("message", "连接失败: " + ex.getMessage());
            fail.put("costMs", System.currentTimeMillis() - started);
            return fail;
        }
    }

    public Map<String, Object> apply(DbConnectionSettings input) {
        if (importService.getProgress().isRunning()) {
            throw new IllegalStateException("导入进行中，请稍后再切换数据库");
        }
        DbConnectionSettings settings = mergeWithCurrent(input);
        validate(settings);
        // 若库不存在，先尝试创建（需要权限）
        ensureDatabaseExists(settings);
        dataSource.reload(settings);
        ensureSchema();
        store.save(settings);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("ok", true);
        out.put("message", "已切换并保存 MySQL 配置");
        out.put("settings", settings.masked());
        out.put("jdbcUrl", settings.toJdbcUrl());
        out.put("configFile", store.getFile().toString());
        return out;
    }

    private void ensureSchema() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/schema.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
        log.info("已确保 nginx_access_log 表存在");
    }

    private void ensureDatabaseExists(DbConnectionSettings settings) {
        String db = settings.getDatabase();
        if (!StringUtils.hasText(db)) {
            return;
        }
        String serverUrl = "jdbc:mysql://" + settings.getHost() + ":" + settings.getPort()
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(
                    serverUrl, settings.getUsername(),
                    settings.getPassword() == null ? "" : settings.getPassword());
                 Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + db.replace("`", "") + "` "
                        + "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
        } catch (Exception ex) {
            // 无建库权限时忽略，后续直连目标库；若库不存在会在 reload 时报错
            log.warn("尝试创建数据库 {} 未成功（可忽略若库已存在）: {}", db, ex.getMessage());
        }
    }

    /**
     * 若前端回传 password=****** 或空，则沿用当前已生效密码。
     */
    private DbConnectionSettings mergeWithCurrent(DbConnectionSettings input) {
        if (input == null) {
            throw new IllegalArgumentException("请填写数据库连接信息");
        }
        DbConnectionSettings merged = input.copy();
        DbConnectionSettings cur = dataSource.currentSettings();
        String pwd = merged.getPassword();
        if (!StringUtils.hasText(pwd) || "******".equals(pwd) || "******".equals(pwd.trim())) {
            if (cur != null) {
                merged.setPassword(cur.getPassword());
            }
        }
        return merged;
    }

    private static void validate(DbConnectionSettings s) {
        if (!StringUtils.hasText(s.getHost())) {
            throw new IllegalArgumentException("host 不能为空");
        }
        if (s.getPort() <= 0 || s.getPort() > 65535) {
            throw new IllegalArgumentException("port 不合法");
        }
        if (!StringUtils.hasText(s.getDatabase())) {
            throw new IllegalArgumentException("database 不能为空");
        }
        if (!StringUtils.hasText(s.getUsername())) {
            throw new IllegalArgumentException("username 不能为空");
        }
    }
}
