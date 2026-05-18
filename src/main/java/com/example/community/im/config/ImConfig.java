package com.example.community.im.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ImConfig {

    /**
     * 离线消息补发执行器（虚拟线程）。
     * 每个任务分配一个虚拟线程；遇到 DB/Redis I/O 阻塞时虚拟线程挂起，
     * 平台线程立即释放去处理其他任务，彻底消除线程堆积。
     * 并发上限由 HikariCP 连接池（非线程数）控制，提供天然背压。
     */
    @Bean("offlineMessageExecutor")
    public Executor offlineMessageExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
