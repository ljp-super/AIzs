package com.yupi.yuaiagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class DataSourceConfig {

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/yu_ai_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true}")
    private String url;

    @Value("${spring.datasource.username:root}")
    private String username;

    @Value("${spring.datasource.password:root}")
    private String password;

    @Bean
    public DataSource dataSource() {
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(url);
            dataSource.setUsername(username);
            dataSource.setPassword(password);

            dataSource.getConnection().close();
            log.info("MySQL database connection successful: {}", url);
            return dataSource;
        } catch (Exception e) {
            log.warn("MySQL connection failed, using H2 memory database: {}", e.getMessage());

            DriverManagerDataSource h2DataSource = new DriverManagerDataSource();
            h2DataSource.setDriverClassName("org.h2.Driver");
            h2DataSource.setUrl("jdbc:h2:mem:yu_ai_agent;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL");
            h2DataSource.setUsername("sa");
            h2DataSource.setPassword("");
            return h2DataSource;
        }
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
