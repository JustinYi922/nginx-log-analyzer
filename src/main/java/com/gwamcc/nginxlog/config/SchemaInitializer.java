package com.gwamcc.nginxlog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final ReloadableDataSource dataSource;

    public SchemaInitializer(ReloadableDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/schema.sql"));
        populator.execute(dataSource);
        log.info("启动时已检查/创建 nginx_access_log 表");
    }
}
