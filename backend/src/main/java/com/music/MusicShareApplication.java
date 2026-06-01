package com.music;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.music.mapper")
public class MusicShareApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicShareApplication.class, args);
    }
}
