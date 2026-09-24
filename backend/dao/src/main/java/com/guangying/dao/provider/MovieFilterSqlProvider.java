package com.guangying.dao.provider;

import org.apache.ibatis.jdbc.SQL;

/**
 * 电影筛选动态SQL构建器
 * <p>
 * 支持按类型(cat)、地区(src)、年份(release_year)、状态(movie_status)多维筛选。
 * 使用 MyBatis SQL Builder 防止SQL注入。
 * </p>
 */
public class MovieFilterSqlProvider {

    /**
     * 构建电影筛选SQL
     */
    public String filterMovies(Integer movieStatus, String cat, String src, Integer year, String sortBy, int offset, int limit) {
        SQL sql = new SQL();
        sql.SELECT("*");
        sql.FROM("movie");

        appendWhere(sql, movieStatus, cat, src, year);

        // 排序
        if ("time".equals(sortBy)) {
            sql.ORDER_BY("id DESC");
        } else if ("rating".equals(sortBy)) {
            sql.ORDER_BY("sc DESC, sort_order ASC");
        } else {
            // 默认按热度（想看人数降序）
            sql.ORDER_BY("wish DESC, sort_order ASC");
        }

        return sql.toString() + " LIMIT #{limit} OFFSET #{offset}";
    }

    /**
     * 构建电影筛选计数SQL
     */
    public String countFilterMovies(Integer movieStatus, String cat, String src, Integer year) {
        SQL sql = new SQL();
        sql.SELECT("COUNT(*)");
        sql.FROM("movie");
        appendWhere(sql, movieStatus, cat, src, year);
        return sql.toString();
    }

    private void appendWhere(SQL sql, Integer movieStatus, String cat, String src, Integer year) {
        sql.WHERE("deleted = 0");

        if (movieStatus != null) {
            sql.WHERE("movie_status = #{movieStatus}");
        }

        if (cat != null && !cat.isEmpty()) {
            // cat字段是逗号分隔的字符串，如 "剧情,科幻"，使用 LIKE 模糊匹配
            sql.WHERE("cat LIKE CONCAT('%', #{cat}, '%')");
        }

        if (src != null && !src.isEmpty()) {
            sql.WHERE("src LIKE CONCAT('%', #{src}, '%')");
        }

        if (year != null && year > 0) {
            sql.WHERE("release_year = #{year}");
        }
    }
}
