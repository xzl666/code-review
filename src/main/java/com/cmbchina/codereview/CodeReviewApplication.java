package com.cmbchina.codereview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.cmbchina.codereview.infrastructure.persistence.mapper")
@SpringBootApplication
public class CodeReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewApplication.class, args);
    }
}
