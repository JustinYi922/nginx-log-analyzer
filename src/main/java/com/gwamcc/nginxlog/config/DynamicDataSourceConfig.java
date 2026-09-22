package com.gwamcc.nginxlog.config;

import com.gwamcc.nginxlog.model.DbConnectionSettings;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class DynamicDataSourceConfig {

    private static final Pattern JDBC_MYSQL =
            Pattern.compile("^jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]*)(?:\\?(.*))?$", Pattern.CASE_INSENSITIVE);

    @Bean
    @Primary
    public ReloadableDataSource dataSource(DataSourceProperties props, DbSettingsStore store) {
        DbConnectionSettings saved = store.load();
        DbConnectionSettings initial = saved != null ? saved : fromSpring(props);
        ReloadableDataSource ds = new ReloadableDataSource();
        ds.reload(initial);
        return ds;
    }

    static DbConnectionSettings fromSpring(DataSourceProperties props) {
        DbConnectionSettings s = new DbConnectionSettings();
        if (StringUtils.hasText(props.getUsername())) {
            s.setUsername(props.getUsername());
        }
        if (props.getPassword() != null) {
            s.setPassword(props.getPassword());
        }
        String url = props.getUrl();
        if (StringUtils.hasText(url)) {
            Matcher m = JDBC_MYSQL.matcher(url.trim());
            if (m.matches()) {
                s.setHost(m.group(1));
                if (m.group(2) != null) {
                    s.setPort(Integer.parseInt(m.group(2)));
                }
                if (m.group(3) != null && !m.group(3).isEmpty()) {
                    s.setDatabase(m.group(3));
                }
                if (m.group(4) != null && !m.group(4).isEmpty()) {
                    s.setParams(m.group(4));
                }
            } else {
                // 兜底：尽量解析 URI 风格
                try {
                    String stripped = url.replaceFirst("(?i)^jdbc:", "");
                    URI uri = URI.create(stripped);
                    if (uri.getHost() != null) {
                        s.setHost(uri.getHost());
                    }
                    if (uri.getPort() > 0) {
                        s.setPort(uri.getPort());
                    }
                    if (uri.getPath() != null && uri.getPath().length() > 1) {
                        s.setDatabase(uri.getPath().substring(1));
                    }
                    if (uri.getQuery() != null) {
                        s.setParams(uri.getQuery());
                    }
                } catch (Exception ignored) {
                    // keep defaults
                }
            }
        }
        return s;
    }
}
