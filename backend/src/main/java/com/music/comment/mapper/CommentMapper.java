package com.music.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.comment.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，获得 {@code comment} 表的单表 CRUD
 * 与条件构造器能力。本模块的"主评论分页 + 回复数"等组合查询，统一在 Service 层
 * 用 LambdaQueryWrapper 拼装并辅以批量查询规避 N+1，不手写 XML/注解 SQL，
 * 与项目既有 Mapper（SongMapper/UserMapper 等）风格保持一致。</p>
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
