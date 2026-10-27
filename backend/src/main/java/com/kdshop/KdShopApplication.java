package com.kdshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KdShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(KdShopApplication.class, args);
    }
}
