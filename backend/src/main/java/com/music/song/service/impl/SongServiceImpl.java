package com.music.song.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.exception.BizException;
import com.music.common.result.PageVO;
import com.music.common.result.ResultCode;
import com.music.song.dto.SongDetailVO;
import com.music.song.dto.SongMoveDTO;
import com.music.song.dto.SongUpdateDTO;
import com.music.song.dto.SongUploadDTO;
import com.music.song.dto.SongVO;
import com.music.song.entity.Album;
import com.music.song.entity.Song;
import com.music.song.mapper.AlbumMapper;
import com.music.song.mapper.SongMapper;
import com.music.song.service.SongService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 歌曲业务实现。
 *
 * <p>上传支持三种专辑模式（见 {@link #upload}）；查询区分口径A（公开）
 * 与口径B（我的上传）；改/移动/删均先做归属校验（管理员越过）。
 * 移动可能触发空缺省专辑清理，故加 {@code @Transactional}。</p>
 */
@Service
public class SongServiceImpl implements SongService {

    /** 角色：管理员（越过归属校验、改歌不重置审核态）。 */
    private static final int ROLE_ADMIN = 2;

    /** 审核状态：待审（上传默认、上传者改后回退）。 */
    private static final int AUDIT_PENDING = 0;

    /** 审核状态：通过（口径A 可见条件之一）。 */
    private static final int AUDIT_PASSED = 1;

    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;

    /**
     * 构造器注入依赖。
     *
     * @param songMapper  歌曲数据访问
     * @param albumMapper 专辑数据访问（校验/新建/清理专辑用）
     */
    public SongServiceImpl(SongMapper songMapper, AlbumMapper albumMapper) {
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
    }
    /**
     * 上传歌曲（待审）。先按三模式确定/创建专辑，再落库歌曲。
     * 含建专辑时加事务，保证"建专辑 + 建歌"原子。
     */
    @Override
    @Transactional
    public Long upload(Long uploaderUid, SongUploadDTO dto) {
        boolean hasAid = dto.getAlbumAid() != null;
        boolean hasNewName = dto.getNewAlbumName() != null && !dto.getNewAlbumName().isBlank();
        // 两种专辑模式互斥，同时给出视为非法
        if (hasAid && hasNewName) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能同时指定已有专辑和新建专辑");
        }
        Long albumAid;
        if (hasAid) {
            // 模式①：放入已有专辑，校验存在、未删、且为本人创建
            Album album = albumMapper.selectById(dto.getAlbumAid());
            if (album == null || Boolean.TRUE.equals(album.getIsDeleted())) {
                throw new BizException(ResultCode.NOT_FOUND, "目标专辑不存在");
            }
            if (!album.getCreatorUid().equals(uploaderUid)) {
                throw new BizException(ResultCode.FORBIDDEN, "不能向他人的专辑上传歌曲");
            }
            albumAid = album.getAid();
        } else if (hasNewName) {
            // 模式②：当场新建普通专辑
            albumAid = createAlbum(uploaderUid, dto.getNewAlbumName(), false);
        } else {
            // 模式③：自动生成独立缺省专辑，专辑名取歌名
            albumAid = createAlbum(uploaderUid, dto.getTitle(), true);
        }
        // 落库歌曲：待审、未删、点唱数 0
        Song song = new Song();
        song.setTitle(dto.getTitle());
        song.setCover(dto.getCover());
        song.setDuration(dto.getDuration());
        song.setLyric(dto.getLyric());
        song.setAudioPath(dto.getAudioPath());
        song.setPlayCount(0L);
        song.setAlbumAid(albumAid);
        song.setUploaderUid(uploaderUid);
        song.setAuditStatus(AUDIT_PENDING);
        song.setIsDeleted(false);
        songMapper.insert(song);
        return song.getSid();
    }
    /**
     * 公开歌曲列表（口径A：已审核 + 未删），可选标题模糊搜索，按 sid 倒序分页。
     */
    @Override
    public PageVO<SongVO> listPublic(String keyword, long page, long size) {
        var wrapper = Wrappers.<Song>lambdaQuery()
                .eq(Song::getAuditStatus, AUDIT_PASSED)
                .eq(Song::getIsDeleted, false)
                .like(keyword != null && !keyword.isBlank(), Song::getTitle, keyword)
                .orderByDesc(Song::getSid);
        IPage<Song> result = songMapper.selectPage(new Page<>(page, size), wrapper);
        return toPageVO(result);
    }

    /**
     * 公开歌曲详情（口径A：已审核 + 未删），不满足则 404。
     */
    @Override
    public SongDetailVO getPublic(Long sid) {
        Song song = songMapper.selectById(sid);
        if (song == null
                || Boolean.TRUE.equals(song.getIsDeleted())
                || song.getAuditStatus() == null
                || song.getAuditStatus() != AUDIT_PASSED) {
            throw new BizException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        return SongDetailVO.from(song);
    }

    /**
     * 我的上传（口径B：本人 + 未删，任意审核态），按 sid 倒序分页。
     */
    @Override
    public PageVO<SongVO> listMine(Long uploaderUid, long page, long size) {
        var wrapper = Wrappers.<Song>lambdaQuery()
                .eq(Song::getUploaderUid, uploaderUid)
                .eq(Song::getIsDeleted, false)
                .orderByDesc(Song::getSid);
        IPage<Song> result = songMapper.selectPage(new Page<>(page, size), wrapper);
        return toPageVO(result);
    }
    /**
     * 修改歌曲元信息：校验归属后更新。上传者本人改后审核态回退为待审，
     * 管理员改不重置（可信）。
     */
    @Override
    public void update(Long operatorUid, Integer operatorRole, Long sid, SongUpdateDTO dto) {
        Song song = getOwnedSong(sid, operatorUid, operatorRole);
        song.setTitle(dto.getTitle());
        song.setCover(dto.getCover());
        song.setDuration(dto.getDuration());
        song.setLyric(dto.getLyric());
        // 上传者改内容后回到待审，防止"过审后偷改"；管理员可信，不重置
        if (operatorRole == null || operatorRole != ROLE_ADMIN) {
            song.setAuditStatus(AUDIT_PENDING);
            song.setAuditRemark(null);
        }
        songMapper.updateById(song);
    }

    /**
     * 移动歌曲到另一专辑：校验歌曲归属与目标专辑（存在/未删/本人，管理员越过），
     * 改其 albumAid；若源专辑为空的缺省专辑则一并软删。事务保证原子。
     */
    @Override
    @Transactional
    public void move(Long operatorUid, Integer operatorRole, Long sid, SongMoveDTO dto) {
        Song song = getOwnedSong(sid, operatorUid, operatorRole);
        Long sourceAid = song.getAlbumAid();
        Long targetAid = dto.getTargetAlbumAid();
        if (targetAid.equals(sourceAid)) {
            throw new BizException("歌曲已在该专辑中");
        }
        // 校验目标专辑：存在、未删、且本人创建（管理员越过）
        Album target = albumMapper.selectById(targetAid);
        if (target == null || Boolean.TRUE.equals(target.getIsDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND, "目标专辑不存在");
        }
        if ((operatorRole == null || operatorRole != ROLE_ADMIN)
                && !target.getCreatorUid().equals(operatorUid)) {
            throw new BizException(ResultCode.FORBIDDEN, "不能移动到他人的专辑");
        }
        song.setAlbumAid(targetAid);
        songMapper.updateById(song);
        cleanupEmptyDefaultAlbum(sourceAid);
    }
    /**
     * 软删除歌曲：校验归属后置 isDeleted=true；若源专辑为空缺省专辑则一并清理。
     */
    @Override
    @Transactional
    public void delete(Long operatorUid, Integer operatorRole, Long sid) {
        Song song = getOwnedSong(sid, operatorUid, operatorRole);
        Long sourceAid = song.getAlbumAid();
        song.setIsDeleted(true);
        songMapper.updateById(song);
        cleanupEmptyDefaultAlbum(sourceAid);
    }

    /**
     * 取未删歌曲并做归属校验：管理员越过，其余须为本人上传，否则 403/404。
     *
     * @param sid          歌曲 sid
     * @param operatorUid  操作者 uid
     * @param operatorRole 操作者角色
     * @return 通过校验的歌曲实体
     */
    private Song getOwnedSong(Long sid, Long operatorUid, Integer operatorRole) {
        Song song = songMapper.selectById(sid);
        if (song == null || Boolean.TRUE.equals(song.getIsDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        if ((operatorRole == null || operatorRole != ROLE_ADMIN)
                && !song.getUploaderUid().equals(operatorUid)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作自己上传的歌曲");
        }
        return song;
    }

    /**
     * 新建专辑（供上传时模式②/③复用）。
     *
     * @param creatorUid 创建者 uid
     * @param name       专辑名
     * @param isDefault  是否缺省专辑
     * @return 新专辑 aid
     */
    private Long createAlbum(Long creatorUid, String name, boolean isDefault) {
        Album album = new Album();
        album.setAlbumName(name);
        album.setIsDefault(isDefault);
        album.setCreatorUid(creatorUid);
        album.setIsDeleted(false);
        albumMapper.insert(album);
        return album.getAid();
    }
    /**
     * 清理空的缺省专辑：缺省专辑为单曲而生，移走/删掉其内唯一歌曲后即失去意义。
     * 仅当专辑存在、未删、is_default=true 且其下已无未删歌曲时，软删该专辑。
     * 普通专辑空了不处理（用户可能想保留）。
     *
     * @param aid 待检查的源专辑 aid
     */
    private void cleanupEmptyDefaultAlbum(Long aid) {
        Album album = albumMapper.selectById(aid);
        if (album == null
                || Boolean.TRUE.equals(album.getIsDeleted())
                || !Boolean.TRUE.equals(album.getIsDefault())) {
            return;
        }
        Long remaining = songMapper.selectCount(Wrappers.<Song>lambdaQuery()
                .eq(Song::getAlbumAid, aid)
                .eq(Song::getIsDeleted, false));
        if (remaining == null || remaining == 0) {
            album.setIsDeleted(true);
            albumMapper.updateById(album);
        }
    }

    /**
     * 把 MyBatis-Plus 分页结果转为对外的 {@link PageVO}（实体投影为 SongVO）。
     *
     * @param result 分页查询结果
     * @return 分页 VO
     */
    private PageVO<SongVO> toPageVO(IPage<Song> result) {
        List<SongVO> records = result.getRecords().stream().map(SongVO::from).toList();
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }
}
