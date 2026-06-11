package com.music.rating.service;

import com.music.common.result.PageVO;
import com.music.rating.dto.MyRatingVO;
import com.music.rating.dto.RatingStatVO;

/**
 * 评分业务接口。
 *
 * <p>提供提交/撤销评分、某歌评分概况、我的评分列表四项能力。所有写操作的操作者
 * 身份均由调用方（Controller）从服务端会话取出后传入，本层不信任前端传来的 uid，
 * 杜绝伪造身份替他人评分/撤分。</p>
 *
 * <p><strong>评分语义</strong>：一人一首仅一条评分（{@code (uid,sid)} 唯一约束，
 * 决策③）。「提交评分」为 upsert——已评过则更新分数（改分），未评过则插入；
 * 对前端是单一「提交我的评分」语义，不区分首评/改评。评分与评论解耦，互不影响。</p>
 *
 * <p><strong>可评分范围</strong>：仅能给「口径A 公开可见」（已审核未删）的歌评分，
 * 否则视为不存在（404）。</p>
 */
public interface RatingService {

    /**
     * 提交评分（upsert：首评则插入，已评则改分）。
     *
     * <p>仅能给口径A可见的歌评分，否则 404。已存在评分则更新 score 与 rateTime，
     * 不新增记录（依赖 {@code (uid,sid)} 唯一约束的语义）。</p>
     *
     * @param uid   操作者 uid（服务端会话）
     * @param sid   歌曲 sid
     * @param score 分数（1~5，Controller 层已用 @Min/@Max 校验）
     * @throws com.music.common.exception.BizException 歌曲不存在或不可见时抛 404
     */
    void rate(Long uid, Long sid, Integer score);

    /**
     * 撤销评分（幂等）。
     *
     * <p>删除该用户对该歌的评分。若本就没有评分，删除影响 0 行，同样视为成功。
     * 不校验歌曲可见性：已下架的歌也允许撤销其历史评分。</p>
     *
     * @param uid 操作者 uid（服务端会话）
     * @param sid 歌曲 sid
     */
    void cancel(Long uid, Long sid);

    /**
     * 某首歌的评分概况：平均分 + 评分人数 + 我的评分（软鉴权下调用）。
     *
     * <p>不校验歌曲可见性——概况是对历史评分数据的统计，即便歌已下架也应能看到
     * 其曾经的评分情况。{@code currentUid} 为游客时（null），返回的 myScore 为 null；
     * 登录用户则回填其本人评分（未评过亦为 null）。</p>
     *
     * @param sid        歌曲 sid
     * @param currentUid 当前访问者 uid（游客为 null）
     * @return 评分概况
     */
    RatingStatVO getStat(Long sid, Long currentUid);

    /**
     * 我的评分分页列表，按评分时间倒序（最近评分在前）。
     *
     * <p>不过滤失效歌曲：评过的歌即便已下架/驳回也如实列出，每项带 playable 标志，
     * 由前端置灰处理。实现上先分页查评分记录，再批量查歌曲信息回填，规避 N+1。</p>
     *
     * @param uid  操作者 uid（服务端会话）
     * @param page 页码，从 1 起
     * @param size 每页条数
     * @return 我的评分列表项分页
     */
    PageVO<MyRatingVO> listMine(Long uid, long page, long size);
}
