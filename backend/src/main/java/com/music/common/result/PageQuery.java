package com.music.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数规范化工具（评审问题 S-05）。
 *
 * <p>Controller 层的 {@code page}/{@code size} 来自外部请求，此前未做任何校验：
 * {@code page<1} 会生成负 OFFSET 触发 SQL 异常（500）；{@code size} 无上限可一次
 * 拉全表；特别地 {@code size<0} 时 MyBatis-Plus 分页插件<b>不拼接 LIMIT</b>，
 * 等于整表导出。本工具把参数统一钳位到安全区间：</p>
 * <ul>
 *   <li>{@code page < 1} → 1；</li>
 *   <li>{@code size < 1} → 10（与各接口默认值一致）；</li>
 *   <li>{@code size > 100} → 100（上限，另有分页插件 maxLimit 双保险）。</li>
 * </ul>
 *
 * <p>纯静态工具，所有列表查询统一经 {@link #of} 构造 {@link Page}。</p>
 */
public final class PageQuery {

    /** 每页条数上限。 */
    public static final long MAX_SIZE = 100;

    /** size 非法（&lt;1）时的缺省每页条数，与各 Controller 的 defaultValue 对齐。 */
    public static final long DEFAULT_SIZE = 10;

    private PageQuery() {
    }

    /**
     * 构造钳位后的分页对象。
     *
     * @param page 页码（从 1 起；&lt;1 钳为 1）
     * @param size 每页条数（&lt;1 置默认 10；&gt;100 钳为 100）
     * @param <T>  记录类型
     * @return 规范化的 {@link Page}
     */
    public static <T> Page<T> of(long page, long size) {
        return new Page<>(Math.max(page, 1), size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE));
    }
}
