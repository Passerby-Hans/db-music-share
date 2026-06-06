package com.music.song.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.song.entity.Album;
import org.apache.ibatis.annotations.Mapper;

/**
 * 专辑数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，获得单表 CRUD 与
 * 条件构造器（LambdaQueryWrapper）能力，无需手写 SQL。</p>
 */
@Mapper
public interface AlbumMapper extends BaseMapper<Album> {
}
