package com.gwamcc.nginxlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "nginx-log")
public class NginxLogProperties {

    private String defaultFile;
    private int batchSize = 2000;
    /** 上传日志暂存目录 */
    private String uploadDir = System.getProperty("java.io.tmpdir") + "/nginx-log-analyzer-uploads";
    private List<IpLabelRule> ipLabels = new ArrayList<IpLabelRule>();

    public String getDefaultFile() {
        return defaultFile;
    }

    public void setDefaultFile(String defaultFile) {
        this.defaultFile = defaultFile;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public List<IpLabelRule> getIpLabels() {
        return ipLabels;
    }

    public void setIpLabels(List<IpLabelRule> ipLabels) {
        this.ipLabels = ipLabels;
    }

    public static class IpLabelRule {
        /** 展示名，如 office / datacenter */
        private String label;
        /**
         * 匹配规则：精确 IP、前缀（以 . 结尾）、区间（a.b.c.d-a.b.c.e）。
         */
        private List<String> ranges = new ArrayList<String>();

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public List<String> getRanges() {
            return ranges;
        }

        public void setRanges(List<String> ranges) {
            this.ranges = ranges;
        }
    }
}
