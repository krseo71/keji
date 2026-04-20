package com.example.fileapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FileAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileAppApplication.class, args);
    }
}
