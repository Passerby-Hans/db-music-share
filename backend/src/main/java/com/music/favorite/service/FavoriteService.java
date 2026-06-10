package com.music.favorite.service;

import com.music.common.result.PageVO;
import com.music.favorite.dto.FavoriteVO;

/**
 * 收藏业务接口。
 *
 * <p>提供收藏/取消/我的收藏列表/收藏状态查询四项能力。所有写操作的操作者
 * 身份均由调用方（Controller）从服务端会话取出后传入，本层不信任也不接收
 * 前端传来的 uid，杜绝越权收藏/取消他人收藏。</p>
 */
public interface FavoriteService {

    /**
     * 收藏一首歌（幂等）。
     *
     * <p>只能收藏「口径A 公开可见」的歌（已通过审核且未删），否则视为不存在。
     * 若该用户此前已收藏过同一首歌，则不重复插入、直接视为成功（幂等），
     * 不抛异常——既防住唯一约束冲突，也契合「再点一次收藏」的前端语义。</p>
     *
     * @param uid 操作者 uid（服务端会话）
     * @param sid 歌曲 sid
     * @throws com.music.common.exception.BizException 歌曲不存在或不可见时抛 404
     */
    void add(Long uid, Long sid);

    /**
     * 取消收藏（幂等）。
     *
     * <p>删除该用户对该歌的收藏记录。若本就没有收藏，删除影响 0 行，
     * 同样视为成功（幂等）——前端「取消收藏」无需先判断是否已收藏。
     * 取消收藏不校验歌曲是否仍可见：已下架的歌也应允许用户取消收藏。</p>
     *
     * @param uid 操作者 uid（服务端会话）
     * @param sid 歌曲 sid
     */
    void remove(Long uid, Long sid);

    /**
     * 我的收藏分页列表，按收藏时间倒序（最近收藏在前）。
     *
     * <p>不过滤失效歌曲：收藏过的歌即便已下架/驳回也如实列出，每项通过
     * {@link FavoriteVO#getPlayable()} 标记当前是否可播放，由前端置灰处理。
     * 实现上先分页查收藏记录，再批量查歌曲信息回填，规避 N+1。</p>
     *
     * @param uid  操作者 uid（服务端会话）
     * @param page 页码，从 1 起
     * @param size 每页条数
     * @return 收藏列表项分页
     */
    PageVO<FavoriteVO> listMine(Long uid, long page, long size);

    /**
     * 查询某用户是否已收藏某首歌。
     *
     * <p>供歌曲详情页渲染收藏按钮的高亮状态。不校验歌曲可见性，
     * 纯粹反映收藏关系是否存在。</p>
     *
     * @param uid 操作者 uid（服务端会话）
     * @param sid 歌曲 sid
     * @return 已收藏返回 {@code true}，否则 {@code false}
     */
    boolean isFavorited(Long uid, Long sid);
}
