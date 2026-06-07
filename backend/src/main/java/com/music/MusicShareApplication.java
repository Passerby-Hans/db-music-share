package com.music;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类。
 *
 * <p>{@code @MapperScan} 扫描 {@code com.music} 下所有包的 Mapper 接口，
 * 适配「按模块分包」的结构（各模块的 mapper 分散在 com.music.&lt;模块&gt;.mapper）。
 * {@code @EnableScheduling} 开启定时任务（孤儿文件清理等）。</p>
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.music.**.mapper")
public class MusicShareApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicShareApplication.class, args);
    }
}
