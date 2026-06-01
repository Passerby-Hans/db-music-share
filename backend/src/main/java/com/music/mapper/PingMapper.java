package com.music.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PingMapper {

    @Select("SELECT COUNT(*) FROM song")
    long countSongs();
}
