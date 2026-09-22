package com.gwamcc.nginxlog.parser;

import com.gwamcc.nginxlog.model.NginxAccessLog;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 nginx access 行。兼容标准 combined，以及本批日志中
 * UA 缺少前引号、XFF 与 upstream 粘连的变体。
 */
public final class NginxAccessLogParser {

    private static final Pattern MAIN = Pattern.compile(
            "^(\\S+) (\\S+) (\\S+) \\[([^\\]]+)] \"(\\S+) ([^\"]*?) (HTTP/[^\"\\s]+)\" (\\d{3}) (\\d+|-) \"([^\"]*)\" (.*)$");

    private static final Pattern TAIL_STANDARD = Pattern.compile(
            "^\"([^\"]*)\"(?: \"([^\"]*)\")?(?:\\s+(\\S+))?(?:\\s+(\\S+))?(?:\\s+(\\S+))?\\s*$");

    private static final Pattern TAIL_BROKEN = Pattern.compile(
            "^([^\"]*)\" \"([^\"]*)\"\\s*(\\S+)?(?:\\s+(\\S+))?(?:\\s+(\\S+))?\\s*$");

    private static final ThreadLocal<SimpleDateFormat> TIME_FMT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH));

    private NginxAccessLogParser() {
    }

    public static NginxAccessLog parse(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher m = MAIN.matcher(trimmed);
        if (!m.matches()) {
            return null;
        }
        NginxAccessLog log = new NginxAccessLog();
        log.setRemoteAddr(m.group(1));
        String user = m.group(3);
        log.setRemoteUser("-".equals(user) ? null : user);
        log.setAccessTime(parseTime(m.group(4)));
        log.setMethod(m.group(5));
        log.setPath(truncate(m.group(6), 1024));
        log.setProtocol(m.group(7));
        log.setStatus(Integer.valueOf(m.group(8)));
        log.setBodyBytes(parseBytes(m.group(9)));
        String referer = m.group(10);
        log.setReferer("-".equals(referer) ? null : truncate(referer, 1024));
        parseTail(m.group(11), log);
        return log;
    }

    private static void parseTail(String tail, NginxAccessLog log) {
        if (tail == null || tail.isEmpty()) {
            return;
        }
        Matcher standard = TAIL_STANDARD.matcher(tail);
        if (standard.matches()) {
            log.setUserAgent(truncate(emptyToNull(standard.group(1)), 512));
            log.setXForwardedFor(emptyToNull(standard.group(2)));
            log.setUpstreamAddr(emptyToNull(standard.group(3)));
            log.setUpstreamResponseTime(parseDecimal(firstNumber(standard.group(4), standard.group(5))));
            return;
        }
        Matcher broken = TAIL_BROKEN.matcher(tail);
        if (broken.matches()) {
            log.setUserAgent(truncate(emptyToNull(broken.group(1)), 512));
            log.setXForwardedFor(emptyToNull(broken.group(2)));
            log.setUpstreamAddr(emptyToNull(broken.group(3)));
            log.setUpstreamResponseTime(parseDecimal(firstNumber(broken.group(4), broken.group(5))));
            return;
        }
        // 兜底：整段当 UA
        String ua = tail;
        if (ua.endsWith("\"")) {
            ua = ua.substring(0, ua.length() - 1);
        }
        if (ua.startsWith("\"")) {
            ua = ua.substring(1);
        }
        log.setUserAgent(truncate(ua, 512));
    }

    private static String firstNumber(String a, String b) {
        if (a != null && !"-".equals(a)) {
            return a;
        }
        if (b != null && !"-".equals(b)) {
            return b;
        }
        return null;
    }

    private static java.util.Date parseTime(String raw) {
        try {
            return TIME_FMT.get().parse(raw);
        } catch (ParseException ex) {
            return null;
        }
    }

    private static Long parseBytes(String raw) {
        if (raw == null || "-".equals(raw)) {
            return 0L;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isEmpty() || "-".equals(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) {
            return null;
        }
        return value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
