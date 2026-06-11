package com.music.song.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.exception.BizException;
import com.music.common.result.PageVO;
import com.music.common.result.ResultCode;
import com.music.common.storage.StorageService;
import com.music.song.dto.AlbumCreateDTO;
import com.music.song.dto.AlbumUpdateDTO;
import com.music.song.dto.AlbumVO;
import com.music.song.dto.SongVO;
import com.music.song.entity.Album;
import com.music.song.entity.Song;
import com.music.song.mapper.AlbumMapper;
import com.music.song.mapper.SongMapper;
import com.music.song.service.AlbumService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 专辑业务实现。
 *
 * <p>归属校验统一走 {@link #checkOwnership}：管理员（role=2）越过，
 * 其余须 creatorUid 匹配。删除专辑为级联软删，加 {@code @Transactional}
 * 保证"删歌 + 删专辑"原子。</p>
 */
@Service
public class AlbumServiceImpl implements AlbumService {

    /** 角色：管理员（可越过归属校验）。 */
    private static final int ROLE_ADMIN = 2;

    /** 音频审核状态：通过（口径A 可见条件之一）。 */
    private static final int AUDIT_PASSED = 1;

    private final AlbumMapper albumMapper;
    private final SongMapper songMapper;
    private final com.music.common.storage.StorageService storageService;
    private final com.music.common.storage.DeferredStorageCleaner storageCleaner;

    /**
     * 构造器注入依赖。
     *
     * @param albumMapper    专辑数据访问
     * @param songMapper     歌曲数据访问（级联删歌、查专辑内曲目用）
     * @param storageService 对象存储（生成封面公开直链）
     * @param storageCleaner 延迟删除器（级联删专辑时在事务提交后物理删歌曲文件）
     */
    public AlbumServiceImpl(AlbumMapper albumMapper, SongMapper songMapper,
                            com.music.common.storage.StorageService storageService,
                            com.music.common.storage.DeferredStorageCleaner storageCleaner) {
        this.albumMapper = albumMapper;
        this.songMapper = songMapper;
        this.storageService = storageService;
        this.storageCleaner = storageCleaner;
    }
    /**
     * 新建普通专辑（is_default=false）。
     */
    @Override
    public Long create(Long creatorUid, AlbumCreateDTO dto) {
        Album album = new Album();
        album.setAlbumName(dto.getAlbumName());
        album.setCover(dto.getCover());
        album.setReleaseDate(dto.getReleaseDate());
        album.setIntroduction(dto.getIntroduction());
        album.setIsDefault(false);
        album.setCreatorUid(creatorUid);
        album.setIsDeleted(false);
        albumMapper.insert(album);
        return album.getAid();
    }

    /**
     * 公开专辑列表（未删）：可选按名模糊搜索，按 aid 倒序分页。
     */
    @Override
    public PageVO<AlbumVO> listPublic(String keyword, long page, long size) {
        var wrapper = Wrappers.<Album>lambdaQuery()
                .eq(Album::getIsDeleted, false)
                .like(keyword != null && !keyword.isBlank(), Album::getAlbumName, keyword)
                .orderByDesc(Album::getAid);
        IPage<Album> result = albumMapper.selectPage(new Page<>(page, size), wrapper);
        return toPageVO(result);
    }

    /**
     * 公开专辑详情（未删），不存在则 404。
     */
    @Override
    public AlbumVO getPublic(Long aid) {
        Album album = albumMapper.selectById(aid);
        if (album == null || Boolean.TRUE.equals(album.getIsDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND, "专辑不存在");
        }
        return toVO(album);
    }
    /**
     * 某专辑下"口径A可见"的歌曲（已审核 + 未删），按 sid 倒序。
     */
    @Override
    public List<SongVO> listPublicSongs(Long aid) {
        List<Song> songs = songMapper.selectList(Wrappers.<Song>lambdaQuery()
                .eq(Song::getAlbumAid, aid)
                .eq(Song::getAuditStatus, AUDIT_PASSED)
                .eq(Song::getIsDeleted, false)
                .orderByDesc(Song::getSid));
        return songs.stream().map(this::songToVO).toList();
    }

    /**
     * 我创建的专辑（本人 + 未删），按 aid 倒序分页。
     */
    @Override
    public PageVO<AlbumVO> listMine(Long creatorUid, long page, long size) {
        var wrapper = Wrappers.<Album>lambdaQuery()
                .eq(Album::getCreatorUid, creatorUid)
                .eq(Album::getIsDeleted, false)
                .orderByDesc(Album::getAid);
        IPage<Album> result = albumMapper.selectPage(new Page<>(page, size), wrapper);
        return toPageVO(result);
    }

    /**
     * 修改专辑信息：校验归属 + 禁止改缺省专辑，随后更新。
     */
    @Override
    public void update(Long operatorUid, Integer operatorRole, Long aid, AlbumUpdateDTO dto) {
        Album album = getExisting(aid);
        checkOwnership(album, operatorUid, operatorRole);
        // 缺省专辑由系统托管（随单曲生成/清理），不允许手动改
        if (Boolean.TRUE.equals(album.getIsDefault())) {
            throw new BizException("缺省专辑由系统托管，不可修改");
        }
        album.setAlbumName(dto.getAlbumName());
        album.setCover(dto.getCover());
        album.setReleaseDate(dto.getReleaseDate());
        album.setIntroduction(dto.getIntroduction());
        albumMapper.updateById(album);
    }
    /**
     * 删除专辑（级联软删）：先软删专辑内所有未删歌曲，再软删专辑本身。
     * 事务保证两步原子；非空专辑由本接口统一级联，不另设"非空禁删"。
     */
    @Override
    @Transactional
    public void delete(Long operatorUid, Integer operatorRole, Long aid) {
        Album album = getExisting(aid);
        checkOwnership(album, operatorUid, operatorRole);
        // 先查出专辑内未删歌曲，留存其文件 key(用于 DB 落定后物理删除)
        List<Song> songs = songMapper.selectList(Wrappers.<Song>lambdaQuery()
                .eq(Song::getAlbumAid, aid)
                .eq(Song::getIsDeleted, false));
        // 第一步：软删该专辑下所有未删歌曲
        Song songFlag = new Song();
        songFlag.setIsDeleted(true);
        songMapper.update(songFlag, Wrappers.<Song>lambdaUpdate()
                .eq(Song::getAlbumAid, aid)
                .eq(Song::getIsDeleted, false));
        // 第二步：软删专辑本身
        album.setIsDeleted(true);
        albumMapper.updateById(album);
        // 注册提交后删除各歌曲文件：事务成功提交才删，回滚则全部保留，杜绝悬空记录指向空文件
        for (Song s : songs) {
            storageCleaner.deleteAfterCommit(StorageService.BucketType.AUDIO, s.getAudioPath());
            storageCleaner.deleteAfterCommit(StorageService.BucketType.COVER, s.getCover());
        }
    }

    /**
     * 取未删专辑实体，不存在或已删则 404。
     */
    @Override
    public Album getExisting(Long aid) {
        Album album = albumMapper.selectById(aid);
        if (album == null || Boolean.TRUE.equals(album.getIsDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND, "专辑不存在");
        }
        return album;
    }

    /**
     * 归属校验：管理员越过；其余须为本人创建，否则 403。
     *
     * @param album        目标专辑
     * @param operatorUid  操作者 uid
     * @param operatorRole 操作者角色
     */
    private void checkOwnership(Album album, Long operatorUid, Integer operatorRole) {
        if (operatorRole != null && operatorRole == ROLE_ADMIN) {
            return;
        }
        if (!album.getCreatorUid().equals(operatorUid)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作自己创建的专辑");
        }
    }

    /**
     * 把 MyBatis-Plus 分页结果转为对外的 {@link PageVO}（实体投影为 AlbumVO）。
     *
     * @param result 分页查询结果
     * @return 分页 VO
     */
    private PageVO<AlbumVO> toPageVO(IPage<Album> result) {
        List<AlbumVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 专辑实体转 VO，封面 key 替换为公开直链。
     *
     * @param album 专辑实体
     * @return 专辑 VO
     */
    private AlbumVO toVO(Album album) {
        AlbumVO vo = AlbumVO.from(album);
        vo.setCover(storageService.publicUrl(album.getCover()));
        return vo;
    }

    /**
     * 歌曲实体转列表 VO，封面 key 替换为公开直链。
     *
     * @param song 歌曲实体
     * @return 歌曲列表 VO
     */
    private SongVO songToVO(Song song) {
        SongVO vo = SongVO.from(song);
        vo.setCover(storageService.publicUrl(song.getCover()));
        return vo;
    }
}
