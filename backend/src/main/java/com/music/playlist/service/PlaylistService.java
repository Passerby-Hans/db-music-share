package com.music.playlist.service;

import com.music.common.result.PageVO;
import com.music.playlist.dto.PlaylistCreateDTO;
import com.music.playlist.dto.PlaylistDetailVO;
import com.music.playlist.dto.PlaylistUpdateDTO;
import com.music.playlist.dto.PlaylistVO;

/**
 * 歌单业务接口。
 *
 * <p>提供歌单的增删改查、歌单内曲目的增删，以及两类列表（我的歌单 / 公开歌单广场）。
 * 所有写操作的操作者身份均由调用方（Controller）从服务端会话取出后传入，本层
 * 不信任前端传来的 uid，杜绝越权管理他人歌单。</p>
 *
 * <p><strong>可见性模型（两层叠加）</strong>：</p>
 * <ul>
 *   <li><b>歌单层</b>：公开歌单任何登录用户可看；私密歌单仅 owner（及管理员）可看，
 *       否则视为不存在（404，不泄露私密歌单的存在性）；</li>
 *   <li><b>歌曲层</b>：加入歌单要求歌曲「口径A 公开可见」（已审核未删）；歌单详情
 *       的曲目<em>不剔除</em>失效歌曲，仅以 {@code playable=false} 标记置灰。</li>
 * </ul>
 *
 * <p><strong>权限</strong>：创建/查看自己的歌单——登录即可；改/删歌单、加/移曲目
 * ——owner 或管理员，否则 403。</p>
 */
public interface PlaylistService {

    /**
     * 创建歌单。
     *
     * <p>归属当前用户。{@code isPublic} 为 null 时兜底为公开（true），
     * 对齐数据库默认值。</p>
     *
     * @param uid 操作者 uid（服务端会话）
     * @param dto 创建参数（name 必填）
     * @return 新建歌单的 plid
     */
    Long create(Long uid, PlaylistCreateDTO dto);

    /**
     * 修改歌单元信息（名称/简介/封面/公开性）。
     *
     * <p>仅 owner 或管理员可改。不涉及曲目增删。</p>
     *
     * @param uid  操作者 uid（服务端会话）
     * @param role 操作者角色（用于管理员越权判断）
     * @param plid 歌单 plid
     * @param dto  更新参数
     * @throws com.music.common.exception.BizException 歌单不存在→404；非 owner 且非管理员→403
     */
    void update(Long uid, Integer role, Long plid, PlaylistUpdateDTO dto);

    /**
     * 删除歌单。
     *
     * <p>仅 owner 或管理员可删。歌单内曲目记录由数据库 {@code ON DELETE CASCADE}
     * 级联物理删除。</p>
     *
     * @param uid  操作者 uid（服务端会话）
     * @param role 操作者角色（用于管理员越权判断）
     * @param plid 歌单 plid
     * @throws com.music.common.exception.BizException 歌单不存在→404；非 owner 且非管理员→403
     */
    void delete(Long uid, Integer role, Long plid);

    /**
     * 我的歌单分页（含私密），按创建时间倒序。
     *
     * <p>返回当前用户创建的全部歌单（公开+私密）。每项带曲目数，
     * 曲目数为防 N+1 用一次 group by 批量统计。</p>
     *
     * @param uid  操作者 uid（服务端会话）
     * @param page 页码，从 1 起
     * @param size 每页条数
     * @return 歌单列表项分页
     */
    PageVO<PlaylistVO> listMine(Long uid, long page, long size);

    /**
     * 公开歌单广场分页（占位）。
     *
     * <p><strong>1.0 暂为空壳</strong>：当前返回空分页，仅占位保留接口契约，
     * 待后续「发现页」需求再补实现（仅列 {@code is_public=true} 的歌单，
     * 支持按名称关键词搜索）。</p>
     *
     * @param keyword 名称关键词（可空，预留）
     * @param page    页码，从 1 起
     * @param size    每页条数
     * @return 空分页（占位）
     */
    PageVO<PlaylistVO> listPublic(String keyword, long page, long size);

    /**
     * 歌单详情 + 曲目分页（软鉴权下调用）。
     *
     * <p>可见性：公开歌单任何人可看；私密歌单仅 owner/管理员可看，否则 404。
     * 曲目按加入时间倒序，失效歌曲不剔除（playable=false）。</p>
     *
     * @param plid        歌单 plid
     * @param currentUid  当前访问者 uid（游客为 null）
     * @param currentRole 当前访问者角色（游客为 null）
     * @param page        曲目页码，从 1 起
     * @param size        曲目每页条数
     * @return 歌单详情（元信息 + 曲目分页）
     * @throws com.music.common.exception.BizException 歌单不存在或无权查看私密歌单→404
     */
    PlaylistDetailVO getDetail(Long plid, Long currentUid, Integer currentRole, long page, long size);

    /**
     * 向歌单加入一首歌（幂等）。
     *
     * <p>仅 owner 或管理员可加。歌曲须「口径A 公开可见」，否则 404。
     * 若该歌已在歌单中，直接视为成功（依赖 {@code (plid,sid)} 唯一约束防重）。</p>
     *
     * @param uid  操作者 uid（服务端会话）
     * @param role 操作者角色
     * @param plid 歌单 plid
     * @param sid  歌曲 sid
     * @throws com.music.common.exception.BizException 歌单/歌曲不存在或不可见→404；非 owner 且非管理员→403
     */
    void addSong(Long uid, Integer role, Long plid, Long sid);

    /**
     * 从歌单移出一首歌（幂等）。
     *
     * <p>仅 owner 或管理员可移。不校验歌曲可见性：已下架的歌也允许从歌单移出。
     * 若该歌本就不在歌单中，删除影响 0 行，同样视为成功（幂等）。</p>
     *
     * @param uid  操作者 uid（服务端会话）
     * @param role 操作者角色
     * @param plid 歌单 plid
     * @param sid  歌曲 sid
     * @throws com.music.common.exception.BizException 歌单不存在→404；非 owner 且非管理员→403
     */
    void removeSong(Long uid, Integer role, Long plid, Long sid);
}
