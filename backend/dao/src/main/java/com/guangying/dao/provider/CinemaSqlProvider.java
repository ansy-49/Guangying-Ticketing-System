package com.guangying.dao.provider;

import com.guangying.domain.model.dto.CinemaQueryDTO;
import org.apache.ibatis.jdbc.SQL;

/**
 * 影院动态SQL构建器
 * <p>
 * 使用 MyBatis SQL Builder 构建复杂动态查询，
 * 支持品牌/服务/厅型/商圈/地铁多维度联合过滤。
 * </p>
 */
public class CinemaSqlProvider {

    /**
     * 构建影院列表查询SQL
     * <p>
     * 当需要按服务或厅型过滤时，通过 JOIN 关联表实现，
     * 使用 DISTINCT 避免因 JOIN 导致的重复记录。
     * 所有过滤参数均通过 MyBatis #{} 占位符防止SQL注入。
     * </p>
     */
    public String selectCinemaList(CinemaQueryDTO query) {
        SQL sql = new SQL();
        sql.SELECT_DISTINCT("c.*");
        sql.FROM("cinema c");

        // 服务类型过滤 — 通过关联表 JOIN
        if (query.getServiceId() != null && query.getServiceId() > 0) {
            sql.JOIN("cinema_service_rel csr ON c.id = csr.cinema_id AND csr.service_id = #{serviceId}");
        }

        // 厅型过滤 — 通过关联表 JOIN
        if (query.getHallType() != null && query.getHallType() > 0) {
            sql.JOIN("cinema_hall_type_rel chr ON c.id = chr.cinema_id AND chr.hall_type_id = #{hallType}");
        }

        // 基础条件
        sql.WHERE("c.city_id = #{cityId}");
        sql.WHERE("c.deleted = 0");

        // 品牌过滤
        if (query.getBrandId() != null && query.getBrandId() > 0) {
            sql.WHERE("c.brand_id = #{brandId}");
        }

        // 行政区过滤
        if (query.getDistrictId() != null && query.getDistrictId() > 0) {
            sql.WHERE("c.district_id = #{districtId}");
        }

        // 商圈/区域过滤
        if (query.getAreaId() != null && query.getAreaId() > 0) {
            sql.WHERE("c.area_id = #{areaId}");
        }

        sql.ORDER_BY("c.sort_order, c.id");

        // MyBatis SQL Builder 不直接支持 LIMIT/OFFSET，拼接到末尾
        return sql.toString() + " LIMIT #{limit} OFFSET #{offset}";
    }
}
