package com.gwamcc.nginxlog.service;

import com.gwamcc.nginxlog.config.IpLabelStore;
import com.gwamcc.nginxlog.config.NginxLogProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 按配置网段给来源 IP 打业务归属标签；支持本地 JSON 覆盖与运行时改写。
 */
@Component
public class IpLabelService {

    public static final String OTHER_LABEL = "其他";
    public static final String SOURCE_FILE = "file";
    public static final String SOURCE_YML = "yml";

    private static final Pattern IPV4 = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private final NginxLogProperties properties;
    private final IpLabelStore store;
    private final AtomicReference<List<NginxLogProperties.IpLabelRule>> rulesRef =
            new AtomicReference<List<NginxLogProperties.IpLabelRule>>();
    private volatile String source = SOURCE_YML;

    public IpLabelService(NginxLogProperties properties, IpLabelStore store) {
        this.properties = properties;
        this.store = store;
        reloadFromStoreOrYml();
    }

    public synchronized void reloadFromStoreOrYml() {
        List<NginxLogProperties.IpLabelRule> fromFile = store.load();
        if (fromFile != null) {
            rulesRef.set(copyRules(fromFile));
            source = SOURCE_FILE;
            return;
        }
        rulesRef.set(copyRules(properties.getIpLabels()));
        source = SOURCE_YML;
    }

    public String getSource() {
        return source;
    }

    public String getConfigFile() {
        return store.getFile().toString();
    }

    public boolean hasSavedFile() {
        return store.exists();
    }

    public List<NginxLogProperties.IpLabelRule> listRules() {
        return copyRules(rulesRef.get());
    }

    public List<String> listKnownLabels() {
        Set<String> labels = new LinkedHashSet<String>();
        for (NginxLogProperties.IpLabelRule rule : currentRules()) {
            if (rule != null && StringUtils.hasText(rule.getLabel())) {
                labels.add(rule.getLabel().trim());
            }
        }
        return new ArrayList<String>(labels);
    }

    public synchronized List<NginxLogProperties.IpLabelRule> replaceRules(
            List<NginxLogProperties.IpLabelRule> incoming) {
        List<NginxLogProperties.IpLabelRule> normalized = normalizeAndValidate(incoming);
        store.save(normalized);
        rulesRef.set(copyRules(normalized));
        source = SOURCE_FILE;
        return listRules();
    }

    /**
     * 将单个精确 IP 指派到某系统：从各规则中移除该 IP 的精确条目，再追加到目标系统。
     */
    public synchronized List<NginxLogProperties.IpLabelRule> assignIp(String ip, String label) {
        String trimmedIp = requireIpv4(ip);
        String trimmedLabel = requireLabel(label);

        List<NginxLogProperties.IpLabelRule> next = copyRules(currentRules());
        for (NginxLogProperties.IpLabelRule rule : next) {
            if (rule.getRanges() == null) {
                continue;
            }
            List<String> kept = new ArrayList<String>();
            for (String range : rule.getRanges()) {
                if (range == null) {
                    continue;
                }
                if (trimmedIp.equals(range.trim())) {
                    continue;
                }
                kept.add(range.trim());
            }
            rule.setRanges(kept);
        }

        NginxLogProperties.IpLabelRule target = null;
        for (NginxLogProperties.IpLabelRule rule : next) {
            if (rule.getLabel() != null && trimmedLabel.equalsIgnoreCase(rule.getLabel().trim())) {
                target = rule;
                break;
            }
        }
        if (target == null) {
            target = new NginxLogProperties.IpLabelRule();
            target.setLabel(trimmedLabel);
            target.setRanges(new ArrayList<String>());
            next.add(target);
        } else {
            target.setLabel(trimmedLabel);
            if (target.getRanges() == null) {
                target.setRanges(new ArrayList<String>());
            }
        }
        if (!containsExactIp(target.getRanges(), trimmedIp)) {
            target.getRanges().add(0, trimmedIp);
        }

        // 去掉空 ranges 的规则（避免脏数据）
        List<NginxLogProperties.IpLabelRule> cleaned = new ArrayList<NginxLogProperties.IpLabelRule>();
        for (NginxLogProperties.IpLabelRule rule : next) {
            if (rule.getRanges() != null && !rule.getRanges().isEmpty()) {
                cleaned.add(rule);
            }
        }
        return replaceRules(cleaned);
    }

    public String labelOf(String ip) {
        if (!StringUtils.hasText(ip)) {
            return OTHER_LABEL;
        }
        String trimmed = ip.trim();
        // 精确 IP 优先于前缀/区间，保证 assign 后立即生效
        for (NginxLogProperties.IpLabelRule rule : currentRules()) {
            if (rule == null || !StringUtils.hasText(rule.getLabel()) || rule.getRanges() == null) {
                continue;
            }
            for (String range : rule.getRanges()) {
                if (range != null && trimmed.equals(range.trim())) {
                    return rule.getLabel().trim();
                }
            }
        }
        for (NginxLogProperties.IpLabelRule rule : currentRules()) {
            if (rule == null || !StringUtils.hasText(rule.getLabel()) || rule.getRanges() == null) {
                continue;
            }
            for (String range : rule.getRanges()) {
                if (range == null || trimmed.equals(range.trim())) {
                    continue;
                }
                if (matches(trimmed, range)) {
                    return rule.getLabel().trim();
                }
            }
        }
        return OTHER_LABEL;
    }

