package com.kdshop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启定时任务：超时取消、自动确认收货。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
