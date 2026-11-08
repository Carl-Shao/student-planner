package com.planner.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.planner.mapper")
public class MybatisConfig {
}
