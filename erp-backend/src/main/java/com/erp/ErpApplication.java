package com.erp;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * XeFlow open-source ERP application.
 */
@EnableScheduling
@SpringBootApplication
@MapperScan(basePackages = "com.erp", annotationClass = Mapper.class)
public class ErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }
}
