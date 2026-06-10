package com.music.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.favorite.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，获得 {@code favorite} 表的单表 CRUD
 * 与条件构造器能力。收藏/取消/状态查询均为单表操作；「我的收藏列表」需联
 * {@code song} 投影歌曲信息，统一在 Service 层用条件构造器 + 批量查询拼装，
 * 不手写 XML/注解 SQL，与项目既有 Mapper 风格保持一致。</p>
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
