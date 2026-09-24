package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.UserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 系统用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    /**
     * 扣减积分（原子操作，防止并发超扣）
     * @return 影响行数：1=成功，0=积分不足
     */
    @Update("UPDATE sys_user SET points = points - #{amount}, update_time = CURRENT_TIMESTAMP WHERE id = #{userId} AND points >= #{amount} AND deleted = 0")
    int deductPoints(@Param("userId") Long userId, @Param("amount") int amount);

    /**
     * 增加积分（退款/取消时回退）
     */
    @Update("UPDATE sys_user SET points = points + #{amount}, update_time = CURRENT_TIMESTAMP WHERE id = #{userId} AND deleted = 0")
    int addPoints(@Param("userId") Long userId, @Param("amount") int amount);
}
