package com.ywh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// @EnableAsync：开启 @Async 后台线程（党建新闻生成 NewsAsyncWorker 用），脱离 HTTP 请求线程跑到底
@EnableAsync
@SpringBootApplication
public class YwhApplication {
    public static void main(String[] args) {
        SpringApplication.run(YwhApplication.class, args);
    }
}
