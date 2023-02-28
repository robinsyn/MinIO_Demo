package com.iredbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import tk.mybatis.spring.annotation.MapperScan;

@EnableConfigurationProperties
@SpringBootApplication
@MapperScan(basePackages = "com.iredbook.mapper")
public class MinIoDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinIoDemoApplication.class, args);
    }

}
