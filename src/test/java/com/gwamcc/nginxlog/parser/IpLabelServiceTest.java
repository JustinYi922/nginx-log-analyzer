package com.gwamcc.nginxlog.parser;

import com.gwamcc.nginxlog.config.IpLabelStore;
import com.gwamcc.nginxlog.config.NginxLogProperties;
import com.gwamcc.nginxlog.service.IpLabelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpLabelServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void matches_platformRanges() {
        assertTrue(IpLabelService.matches("10.170.24.125", "10.170.24.121-10.170.24.146"));
        assertTrue(IpLabelService.matches("10.170.24.121", "10.170.24.121-10.170.24.146"));
        assertTrue(IpLabelService.matches("10.170.24.146", "10.170.24.121-10.170.24.146"));
        assertFalse(IpLabelService.matches("10.170.24.120", "10.170.24.121-10.170.24.146"));
        assertTrue(IpLabelService.matches("10.170.68.63", "10.170.68.61-10.170.68.65"));
        assertFalse(IpLabelService.matches("10.168.100.1", "10.170.24.121-10.170.24.146"));
    }

    @Test
    void ymlFallback_thenJsonOverrideAndAssign() {
        NginxLogProperties props = new NginxLogProperties();
        NginxLogProperties.IpLabelRule ymlRule = new NginxLogProperties.IpLabelRule();
        ymlRule.setLabel("biz-workers");
        ymlRule.setRanges(Collections.singletonList("10.170.24.125-10.170.24.136"));
        props.setIpLabels(Collections.singletonList(ymlRule));

        IpLabelStore store = new IpLabelStore(tempDir.resolve("ip-labels.json"));
        IpLabelService service = new IpLabelService(props, store);
        assertEquals(IpLabelService.SOURCE_YML, service.getSource());
        assertEquals("biz-workers", service.labelOf("10.170.24.130"));
        assertEquals(IpLabelService.OTHER_LABEL, service.labelOf("10.1.1.1"));

        NginxLogProperties.IpLabelRule fileRule = new NginxLogProperties.IpLabelRule();
        fileRule.setLabel("edge-nginx");
        fileRule.setRanges(Arrays.asList("10.1.1.1", "10.170.24."));
        service.replaceRules(Collections.singletonList(fileRule));

        assertEquals(IpLabelService.SOURCE_FILE, service.getSource());
        assertEquals("edge-nginx", service.labelOf("10.1.1.1"));
        assertEquals("edge-nginx", service.labelOf("10.170.24.200"));
        assertEquals("edge-nginx", service.labelOf("10.170.24.130"));

        service.assignIp("10.170.24.130", "biz-workers");
        // 精确 IP 写在 biz-workers 规则最前，优先于 edge-nginx 的前缀匹配
        assertEquals("biz-workers", service.labelOf("10.170.24.130"));
        assertEquals("edge-nginx", service.labelOf("10.1.1.1"));
        assertEquals("edge-nginx", service.labelOf("10.170.24.200"));

        List<String> labels = service.listKnownLabels();
        assertTrue(labels.contains("biz-workers"));
        assertTrue(labels.contains("edge-nginx"));
    }
}
