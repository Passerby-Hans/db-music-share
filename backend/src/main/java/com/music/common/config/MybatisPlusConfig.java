package com.music.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.music.common.result.PageQuery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>注册分页内置拦截器，使 {@code page()} 查询能自动改写为
 * 带 LIMIT/OFFSET 的 PostgreSQL 分页 SQL（搜索、列表等接口将依赖此能力）。</p>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器链，加入分页插件。
     *
     * @return 配置好分页（PostgreSQL 方言）的拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 指定数据库类型为 PostgreSQL，分页 SQL 才能生成正确方言
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 每页条数全局上限（评审问题 S-05）：即使调用方漏做参数校验，插件也会把
        // 超大 size 压到 100，防止一次拉全表。注意 maxLimit 只钳制正数 size，
        // size<0 仍会绕过 LIMIT 拼接，故各查询点还须统一经 PageQuery.of 规范化（双保险）
        pagination.setMaxLimit(PageQuery.MAX_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
