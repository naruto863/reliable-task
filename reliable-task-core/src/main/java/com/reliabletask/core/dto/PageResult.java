package com.reliabletask.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果通用类
 *
 * <p>返回给 Admin API 和控制台前端的分页读模型。records 只包含当前页数据，
 * total/totalPages/hasNext 用于前端分页控件，不表达查询是否被截断。
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /**
     * 当前页数据列表
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private int pageNum;

    /**
     * 每页大小
     */
    private int pageSize;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 是否有下一页
     */
    private boolean hasNext;

    /**
     * 构建分页结果
     *
     * @param records  数据列表
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        // pageSize 为 0 时避免除零，调用方正常应在请求归一化阶段把 pageSize 修正为正数。
        result.setTotalPages(pageSize == 0 ? 0 : (int) Math.ceil((double) total / pageSize));
        result.setHasNext(pageNum < result.getTotalPages());
        return result;
    }
}
