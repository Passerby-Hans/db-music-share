package com.music.playrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.playrecord.entity.PlayRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点唱记录数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper},获得 {@code play_record} 表单表 CRUD
 * 与条件构造器能力。点唱写入、未来「我的点唱历史」「某歌点唱明细」均为单表操作,
 * 无需手写 SQL,与项目既有 Mapper 风格一致。</p>
 */
@Mapper
public interface PlayRecordMapper extends BaseMapper<PlayRecord> {
}
