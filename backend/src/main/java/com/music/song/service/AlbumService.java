package com.music.song.service;

import com.music.common.result.PageVO;
import com.music.song.dto.AlbumCreateDTO;
import com.music.song.dto.AlbumUpdateDTO;
import com.music.song.dto.AlbumVO;
import com.music.song.dto.SongVO;
import com.music.song.entity.Album;

import java.util.List;

/**
 * 专辑业务接口。
 *
 * <p>承载专辑的增删改查与级联删除。权限边界由 Controller 的
 * {@code @RequireRole} 与本层的 owner 校验共同保证：上传者只能动
 * 自己创建的专辑（creatorUid 匹配），管理员可动所有。</p>
 */
public interface AlbumService {

    /**
     * 新建普通专辑。
     *
     * @param creatorUid 创建者 uid（当前登录用户）
     * @param dto        专辑参数
     * @return 新建专辑的 aid
     */
    Long create(Long creatorUid, AlbumCreateDTO dto);

    /**
     * 公开专辑列表（口径A：未删），支持按名模糊搜索 + 排序 + 分页。
     *
     * @param keyword 专辑名关键词，可空
     * @param sort    排序:release_date(发行)/其它或空=默认 aid 创建序;desc 固定
     * @param page    页码（从 1 起）
     * @param size    每页条数
     * @return 分页专辑列表
     */
    PageVO<AlbumVO> listPublic(String keyword, String sort, long page, long size);

    /**
     * 公开专辑详情（口径A：未删）。
     *
     * @param aid 专辑 aid
     * @return 专辑 VO
     */
    AlbumVO getPublic(Long aid);

    /**
     * 某专辑下"口径A可见"的歌曲列表（已审核 + 未删）。
     *
     * @param aid 专辑 aid
     * @return 歌曲列表项
     */
    List<SongVO> listPublicSongs(Long aid);

    /**
     * 我创建的专辑（口径B：本人 + 未删）。
     *
     * @param creatorUid 当前登录用户 uid
     * @param page       页码（从 1 起）
     * @param size       每页条数
     * @return 分页专辑列表
     */
    PageVO<AlbumVO> listMine(Long creatorUid, long page, long size);

    /**
     * 修改专辑信息。校验归属与"非缺省专辑"后更新。
     *
     * @param operatorUid  操作者 uid
     * @param operatorRole 操作者角色（2 管理员越过归属校验）
     * @param aid          专辑 aid
     * @param dto          修改参数
     */
    void update(Long operatorUid, Integer operatorRole, Long aid, AlbumUpdateDTO dto);

    /**
     * 删除专辑（级联软删）：先软删专辑内所有歌，再软删专辑本身，事务保证原子。
     *
     * @param operatorUid  操作者 uid
     * @param operatorRole 操作者角色（2 管理员越过归属校验）
     * @param aid          专辑 aid
     */
    void delete(Long operatorUid, Integer operatorRole, Long aid);

    /**
     * 取一个未删专辑实体，校验存在性。
     *
     * @param aid 专辑 aid
     * @return 专辑实体
     */
    Album getExisting(Long aid);
}
