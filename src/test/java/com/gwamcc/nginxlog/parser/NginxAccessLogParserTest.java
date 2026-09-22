package com.gwamcc.nginxlog.parser;

import com.gwamcc.nginxlog.model.NginxAccessLog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NginxAccessLogParserTest {

    @Test
    void parse_combinedShortLine() {
        String line = "10.166.84.12 - - [23/Jul/2025:15:27:33 +0800] \"GET / HTTP/1.1\" 404 22 \"-\" \"curl/7.71.1\"";
        NginxAccessLog log = NginxAccessLogParser.parse(line);
        assertNotNull(log);
        assertEquals("10.166.84.12", log.getRemoteAddr());
        assertEquals("GET", log.getMethod());
        assertEquals("/", log.getPath());
        assertEquals(Integer.valueOf(404), log.getStatus());
        assertEquals(Long.valueOf(22L), log.getBodyBytes());
        assertEquals("curl/7.71.1", log.getUserAgent());
        assertNull(log.getUpstreamAddr());
    }

    @Test
    void parse_brokenQuoteWithUpstream() {
        String line = "10.168.70.103 - - [11/Dec/2025:10:00:45 +0800] \"POST /v1/rerank HTTP/1.1\" 200 128186 \"-\" "
                + "python-requests/2.32.4\" \"-\"10.170.68.50:18083 0.318 0.323";
        NginxAccessLog log = NginxAccessLogParser.parse(line);
        assertNotNull(log);
        assertEquals("POST", log.getMethod());
        assertEquals("/v1/rerank", log.getPath());
        assertEquals(Integer.valueOf(200), log.getStatus());
        assertEquals("python-requests/2.32.4", log.getUserAgent());
        assertEquals("10.170.68.50:18083", log.getUpstreamAddr());
        assertEquals("0.318", log.getUpstreamResponseTime().toPlainString());
    }
}
