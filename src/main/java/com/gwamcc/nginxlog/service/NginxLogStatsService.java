package com.gwamcc.nginxlog.service;

import com.gwamcc.nginxlog.repo.NginxAccessLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NginxLogStatsService {

    private final NginxAccessLogRepository repository;
    private final IpLabelService ipLabelService;

    public NginxLogStatsService(NginxAccessLogRepository repository, IpLabelService ipLabelService) {
        this.repository = repository;
        this.ipLabelService = ipLabelService;
    }

    public Map<String, Object> overview(Date start, Date end, List<String> ips) {
        List<String> normalized = normalizeIps(ips);
        Map<String, Object> raw = repository.overview(start, end, normalized);
        long total = toLong(raw.get("total"));
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("total", total);
        out.put("ok2xx", toLong(raw.get("ok_2xx")));
        out.put("client4xx", toLong(raw.get("client_4xx")));
        out.put("server5xx", toLong(raw.get("server_5xx")));
        out.put("totalBytes", toLong(raw.get("total_bytes")));
        out.put("avgUpstreamSec", raw.get("avg_upstream_ms"));
        out.put("minTime", raw.get("min_time"));
        out.put("maxTime", raw.get("max_time"));
        out.put("filterStart", start);
        out.put("filterEnd", end);
        out.put("filterIps", normalized);
        out.put("okRate", percent(toLong(raw.get("ok_2xx")), total));
        out.put("err4xxRate", percent(toLong(raw.get("client_4xx")), total));
        out.put("err5xxRate", percent(toLong(raw.get("server_5xx")), total));

        List<Map<String, Object>> byOwner = byIpOwner(start, end, normalized);
        long labeled = 0L;
        long other = 0L;
        for (Map<String, Object> row : byOwner) {
            String name = String.valueOf(row.get("name"));
            long cnt = toLong(row.get("cnt"));
            if (IpLabelService.OTHER_LABEL.equals(name)) {
                other = cnt;
            } else {
                labeled += cnt;
            }
        }
        out.put("labeledCount", labeled);
        out.put("otherCount", other);
        out.put("labeledRate", percent(labeled, total));
        // 兼容旧字段名
        out.put("platformCount", labeled);
        out.put("platformRate", percent(labeled, total));
        return out;
    }

    public Map<String, Object> all(int limit, Date start, Date end, List<String> ips) {
        if (start != null && end != null && start.after(end)) {
            throw new IllegalArgumentException("startTime 不能晚于 endTime");
        }
        List<String> normalized = normalizeIps(ips);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("overview", overview(start, end, normalized));
        out.put("byStatus", withPercent(repository.groupByStatus(start, end, normalized)));
        out.put("byMethod", withPercent(repository.groupByMethod(start, end, normalized)));
        out.put("byIpOwner", byIpOwner(start, end, normalized));
        out.put("topPaths", withPercent(repository.topPaths(limit, start, end, normalized)));
        out.put("topIps", withPercent(labelIps(repository.topIps(limit, start, end, normalized))));
        out.put("topUserAgents", withPercent(repository.topUserAgents(Math.min(limit, 20), start, end, normalized)));
        out.put("topUpstreams", withPercent(repository.topUpstreams(Math.min(limit, 20), start, end, normalized)));
        out.put("byDay", repository.byDay(start, end, normalized));
        out.put("byHour", repository.byHour(start, end, normalized));
        return out;
    }

    private List<Map<String, Object>> byIpOwner(Date start, Date end, List<String> ips) {
        // 拉全量 IP 计数再按标签汇总（时间筛选下量级可控）
        List<Map<String, Object>> allIps = repository.topIps(50_000, start, end, ips);
        Map<String, Long> agg = new LinkedHashMap<String, Long>();
        for (Map<String, Object> row : allIps) {
            String ip = String.valueOf(row.get("name"));
            String label = ipLabelService.labelOf(ip);
            long cnt = toLong(row.get("cnt"));
            Long prev = agg.get(label);
            agg.put(label, (prev == null ? 0L : prev) + cnt);
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Long> e : agg.entrySet()) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("name", e.getKey());
            item.put("cnt", e.getValue());
            rows.add(item);
        }
        // 按请求量降序
        Collections.sort(rows, (a, b) -> Long.compare(toLong(b.get("cnt")), toLong(a.get("cnt"))));
        return withPercent(rows);
    }

    private List<Map<String, Object>> labelIps(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<String, Object>(row);
            String ip = String.valueOf(row.get("name"));
            String label = ipLabelService.labelOf(ip);
            item.put("label", label);
            item.put("labeled", !IpLabelService.OTHER_LABEL.equals(label));
            item.put("platform", !IpLabelService.OTHER_LABEL.equals(label));
            out.add(item);
        }
        return out;
    }

    public static List<String> normalizeIps(List<String> ips) {
        if (ips == null || ips.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String raw : ips) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String[] parts = raw.split("[,;\\s]+");
            for (String part : parts) {
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                String ip = part.trim();
                if (!out.contains(ip)) {
                    out.add(ip);
                }
            }
        }
        return out;
    }

    private List<Map<String, Object>> withPercent(List<Map<String, Object>> rows) {
        long total = 0L;
        for (Map<String, Object> row : rows) {
            total += toLong(row.get("cnt"));
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<String, Object>(row);
            long cnt = toLong(row.get("cnt"));
            item.put("percent", percent(cnt, total));
            out.add(item);
        }
        return out;
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static BigDecimal percent(long part, long total) {
        if (total <= 0L) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part * 100D / total).setScale(2, RoundingMode.HALF_UP);
    }
}
