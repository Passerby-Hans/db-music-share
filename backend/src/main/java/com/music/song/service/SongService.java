package com.music.song.service;

import com.music.common.result.PageVO;
import com.music.song.dto.SongAuditDTO;
import com.music.song.dto.SongDetailVO;
import com.music.song.dto.SongMoveDTO;
import com.music.song.dto.SongUpdateDTO;
import com.music.song.dto.SongUploadDTO;
import com.music.song.dto.SongVO;

/**
 * 歌曲业务接口。
 *
 * <p>承载歌曲上传（三种专辑模式）、公开/我的查询、修改、移动专辑、软删。
 * 权限边界：上传/管理类操作要求 role≥1（Controller 把关），且只能动
 * 自己上传的歌（uploaderUid 匹配，本层校验），管理员可动所有。</p>
 */
public interface SongService {

    /**
     * 上传歌曲（待审 auditStatus=0）。专辑归属三选一：
     * 指定已有专辑 / 当场新建专辑 / 自动生成独立缺省专辑。
     *
     * @param uploaderUid 上传者 uid（当前登录用户）
     * @param dto         上传参数
     * @return 新建歌曲的 sid
     */
    Long upload(Long uploaderUid, SongUploadDTO dto);

    /**
     * 公开歌曲列表（口径A：已审核 + 未删），支持按标题模糊搜索 + 排序 + 分页。
     *
     * @param keyword 标题关键词，可空
     * @param sort    排序:play_count(热度)/create_time(最新)/其它或空=默认 sid 发布序;desc 固定
     * @param page    页码（从 1 起）
     * @param size    每页条数
     * @return 分页歌曲列表
     */
    PageVO<SongVO> listPublic(String keyword, String sort, long page, long size);

    /**
     * 公开歌曲详情（口径A：已审核 + 未删）。
     *
     * @param sid 歌曲 sid
     * @return 歌曲详情 VO
     */
    SongDetailVO getPublic(Long sid);

    /**
     * 我的上传（口径B：本人 + 未删，任意审核态），支持分页。
     *
     * @param uploaderUid 当前登录用户 uid
     * @param page        页码（从 1 起）
     * @param size        每页条数
     * @return 分页歌曲列表
     */
    PageVO<SongVO> listMine(Long uploaderUid, long page, long size);

    /**
     * 修改歌曲元信息。校验归属后更新；上传者本人改后回到待审，管理员改不重置。
     *
     * @param operatorUid  操作者 uid
     * @param operatorRole 操作者角色（2 管理员越过归属校验且不重置审核态）
     * @param sid          歌曲 sid
     * @param dto          修改参数
     */
    void update(Long operatorUid, Integer operatorRole, Long sid, SongUpdateDTO dto);

    /**
     * 移动歌曲到另一专辑。校验歌曲与目标专辑归属后改其 albumAid；
     * 若源专辑为空的缺省专辑则一并软删。
     *
     * @param operatorUid  操作者 uid
     * @param operatorRole 操作者角色（2 管理员越过归属校验）
     * @param sid          歌曲 sid
     * @param dto          移动参数（目标专辑）
     */
    void move(Long operatorUid, Integer operatorRole, Long sid, SongMoveDTO dto);

    /**
     * 软删除歌曲。校验归属后置 isDeleted=true。
     *
     * @param operatorUid  操作者 uid
     * @param operatorRole 操作者角色（2 管理员越过归属校验）
     * @param sid          歌曲 sid
     */
    void delete(Long operatorUid, Integer operatorRole, Long sid);

    /**
     * 取歌曲的音频播放地址（口径A可见才返回）：生成限时预签名 URL。
     *
     * @param sid 歌曲 sid
     * @return 限时可访问的音频 URL
     */
    String getPlayUrl(Long sid);

    /**
     * 待审歌曲列表（管理员用，口径：auditStatus=0 且未删），按 sid 倒序分页。
     *
     * @param page 页码（从 1 起）
     * @param size 每页条数
     * @return 分页待审歌曲列表
     */
    PageVO<SongVO> listPending(long page, long size);

    /**
     * 审核歌曲（管理员用）。仅允许对「待审」（auditStatus=0）歌曲操作：
     * 通过则置 auditStatus=1 并清空理由；驳回则置 auditStatus=2 并记录理由。
     *
     * @param sid 歌曲 sid
     * @param dto 审核参数（pass 必填；驳回时 remark 必填）
     * @throws com.music.common.exception.BizException 歌曲不存在/已删（404）、
     *         已审核过（409）、或驳回未填理由（400）
     */
    void audit(Long sid, SongAuditDTO dto);
}
