package com.gwamcc.nginxlog.service;

import com.gwamcc.nginxlog.config.NginxLogProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 按配置网段给来源 IP 打业务归属标签。
 */
@Component
public class IpLabelService {

    public static final String OTHER_LABEL = "其他";

    private final NginxLogProperties properties;

    public IpLabelService(NginxLogProperties properties) {
        this.properties = properties;
    }

    public String labelOf(String ip) {
        if (!StringUtils.hasText(ip)) {
            return OTHER_LABEL;
        }
        String trimmed = ip.trim();
        List<NginxLogProperties.IpLabelRule> rules = properties.getIpLabels();
        if (rules == null) {
            return OTHER_LABEL;
        }
        for (NginxLogProperties.IpLabelRule rule : rules) {
            if (rule == null || !StringUtils.hasText(rule.getLabel()) || rule.getRanges() == null) {
                continue;
            }
            for (String range : rule.getRanges()) {
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
