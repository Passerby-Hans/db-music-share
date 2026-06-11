package com.music.rating.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.rating.entity.Rating;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评分数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，获得 {@code rating} 表的单表 CRUD
 * 与条件构造器能力。提交/撤销评分、「我的评分」分页均为单表操作；某歌评分概况
 * （平均分 + 评分人数）用条件构造器的聚合查询（{@code AVG/COUNT}）实现，统一在
 * Service 层拼装，不手写 XML/注解 SQL，与项目既有 Mapper 风格保持一致。</p>
 */
@Mapper
public interface RatingMapper extends BaseMapper<Rating> {
}
