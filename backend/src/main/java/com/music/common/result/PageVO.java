package com.music.common.result;

import java.util.List;

/**
 * 分页结果通用包装。
 *
 * <p>承载一页数据与总量信息，供各模块的分页查询接口统一返回。
 * 放在 {@code common} 下而非具体模块，便于全项目复用。</p>
 *
 * @param <T> 列表项类型
 */
public class PageVO<T> {

    /** 当前页数据。 */
    private List<T> records;

    /** 总记录数。 */
    private long total;

    /** 当前页码（从 1 起）。 */
    private long page;

    /** 每页条数。 */
    private long size;

    /** 无参构造器（序列化需要）。 */
    public PageVO() {
    }

    /**
     * 全参构造器。
     *
     * @param records 当前页数据
     * @param total   总记录数
     * @param page    当前页码
     * @param size    每页条数
     */
    public PageVO(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
