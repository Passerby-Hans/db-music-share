package com.music.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表数据访问接口。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，自动获得对 {@code app_user} 表的
 * 单表 CRUD 能力（insert/selectById/updateById 等），无需手写基础 SQL。
 * 复杂条件查询通过 Service 层的 LambdaQueryWrapper 组装。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
