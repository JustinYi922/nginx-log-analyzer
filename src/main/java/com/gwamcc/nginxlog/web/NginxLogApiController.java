package com.gwamcc.nginxlog.web;

import com.gwamcc.nginxlog.model.DbConnectionSettings;
import com.gwamcc.nginxlog.service.DbConfigService;
import com.gwamcc.nginxlog.service.ImportProgress;
import com.gwamcc.nginxlog.service.NginxLogImportService;
import com.gwamcc.nginxlog.service.NginxLogStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NginxLogApiController {

    private final NginxLogImportService importService;
    private final NginxLogStatsService statsService;
    private final DbConfigService dbConfigService;

    public NginxLogApiController(NginxLogImportService importService,
                                 NginxLogStatsService statsService,
                                 DbConfigService dbConfigService) {
        this.importService = importService;
        this.statsService = statsService;
        this.dbConfigService = dbConfigService;
    }

    @PostMapping("/import")
    public Map<String, Object> startImport(@RequestBody(required = false) Map<String, Object> body) {
        String filePath = body == null ? null : str(body.get("filePath"));
        boolean truncate = body == null || body.get("truncate") == null || Boolean.TRUE.equals(body.get("truncate"));
        String path = importService.startImport(filePath, truncate);
        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("ok", true);
        resp.put("filePath", path);
        resp.put("message", "导入已启动，请轮询 /api/import/progress");
        return resp;
    }

    /**
     * 本地选择文件上传后分析。form-data: file + truncate(可选，默认 true)
     */
    @PostMapping("/import/upload")
    public Map<String, Object> uploadAndImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "truncate", defaultValue = "true") boolean truncate) throws Exception {
        String path = importService.startImportUpload(file, truncate);
        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("ok", true);
        resp.put("filePath", path);
        resp.put("originalFilename", file.getOriginalFilename());
        resp.put("size", file.getSize());
        resp.put("message", "上传成功，导入已启动，请轮询 /api/import/progress");
        return resp;
    }

    /** 一键清空库内分析数据 */
    @PostMapping("/clear")
    public Map<String, Object> clear() {
        return importService.clearAll();
    }

    @GetMapping("/db/config")
    public Map<String, Object> dbConfig() {
        return dbConfigService.current();
    }

    @PostMapping("/db/test")
    public Map<String, Object> dbTest(@RequestBody DbConnectionSettings body) {
        return dbConfigService.test(body);
    }

    @PostMapping("/db/apply")
    public Map<String, Object> dbApply(@RequestBody DbConnectionSettings body) {
        return dbConfigService.apply(body);
    }

    @GetMapping("/import/progress")
    public Map<String, Object> progress() {
        ImportProgress p = importService.getProgress();
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("running", p.isRunning());
        resp.put("message", p.getMessage());
        resp.put("error", p.getError());
        resp.put("filePath", p.getFilePath());
        resp.put("totalLines", p.getTotalLines());
        resp.put("parsed", p.getParsed());
        resp.put("inserted", p.getInserted());
        resp.put("skipped", p.getSkipped());
        resp.put("percent", p.getPercent());
        resp.put("startedAt", p.getStartedAt());
        resp.put("finishedAt", p.getFinishedAt());
        return resp;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) List<String> ips,
            @RequestParam(required = false) String ip) {
        return statsService.all(Math.max(5, Math.min(limit, 100)), startTime, endTime, mergeIps(ips, ip));
    }

    @GetMapping("/stats/overview")
    public Map<String, Object> overview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) List<String> ips,
            @RequestParam(required = false) String ip) {
        return statsService.overview(startTime, endTime, mergeIps(ips, ip));
    }

    private static List<String> mergeIps(List<String> ips, String ip) {
        if ((ips == null || ips.isEmpty()) && ip == null) {
            return Collections.emptyList();
        }
        if (ips == null || ips.isEmpty()) {
            return Collections.singletonList(ip);
        }
        if (ip == null || ip.trim().isEmpty()) {
            return ips;
        }
        List<String> merged = new java.util.ArrayList<String>(ips);
        merged.add(ip);
        return merged;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
