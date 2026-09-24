package com.gwamcc.nginxlog.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 IP→系统 规则持久化到本地 JSON（与 db.json 同目录）。
 */
@Component
public class IpLabelStore {

    private static final Logger log = LoggerFactory.getLogger(IpLabelStore.class);
    private static final TypeReference<List<NginxLogProperties.IpLabelRule>> TYPE =
            new TypeReference<List<NginxLogProperties.IpLabelRule>>() {};

    private final Path file;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IpLabelStore() {
        this(Paths.get(System.getProperty("user.home"), ".nginx-log-analyzer", "ip-labels.json"));
    }

    /** 指定存储路径（生产用默认构造；测试可传入临时文件） */
    public IpLabelStore(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    public boolean exists() {
        return Files.isRegularFile(file);
    }

    public List<NginxLogProperties.IpLabelRule> load() {
        if (!exists()) {
            return null;
        }
        try {
            List<NginxLogProperties.IpLabelRule> rules = objectMapper.readValue(file.toFile(), TYPE);
            return rules == null ? new ArrayList<NginxLogProperties.IpLabelRule>() : rules;
        } catch (IOException ex) {
            log.warn("读取 IP 标签配置失败: {}", file, ex);
            return null;
        }
    }

    public void save(List<NginxLogProperties.IpLabelRule> rules) {
        try {
            Files.createDirectories(file.getParent());
            List<NginxLogProperties.IpLabelRule> payload =
                    rules == null ? new ArrayList<NginxLogProperties.IpLabelRule>() : rules;
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
            log.info("已保存 IP 标签配置到 {}（{} 条）", file, payload.size());
        } catch (IOException ex) {
            throw new IllegalStateException("保存 IP 标签配置失败: " + ex.getMessage(), ex);
        }
    }
}
