package com.liushiqi.blogmain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling 开启定时任务，ViewCountSyncTask 的 @Scheduled 才会生效
@EnableScheduling
@SpringBootApplication
public class BlogMainApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogMainApplication.class, args);
    }

}