    public static boolean matches(String ip, String range) {
        if (!StringUtils.hasText(range)) {
            return false;
        }
        String r = range.trim();
        if (r.equals(ip)) {
            return true;
        }
        // 前缀：10.170.24. 或 10.170.24
        if (r.endsWith(".")) {
            return ip.startsWith(r);
        }
        if (r.matches("\\d+\\.\\d+\\.\\d+") && ip.startsWith(r + ".")) {
            return true;
        }
        // 区间：10.170.24.121-10.170.24.146
        int dash = r.indexOf('-');
        if (dash > 0) {
            String from = r.substring(0, dash).trim();
            String to = r.substring(dash + 1).trim();
            long ipNum = toLong(ip);
            long fromNum = toLong(from);
            long toNum = toLong(to);
            return ipNum >= 0 && fromNum >= 0 && toNum >= 0 && ipNum >= fromNum && ipNum <= toNum;
        }
        return false;
    }

    private List<NginxLogProperties.IpLabelRule> currentRules() {
        List<NginxLogProperties.IpLabelRule> rules = rulesRef.get();
        return rules == null ? Collections.<NginxLogProperties.IpLabelRule>emptyList() : rules;
    }

    private static List<NginxLogProperties.IpLabelRule> normalizeAndValidate(
            List<NginxLogProperties.IpLabelRule> incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("规则列表不能为空");
        }
        List<NginxLogProperties.IpLabelRule> out = new ArrayList<NginxLogProperties.IpLabelRule>();
        for (NginxLogProperties.IpLabelRule rule : incoming) {
            if (rule == null) {
                continue;
            }
            String label = requireLabel(rule.getLabel());
            if (rule.getRanges() == null || rule.getRanges().isEmpty()) {
                throw new IllegalArgumentException("系统「" + label + "」的网段列表不能为空");
            }
            List<String> ranges = new ArrayList<String>();
            for (String range : rule.getRanges()) {
                if (!StringUtils.hasText(range)) {
                    continue;
                }
                String r = range.trim();
                validateRange(r);
                ranges.add(r);
            }
            if (ranges.isEmpty()) {
                throw new IllegalArgumentException("系统「" + label + "」的网段列表不能为空");
            }
            NginxLogProperties.IpLabelRule copy = new NginxLogProperties.IpLabelRule();
            copy.setLabel(label);
            copy.setRanges(ranges);
            out.add(copy);
        }
        return out;
    }

    private static void validateRange(String range) {
        if (IPV4.matcher(range).matches()) {
            return;
        }
        if (range.endsWith(".") && range.matches("\\d+(\\.\\d+){0,2}\\.")) {
            return;
        }
        if (range.matches("\\d+\\.\\d+\\.\\d+")) {
            return;
        }
        int dash = range.indexOf('-');
        if (dash > 0) {
            String from = range.substring(0, dash).trim();
            String to = range.substring(dash + 1).trim();
            if (IPV4.matcher(from).matches() && IPV4.matcher(to).matches()
                    && toLong(from) >= 0 && toLong(to) >= 0 && toLong(from) <= toLong(to)) {
                return;
            }
        }
        throw new IllegalArgumentException("网段格式不合法: " + range
                + "（支持精确 IP、前缀如 10.170.24.、区间如 a.b.c.d-a.b.c.e）");
    }

    private static String requireIpv4(String ip) {
        if (!StringUtils.hasText(ip)) {
            throw new IllegalArgumentException("IP 不能为空");
        }
        String trimmed = ip.trim();
        if (!IPV4.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("IP 格式不合法: " + trimmed);
        }
        return trimmed;
    }

    private static String requireLabel(String label) {
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("系统名不能为空");
        }
        String trimmed = label.trim();
        if (OTHER_LABEL.equals(trimmed)) {
            throw new IllegalArgumentException("系统名不能为「其他」");
        }
        return trimmed;
    }

    private static boolean containsExactIp(List<String> ranges, String ip) {
        for (String range : ranges) {
            if (range != null && ip.equals(range.trim())) {
                return true;
            }
        }
        return false;
    }

    private static List<NginxLogProperties.IpLabelRule> copyRules(
            List<NginxLogProperties.IpLabelRule> source) {
        List<NginxLogProperties.IpLabelRule> out = new ArrayList<NginxLogProperties.IpLabelRule>();
        if (source == null) {
            return out;
        }
        for (NginxLogProperties.IpLabelRule rule : source) {
            if (rule == null) {
                continue;
            }
            NginxLogProperties.IpLabelRule copy = new NginxLogProperties.IpLabelRule();
            copy.setLabel(rule.getLabel());
            List<String> ranges = new ArrayList<String>();
            if (rule.getRanges() != null) {
                for (String r : rule.getRanges()) {
                    if (StringUtils.hasText(r)) {
                        ranges.add(r.trim());
                    }
                }
            }
            copy.setRanges(ranges);
            out.add(copy);
        }
        return out;
    }

    private static long toLong(String ip) {
        if (ip == null) {
            return -1L;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return -1L;
        }
        try {
            long v = 0L;
            for (String part : parts) {
                int n = Integer.parseInt(part);
                if (n < 0 || n > 255) {
                    return -1L;
                }
                v = (v << 8) + n;
            }
            return v;
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }
}
