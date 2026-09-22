package com.gwamcc.nginxlog.repo;

import com.gwamcc.nginxlog.model.NginxAccessLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Repository
public class NginxAccessLogRepository {

    private static final String INSERT_SQL =
            "INSERT INTO nginx_access_log ("
                    + "remote_addr, remote_user, access_time, method, path, protocol, status, body_bytes, "
                    + "referer, user_agent, x_forwarded_for, upstream_addr, upstream_response_time"
                    + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private final JdbcTemplate jdbcTemplate;

    public NginxAccessLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void truncate() {
        jdbcTemplate.execute("TRUNCATE TABLE nginx_access_log");
    }

    public long countAll() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM nginx_access_log", Long.class);
        return n == null ? 0L : n;
    }

    public void batchInsert(List<NginxAccessLog> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(),
                (PreparedStatement ps, NginxAccessLog log) -> {
                    ps.setString(1, log.getRemoteAddr());
                    ps.setString(2, log.getRemoteUser());
                    if (log.getAccessTime() == null) {
                        ps.setNull(3, Types.TIMESTAMP);
                    } else {
                        ps.setTimestamp(3, new Timestamp(log.getAccessTime().getTime()));
                    }
                    ps.setString(4, log.getMethod());
                    ps.setString(5, log.getPath());
                    ps.setString(6, log.getProtocol());
                    if (log.getStatus() == null) {
                        ps.setNull(7, Types.INTEGER);
                    } else {
                        ps.setInt(7, log.getStatus());
                    }
                    if (log.getBodyBytes() == null) {
                        ps.setNull(8, Types.BIGINT);
                    } else {
                        ps.setLong(8, log.getBodyBytes());
                    }
                    ps.setString(9, log.getReferer());
                    ps.setString(10, log.getUserAgent());
                    ps.setString(11, log.getXForwardedFor());
                    ps.setString(12, log.getUpstreamAddr());
                    if (log.getUpstreamResponseTime() == null) {
                        ps.setNull(13, Types.DECIMAL);
                    } else {
                        ps.setBigDecimal(13, log.getUpstreamResponseTime());
                    }
                });
    }

    public List<Map<String, Object>> groupByStatus(Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        return jdbcTemplate.queryForList(
                "SELECT status AS name, COUNT(*) AS cnt FROM nginx_access_log "
                        + where.sql + " GROUP BY status ORDER BY cnt DESC",
                where.args.toArray());
    }

    public List<Map<String, Object>> groupByMethod(Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        return jdbcTemplate.queryForList(
                "SELECT COALESCE(method,'UNKNOWN') AS name, COUNT(*) AS cnt "
                        + "FROM nginx_access_log " + where.sql
                        + " GROUP BY method ORDER BY cnt DESC",
                where.args.toArray());
    }

    public List<Map<String, Object>> topPaths(int limit, Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        List<Object> args = new ArrayList<Object>(where.args);
        args.add(limit);
        return jdbcTemplate.queryForList(
                "SELECT COALESCE(path,'') AS name, COUNT(*) AS cnt "
                        + "FROM nginx_access_log " + where.sql
                        + " GROUP BY path ORDER BY cnt DESC LIMIT ?",
                args.toArray());
    }

    public List<Map<String, Object>> topIps(int limit, Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        List<Object> args = new ArrayList<Object>(where.args);
        args.add(limit);
        return jdbcTemplate.queryForList(
                "SELECT remote_addr AS name, COUNT(*) AS cnt "
                        + "FROM nginx_access_log " + where.sql
                        + " GROUP BY remote_addr ORDER BY cnt DESC LIMIT ?",
                args.toArray());
    }

    public List<Map<String, Object>> topUserAgents(int limit, Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        List<Object> args = new ArrayList<Object>(where.args);
        args.add(limit);
        return jdbcTemplate.queryForList(
                "SELECT COALESCE(user_agent,'(empty)') AS name, COUNT(*) AS cnt "
                        + "FROM nginx_access_log " + where.sql
                        + " GROUP BY user_agent ORDER BY cnt DESC LIMIT ?",
                args.toArray());
    }

    public List<Map<String, Object>> topUpstreams(int limit, Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        List<Object> args = new ArrayList<Object>(where.args);
        args.add(limit);
        return jdbcTemplate.queryForList(
                "SELECT COALESCE(upstream_addr,'(none)') AS name, COUNT(*) AS cnt "
                        + "FROM nginx_access_log " + where.sql
                        + " GROUP BY upstream_addr ORDER BY cnt DESC LIMIT ?",
                args.toArray());
    }

    public List<Map<String, Object>> byHour(Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        String sql = "SELECT DATE_FORMAT(access_time, '%Y-%m-%d %H:00') AS name, COUNT(*) AS cnt "
                + "FROM nginx_access_log "
                + mergeNotNull(where.sql)
                + " GROUP BY DATE_FORMAT(access_time, '%Y-%m-%d %H:00') ORDER BY name";
        return jdbcTemplate.queryForList(sql, where.args.toArray());
    }

    public List<Map<String, Object>> byDay(Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        String sql = "SELECT DATE_FORMAT(access_time, '%Y-%m-%d') AS name, COUNT(*) AS cnt "
                + "FROM nginx_access_log "
                + mergeNotNull(where.sql)
                + " GROUP BY DATE_FORMAT(access_time, '%Y-%m-%d') ORDER BY name";
        return jdbcTemplate.queryForList(sql, where.args.toArray());
    }

    public Map<String, Object> overview(Date start, Date end, List<String> ips) {
        SqlPart where = whereClause(start, end, ips);
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, "
                        + "SUM(CASE WHEN status BETWEEN 200 AND 299 THEN 1 ELSE 0 END) AS ok_2xx, "
                        + "SUM(CASE WHEN status BETWEEN 400 AND 499 THEN 1 ELSE 0 END) AS client_4xx, "
                        + "SUM(CASE WHEN status BETWEEN 500 AND 599 THEN 1 ELSE 0 END) AS server_5xx, "
                        + "SUM(COALESCE(body_bytes,0)) AS total_bytes, "
                        + "AVG(upstream_response_time) AS avg_upstream_ms, "
                        + "MIN(access_time) AS min_time, "
                        + "MAX(access_time) AS max_time "
                        + "FROM nginx_access_log " + where.sql,
                where.args.toArray());
    }

    private static String mergeNotNull(String whereSql) {
        if (whereSql == null || whereSql.isEmpty()) {
            return " WHERE access_time IS NOT NULL ";
        }
        return whereSql + " AND access_time IS NOT NULL ";
    }

    private static SqlPart whereClause(Date start, Date end, List<String> ips) {
        List<Object> args = new ArrayList<Object>();
        StringBuilder sb = new StringBuilder();
        if (start != null) {
            sb.append(sb.length() == 0 ? " WHERE " : " AND ");
            sb.append("access_time >= ?");
            args.add(new Timestamp(start.getTime()));
        }
        if (end != null) {
            sb.append(sb.length() == 0 ? " WHERE " : " AND ");
            sb.append("access_time <= ?");
            args.add(new Timestamp(end.getTime()));
        }
        List<String> normalized = normalizeIps(ips);
        if (!normalized.isEmpty()) {
            sb.append(sb.length() == 0 ? " WHERE " : " AND ");
            sb.append("remote_addr IN (");
            for (int i = 0; i < normalized.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('?');
                args.add(normalized.get(i));
            }
            sb.append(')');
        }
        return new SqlPart(sb.toString(), args);
    }

    private static List<String> normalizeIps(List<String> ips) {
        if (CollectionUtils.isEmpty(ips)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String ip : ips) {
            if (ip == null) {
                continue;
            }
            String trimmed = ip.trim();
            if (!trimmed.isEmpty() && !out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static final class SqlPart {
        private final String sql;
        private final List<Object> args;

        private SqlPart(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args;
        }
    }
}
