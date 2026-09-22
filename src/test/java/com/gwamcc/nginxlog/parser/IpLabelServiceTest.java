package com.gwamcc.nginxlog.parser;

import com.gwamcc.nginxlog.service.IpLabelService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpLabelServiceTest {

    @Test
    void matches_platformRanges() {
        assertTrue(IpLabelService.matches("10.170.24.125", "10.170.24.121-10.170.24.146"));
        assertTrue(IpLabelService.matches("10.170.24.121", "10.170.24.121-10.170.24.146"));
        assertTrue(IpLabelService.matches("10.170.24.146", "10.170.24.121-10.170.24.146"));
        assertFalse(IpLabelService.matches("10.170.24.120", "10.170.24.121-10.170.24.146"));
        assertTrue(IpLabelService.matches("10.170.68.63", "10.170.68.61-10.170.68.65"));
        assertFalse(IpLabelService.matches("10.168.100.1", "10.170.24.121-10.170.24.146"));
    }
}
