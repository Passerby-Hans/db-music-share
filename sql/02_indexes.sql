-- =====================================================================
-- 在线音乐分享系统 — 物理设计 / 索引脚本 (02_indexes.sql)
-- DBMS : PostgreSQL 14+
-- 依赖 : 先执行 01_schema.sql (含 CREATE EXTENSION pg_trgm)
--
-- 索引类型说明：
--   B-tree(默认)  : 等值/范围/排序/IN；不支持 LIKE '%x%' 前置通配
--   复合 B-tree    : 遵循最左前缀，(a,b) 服务 a= 及 a= AND b=，不服务单独 b=
--   GIN + pg_trgm  : 支持子串模糊 LIKE '%关键词%'，倒排三元组，空间大写入慢
--   唯一索引       : UNIQUE 约束已自动创建，勿重复建；只服务其最左前缀
--   部分索引(WHERE): 只索引数据子集(如已通过/未删除)，更小更快
-- =====================================================================

-- --------------------------------------------------------------------
-- 1. 外键列索引（PG 不会为外键自动建索引，全部手动补）
--    作用：加速 JOIN 与父表删除时的级联检查/清理
-- --------------------------------------------------------------------
-- album.creator_uid -> app_user
CREATE INDEX idx_album_creator    ON album(creator_uid);

-- song 的两个外键
CREATE INDEX idx_song_album       ON song(album_aid);     -- 专辑详情列曲目
CREATE INDEX idx_song_uploader    ON song(uploader_uid);  -- 某上传者的作品/贡献统计

-- comment 的外键（uid、sid、自引用 parent_cid）
CREATE INDEX idx_comment_uid      ON comment(uid);
CREATE INDEX idx_comment_sid      ON comment(sid);         -- 某首歌的评论列表
CREATE INDEX idx_comment_parent   ON comment(parent_cid);  -- 楼中楼回复

-- playlist.uid
CREATE INDEX idx_playlist_uid     ON playlist(uid);        -- 某用户的歌单

-- --------------------------------------------------------------------
-- 2. 点唱记录 play_record：外键 + 排行榜复合索引
-- --------------------------------------------------------------------
CREATE INDEX idx_play_uid         ON play_record(uid);     -- 用户活跃度统计、按用户查
CREATE INDEX idx_play_sid         ON play_record(sid);     -- 外键;某首歌的点唱明细、级联删除
-- 排行榜核心：日/周榜 WHERE play_time BETWEEN ? AND ? GROUP BY sid
-- 复合 (play_time, sid)：play_time 走范围扫描，sid 已在索引内，聚合时少回表
CREATE INDEX idx_play_time_sid    ON play_record(play_time, sid);

-- --------------------------------------------------------------------
-- 3. 联结表右侧列 sid（左侧列已被 UNIQUE 约束的索引覆盖最左前缀）
--    UNIQUE(uid,sid)/(plid,sid) 只服务最左列查询，单独按 sid 查需补索引
-- --------------------------------------------------------------------
CREATE INDEX idx_rating_sid       ON rating(sid);          -- 某首歌的平均分/评分数
CREATE INDEX idx_favorite_sid     ON favorite(sid);        -- 某首歌被多少人收藏
CREATE INDEX idx_pldetail_sid     ON playlist_detail(sid); -- 某首歌在哪些歌单

-- --------------------------------------------------------------------
-- 4. 模糊搜索 GIN + pg_trgm（支持 LIKE '%关键词%' 走索引）
--    需求：按音乐名、专辑名搜索。普通 B-tree 对前置通配 '%x' 无效。
--    gin_trgm_ops 把字符串拆成三字母组合建倒排，命中子串也能用索引。
-- --------------------------------------------------------------------
CREATE INDEX idx_song_title_trgm  ON song  USING gin (title      gin_trgm_ops);
CREATE INDEX idx_album_name_trgm  ON album USING gin (album_name gin_trgm_ops);

-- --------------------------------------------------------------------
-- 5. 部分索引（只索引高频访问的数据子集，更小更快）
--    前台列表恒定只看「已通过审核且未软删除」的音乐，
--    把过滤条件写进 WHERE，索引体积远小于全表索引。
-- --------------------------------------------------------------------
-- 前台可见音乐：按上传时间排序的列表/分页
CREATE INDEX idx_song_public_time ON song(create_time DESC)
    WHERE audit_status = 1 AND is_deleted = FALSE;

-- 管理员审核队列：只索引「待审」音乐
CREATE INDEX idx_song_pending     ON song(create_time)
    WHERE audit_status = 0;

-- =====================================================================
-- 说明：以下索引已由约束自动创建，无需也不应重复建立——
--   主键：各表 PRIMARY KEY
--   唯一：app_user(username)/(email)、rating(uid,sid)、
--         favorite(uid,sid)、playlist_detail(plid,sid)
--   其中复合 UNIQUE(uid,sid) 已覆盖按 uid（最左前缀）的查询，
--   故上文只为右侧的 sid 单独补索引。
-- =====================================================================


