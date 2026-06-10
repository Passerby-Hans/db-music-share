package com.music.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.comment.entity.CommentLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论点赞数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，获得 {@code comment_like} 表的单表 CRUD
 * 与条件构造器能力。点赞/取消为单表操作；评论列表所需的「各评论点赞数」「当前
 * 用户已赞集合」由 Service 层用条件构造器做批量聚合查询拼装，规避 N+1，
 * 不手写 XML/注解 SQL，与项目既有 Mapper 风格一致。</p>
 */
@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {
}
