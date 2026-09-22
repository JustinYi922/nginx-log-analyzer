package com.gwamcc.nginxlog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gwamcc.nginxlog.model.DbConnectionSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 将用户 MySQL 连接配置持久化到本地文件。
 */
@Component
public class DbSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(DbSettingsStore.class);

    private final Path file;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DbSettingsStore() {
        this.file = Paths.get(System.getProperty("user.home"), ".nginx-log-analyzer", "db.json");
    }

    public Path getFile() {
        return file;
    }

    public DbConnectionSettings load() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), DbConnectionSettings.class);
        } catch (IOException ex) {
            log.warn("读取数据库配置失败: {}", file, ex);
            return null;
        }
    }

    public void save(DbConnectionSettings settings) {
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), settings);
            log.info("已保存数据库配置到 {}", file);
        } catch (IOException ex) {
            throw new IllegalStateException("保存数据库配置失败: " + ex.getMessage(), ex);
        }
    }
}
