package com.gwamcc.nginxlog.service;

import com.gwamcc.nginxlog.config.NginxLogProperties;
import com.gwamcc.nginxlog.model.NginxAccessLog;
import com.gwamcc.nginxlog.parser.NginxAccessLogParser;
import com.gwamcc.nginxlog.repo.NginxAccessLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NginxLogImportService {

    private static final Logger log = LoggerFactory.getLogger(NginxLogImportService.class);

    private final NginxAccessLogRepository repository;
    private final NginxLogProperties properties;
    private final TaskExecutor importExecutor;
    private final ImportProgress progress = new ImportProgress();

    public NginxLogImportService(NginxAccessLogRepository repository,
                                 NginxLogProperties properties,
                                 @Qualifier("importExecutor") TaskExecutor importExecutor) {
        this.repository = repository;
        this.properties = properties;
        this.importExecutor = importExecutor;
    }

    public ImportProgress getProgress() {
        return progress;
    }

    public synchronized String startImport(String filePath, boolean truncate) {
        String path = StringUtils.hasText(filePath) ? filePath.trim() : properties.getDefaultFile();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("未指定日志文件路径");
        }
        File file = new File(path);
        if (!file.isFile()) {
            throw new IllegalArgumentException("日志文件不存在: " + path);
        }
        return enqueueImport(path, truncate);
    }

    /**
     * 接收浏览器上传的日志文件，落盘后异步导入。
     */
    public synchronized String startImportUpload(MultipartFile upload, boolean truncate) throws Exception {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的日志文件");
        }
        String original = upload.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            original = "access.log";
        }
        Path dir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String safeName = original.replaceAll("[\\\\/]+", "_");
        Path target = dir.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + safeName);
        Files.copy(upload.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        log.info("上传日志已保存: {} ({} bytes)", target, Files.size(target));
        return enqueueImport(target.toString(), truncate);
    }

    /**
     * 一键清空库内分析数据（TRUNCATE）。
     */
    public synchronized Map<String, Object> clearAll() {
        if (progress.isRunning()) {
            throw new IllegalStateException("导入进行中，请稍后再清空");
        }
        long before = repository.countAll();
        repository.truncate();
        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("ok", true);
        resp.put("clearedBefore", before);
        resp.put("message", "已清空 nginx_access_log，原有 " + before + " 条");
        log.info("一键清空完成，删除前行数={}", before);
        return resp;
    }

    private String enqueueImport(String path, boolean truncate) {
        File file = new File(path);
        if (!progress.tryStart(path)) {
            throw new IllegalStateException("已有导入任务在执行中");
        }
        progress.setTotalLines(Math.max(1L, file.length() / 130L));
        importExecutor.execute(() -> doImport(path, truncate));
        return path;
    }

    private void doImport(String path, boolean truncate) {
        int batchSize = Math.max(100, properties.getBatchSize());
        long started = System.currentTimeMillis();
        try {
            if (truncate) {
                log.info("清空 nginx_access_log 后开始导入: {}", path);
                repository.truncate();
            }
            List<NginxAccessLog> batch = new ArrayList<NginxAccessLog>(batchSize);
            long lineNo = 0L;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8), 1024 * 1024)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    NginxAccessLog row = NginxAccessLogParser.parse(line);
                    if (row == null) {
                        progress.addSkipped(1);
                        continue;
                    }
                    batch.add(row);
                    progress.addParsed(1);
                    if (batch.size() >= batchSize) {
                        repository.batchInsert(batch);
                        progress.addInserted(batch.size());
                        batch.clear();
                        if (lineNo > progress.getTotalLines()) {
                            progress.setTotalLines(lineNo + batchSize * 10L);
                        }
                        if (progress.getInserted() % (batchSize * 25L) == 0) {
                            log.info("导入进度 inserted={} skipped={} percent={}%",
                                    progress.getInserted(), progress.getSkipped(),
                                    String.format("%.1f", progress.getPercent()));
                        }
                    }
                }
            }
            progress.setTotalLines(lineNo);
            if (!batch.isEmpty()) {
                repository.batchInsert(batch);
                progress.addInserted(batch.size());
                batch.clear();
            }
            long costSec = (System.currentTimeMillis() - started) / 1000L;
            String msg = "done inserted=" + progress.getInserted()
                    + " skipped=" + progress.getSkipped()
                    + " lines=" + lineNo
                    + " costSec=" + costSec;
            progress.finishOk(msg);
            log.info("导入完成 {}", msg);
        } catch (Exception ex) {
            log.error("导入失败", ex);
            progress.finishError(ex.getMessage());
        }
    }
}
