package com.music.playlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.playlist.entity.PlaylistDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 歌单详情数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，获得 {@code playlist_detail} 表的单表
 * CRUD 与条件构造器能力。加歌/移歌依赖 {@code (plid,sid)} 唯一约束做幂等；
 * 歌单详情的曲目列表为防 N+1：先分页查曲目记录，再用 {@code selectBatchIds}
 * 批量回填歌曲信息，固定 2 次查询，与页大小无关——同收藏列表的实现思路。</p>
 */
@Mapper
public interface PlaylistDetailMapper extends BaseMapper<PlaylistDetail> {
}
