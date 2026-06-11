package com.music.playlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.playlist.entity.Playlist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 歌单数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，获得 {@code playlist} 表的单表 CRUD
 * 与条件构造器能力。歌单的增删改查、「我的歌单」「公开歌单」分页均为单表操作，
 * 统一在 Service 层用条件构造器拼装，不手写 XML/注解 SQL，与项目既有 Mapper
 * 风格保持一致。</p>
 */
@Mapper
public interface PlaylistMapper extends BaseMapper<Playlist> {
}
