-- =====================================================================
-- 在线音乐分享系统 — 物理设计 / 测试数据 (03_seed.sql)
-- DBMS : PostgreSQL 14+
-- 依赖 : 先执行 01_schema.sql，再执行 02_indexes.sql
-- 说明 :
--   1. 主键显式写入(IDENTITY 用 BY DEFAULT，允许显式 ID 以维护外键引用)
--   2. play_time 用相对时间(CURRENT_TIMESTAMP - INTERVAL)，
--      保证任何时刻执行，日榜/周榜/总榜都有正确落点的数据
--   3. password 为 bcrypt 密文：所有测试账号明文密码均为 123456
--      （密文由 bcrypt cost=10 生成，Spring BCryptPasswordEncoder 可校验）
--   4. 末尾附验证查询，跑完可直接观察排行榜/统计效果
-- 执行后需校正 IDENTITY 序列(见文件末尾)，否则后续自增会冲突
-- =====================================================================

-- 清空(按依赖逆序)，便于重复执行本脚本
TRUNCATE comment_like, favorite, playlist_detail, playlist, rating, comment,
         play_record, song, album, app_user RESTART IDENTITY CASCADE;

-- --------------------------------------------------------------------
-- 1. 用户 app_user（10 个：1 管理员 / 3 上传者 / 6 普通用户，含 1 封禁、1 软删除）
--    role: 0普通 1上传者 2管理员 | status: 0正常 1封禁
-- --------------------------------------------------------------------
INSERT INTO app_user (uid, username, password, nickname, email, avatar, role, status, is_deleted) VALUES
(1,  'admin',    '$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '系统管理员', 'admin@music.com',  '/avatar/1.png', 2, 0, FALSE),
(2,  'uploader_jay',  '$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '周董',     'jay@music.com',    '/avatar/2.png', 1, 0, FALSE),
(3,  'uploader_eason','$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '陈奕迅',   'eason@music.com',  '/avatar/3.png', 1, 0, FALSE),
(4,  'uploader_tyler','$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '泰勒',     'tyler@music.com',  '/avatar/4.png', 1, 0, FALSE),
(5,  'alice',    '$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '爱丽丝',   'alice@mail.com',   '/avatar/5.png', 0, 0, FALSE),
(6,  'bob',      '$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '小明',     'bob@mail.com',     '/avatar/6.png', 0, 0, FALSE),
(7,  'carol',    '$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '卡罗尔',   'carol@mail.com',   '/avatar/7.png', 0, 0, FALSE),
(8,  'david',    '$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '大卫',     'david@mail.com',   '/avatar/8.png', 0, 0, FALSE),
(9,  'banned_user','$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe','被封号的人','banned@mail.com', '/avatar/9.png', 0, 1, FALSE),
(10, 'ghost',    '$2b$10$5jLFvMMHsuEjRj.WZn6Oa.zHFTMEHDaZa8RE1VqOU0wxKDOYLA3qe', '已注销用户','ghost@mail.com',  '/avatar/10.png',0, 0, TRUE);

-- --------------------------------------------------------------------
-- 2. 专辑 album（8 个：5 个普通专辑 + 3 个单曲缺省专辑 is_default=true）
--    决策①：每首单曲拥有独立缺省专辑，互不共用
-- --------------------------------------------------------------------
INSERT INTO album (aid, album_name, cover, release_date, introduction, is_default, creator_uid) VALUES
(1, '范特西',        '/cover/album1.jpg', '2001-09-14', '周杰伦第二张录音室专辑',       FALSE, 2),
(2, '叶惠美',        '/cover/album2.jpg', '2003-07-31', '周杰伦第四张专辑，含《晴天》', FALSE, 2),
(3, 'U87',           '/cover/album3.jpg', '2005-09-13', '陈奕迅国语专辑',               FALSE, 3),
(4, '1989',          '/cover/album4.jpg', '2014-10-27', 'Taylor Swift 流行转型之作',    FALSE, 4),
(5, 'Red',           '/cover/album5.jpg', '2012-10-22', 'Taylor Swift 第四张专辑',      FALSE, 4),
-- 以下为单曲自动生成的独立缺省专辑（每个只含一首单曲）
(6, '【单曲】告白气球', NULL, NULL, '系统为单曲自动生成的缺省专辑', TRUE, 2),
(7, '【单曲】十年',     NULL, NULL, '系统为单曲自动生成的缺省专辑', TRUE, 3),
(8, '【单曲】Lover',    NULL, NULL, '系统为单曲自动生成的缺省专辑', TRUE, 4);

-- --------------------------------------------------------------------
-- 3. 音乐 song（12 首）
--    audit_status: 0待审 1通过 2驳回 | 覆盖三种状态、含 1 首软删除
--    play_count 为冗余总点唱数，应与 play_record 实际行数对账(见末尾验证)
-- --------------------------------------------------------------------
INSERT INTO song (sid, title, cover, duration, lyric, audio_path, play_count, album_aid, uploader_uid, audit_status, audit_remark, is_deleted) VALUES
-- 已通过(audit_status=1)的主力曲目
(1,  '简单爱',   '/cover/s1.jpg',  269, '说不上为什么 我变得很主动...', '/audio/s1.mp3',  0, 1, 2, 1, NULL, FALSE),
(2,  '晴天',     '/cover/s2.jpg',  269, '故事的小黄花 从出生那年就飘着...', '/audio/s2.mp3', 0, 2, 2, 1, NULL, FALSE),
(3,  '稻香',     '/cover/s3.jpg',  223, '对这个世界如果你有太多的抱怨...', '/audio/s3.mp3', 0, 2, 2, 1, NULL, FALSE),
(4,  '浮夸',     '/cover/s4.jpg',  287, '有人问我 我就会讲...', '/audio/s4.mp3',  0, 3, 3, 1, NULL, FALSE),
(5,  'Shake It Off','/cover/s5.jpg',219,'I stay out too late...', '/audio/s5.mp3', 0, 4, 4, 1, NULL, FALSE),
(6,  'Blank Space','/cover/s6.jpg', 231, 'Nice to meet you, where you been...', '/audio/s6.mp3', 0, 4, 4, 1, NULL, FALSE),
(7,  '告白气球', '/cover/s7.jpg',  215, '塞纳河畔 左岸的咖啡...', '/audio/s7.mp3',  0, 6, 2, 1, NULL, FALSE),
(8,  '十年',     '/cover/s8.jpg',  205, '如果那两个字没有颤抖...', '/audio/s8.mp3',  0, 7, 3, 1, NULL, FALSE),
-- 待审核(audit_status=0)：前台不可见，出现在管理员审核队列
(9,  'All Too Well','/cover/s9.jpg',329,'I walked through the door with you...', '/audio/s9.mp3', 0, 5, 4, 0, NULL, FALSE),
(10, 'Lover',    '/cover/s10.jpg', 221, 'We could leave the Christmas lights up...', '/audio/s10.mp3', 0, 8, 4, 0, NULL, FALSE),
-- 被驳回(audit_status=2)：带驳回理由
(11, '测试音质太差的歌', NULL, 180, '...', '/audio/s11.mp3', 0, 1, 2, 2, '音频码率过低，请重新上传 ≥128kbps 版本', FALSE),
-- 软删除(is_deleted=true)：逻辑删除，保留历史统计
(12, '已下架的歌', '/cover/s12.jpg', 200, '...', '/audio/s12.mp3', 0, 3, 3, 1, NULL, TRUE);

-- --------------------------------------------------------------------
-- 4. 点唱记录 play_record（约 40 条，相对时间分三段）
--    时间窗设计(任何时刻执行都成立)：
--      · 今天内 (< 1 天)         → 进日榜、周榜、总榜
--      · 本周内 (1~6 天前)       → 进周榜、总榜，不进日榜
--      · 更早   (8~40 天前)      → 只进总榜
--    热度梯度(便于看出排行)：
--      今日热门 晴天(2) > 告白气球(7) > 简单爱(1)；
--      本周 浮夸(4)、Shake It Off(5) 强势；总榜 晴天 稳居第一
-- --------------------------------------------------------------------
-- ===== 今天内的点唱(进日榜) =====
INSERT INTO play_record (uid, sid, play_time) VALUES
(5, 2, CURRENT_TIMESTAMP - INTERVAL '1 hour'),
(6, 2, CURRENT_TIMESTAMP - INTERVAL '2 hours'),
(7, 2, CURRENT_TIMESTAMP - INTERVAL '3 hours'),
(8, 2, CURRENT_TIMESTAMP - INTERVAL '5 hours'),
(5, 7, CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
(6, 7, CURRENT_TIMESTAMP - INTERVAL '90 minutes'),
(7, 7, CURRENT_TIMESTAMP - INTERVAL '4 hours'),
(5, 1, CURRENT_TIMESTAMP - INTERVAL '6 hours'),
(6, 1, CURRENT_TIMESTAMP - INTERVAL '7 hours'),
(8, 3, CURRENT_TIMESTAMP - INTERVAL '8 hours'),
(7, 5, CURRENT_TIMESTAMP - INTERVAL '10 hours'),
(8, 8, CURRENT_TIMESTAMP - INTERVAL '11 hours');

-- ===== 本周内、非今天(进周榜，不进日榜) =====
INSERT INTO play_record (uid, sid, play_time) VALUES
(5, 4, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(6, 4, CURRENT_TIMESTAMP - INTERVAL '2 days 3 hours'),
(7, 4, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(8, 4, CURRENT_TIMESTAMP - INTERVAL '3 days 6 hours'),
(5, 5, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(6, 5, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(7, 5, CURRENT_TIMESTAMP - INTERVAL '5 days'),
(5, 2, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(6, 2, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(8, 6, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(7, 6, CURRENT_TIMESTAMP - INTERVAL '5 days'),
(8, 1, CURRENT_TIMESTAMP - INTERVAL '6 days');

-- ===== 更早(8~40 天前，只进总榜) =====
INSERT INTO play_record (uid, sid, play_time) VALUES
(5, 2, CURRENT_TIMESTAMP - INTERVAL '10 days'),
(6, 2, CURRENT_TIMESTAMP - INTERVAL '15 days'),
(7, 2, CURRENT_TIMESTAMP - INTERVAL '20 days'),
(8, 2, CURRENT_TIMESTAMP - INTERVAL '25 days'),
(5, 3, CURRENT_TIMESTAMP - INTERVAL '12 days'),
(6, 3, CURRENT_TIMESTAMP - INTERVAL '18 days'),
(7, 8, CURRENT_TIMESTAMP - INTERVAL '14 days'),
(8, 8, CURRENT_TIMESTAMP - INTERVAL '22 days'),
(5, 1, CURRENT_TIMESTAMP - INTERVAL '30 days'),
(6, 7, CURRENT_TIMESTAMP - INTERVAL '9 days'),
(7, 4, CURRENT_TIMESTAMP - INTERVAL '35 days'),
(9, 2, CURRENT_TIMESTAMP - INTERVAL '40 days');

-- --------------------------------------------------------------------
-- 5. 评论 comment（12 条，含楼中楼回复；评论本身不含评分）
--    parent_cid 为空=主楼评论，非空=对某条评论的回复
-- --------------------------------------------------------------------
INSERT INTO comment (cid, uid, sid, content, like_count, parent_cid, create_time) VALUES
(1, 5, 2, '《晴天》永远的神，青春回忆！',        128, NULL, CURRENT_TIMESTAMP - INTERVAL '5 days'),
(2, 6, 2, '前奏一响，DNA 动了',                   86,  NULL, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(3, 7, 2, '同感，每年听都有不同的感受',           12,  1,    CURRENT_TIMESTAMP - INTERVAL '4 days 2 hours'),
(4, 8, 2, '楼上说得对，经典就是经典',             5,   1,    CURRENT_TIMESTAMP - INTERVAL '3 days'),
(5, 5, 7, '告白气球婚礼必备 BGM',                 45,  NULL, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(6, 6, 7, '甜到齁，单身狗表示酸了',               9,   5,    CURRENT_TIMESTAMP - INTERVAL '1 day'),
(7, 7, 4, '浮夸现场版更震撼，建议去听 Live',      67,  NULL, CURRENT_TIMESTAMP - INTERVAL '6 days'),
(8, 8, 4, 'Eason 的颤音绝了',                     23,  7,    CURRENT_TIMESTAMP - INTERVAL '5 days'),
(9, 5, 5, 'Taylor 的歌太上头了',                  34,  NULL, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(10,6, 1, '简单爱是我的青春',                     18,  NULL, CURRENT_TIMESTAMP - INTERVAL '7 days'),
(11,7, 8, '十年KTV必点',                          29,  NULL, CURRENT_TIMESTAMP - INTERVAL '8 days'),
(12,8, 8, '前奏响起眼泪止不住',                   15,  11,   CURRENT_TIMESTAMP - INTERVAL '7 days');

-- --------------------------------------------------------------------
-- 6. 评分 rating（15 条，1~5 分；(uid,sid) 唯一，一人一首仅一次）
--    与评论解耦：评分用户不必评论过，评论用户也不必评分
-- --------------------------------------------------------------------
INSERT INTO rating (uid, sid, score, rate_time) VALUES
(5, 2, 5, CURRENT_TIMESTAMP - INTERVAL '5 days'),
(6, 2, 5, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(7, 2, 4, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(8, 2, 5, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(5, 7, 4, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(6, 7, 5, CURRENT_TIMESTAMP - INTERVAL '1 day'),
(7, 4, 5, CURRENT_TIMESTAMP - INTERVAL '6 days'),
(8, 4, 4, CURRENT_TIMESTAMP - INTERVAL '5 days'),
(5, 4, 3, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(5, 5, 4, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(6, 5, 5, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(5, 1, 5, CURRENT_TIMESTAMP - INTERVAL '7 days'),
(6, 1, 4, CURRENT_TIMESTAMP - INTERVAL '6 days'),
(7, 8, 5, CURRENT_TIMESTAMP - INTERVAL '8 days'),
(8, 3, 2, CURRENT_TIMESTAMP - INTERVAL '10 days'),
-- carol(uid=7) 评过 sid=12「已下架的歌」：评分发生在下架前，
-- 用于验证"我的评分"对失效歌曲"不剔除、仅标 playable=false"的策略（同收藏模块）
(7, 12, 3, CURRENT_TIMESTAMP - INTERVAL '9 days');

-- --------------------------------------------------------------------
-- 7. 歌单 playlist（6 个，含公开/私密）
-- --------------------------------------------------------------------
INSERT INTO playlist (plid, uid, playlist_name, description, cover, is_public, create_time) VALUES
(1, 5, '我的华语经典',   '收录百听不厌的华语金曲',   '/cover/pl1.jpg', TRUE,  CURRENT_TIMESTAMP - INTERVAL '20 days'),
(2, 5, '私藏单曲',       '不想给别人看的歌',         NULL,             FALSE, CURRENT_TIMESTAMP - INTERVAL '15 days'),
(3, 6, '欧美流行精选',   'Taylor 等欧美热门',        '/cover/pl3.jpg', TRUE,  CURRENT_TIMESTAMP - INTERVAL '12 days'),
(4, 7, '深夜 emo 专用',  '一个人的时候听',           '/cover/pl4.jpg', TRUE,  CURRENT_TIMESTAMP - INTERVAL '8 days'),
(5, 8, '健身燃脂歌单',   '动感节奏',                 NULL,             TRUE,  CURRENT_TIMESTAMP - INTERVAL '5 days'),
(6, 6, '空歌单测试',     '还没加歌的歌单',           NULL,             FALSE, CURRENT_TIMESTAMP - INTERVAL '1 day');

-- --------------------------------------------------------------------
-- 8. 歌单详情 playlist_detail（歌单 n:m 音乐；(plid,sid) 唯一，plid=6 故意留空）
-- --------------------------------------------------------------------
INSERT INTO playlist_detail (plid, sid, add_time) VALUES
(1, 1, CURRENT_TIMESTAMP - INTERVAL '19 days'),
(1, 2, CURRENT_TIMESTAMP - INTERVAL '19 days'),
(1, 3, CURRENT_TIMESTAMP - INTERVAL '18 days'),
(1, 7, CURRENT_TIMESTAMP - INTERVAL '10 days'),
(1, 8, CURRENT_TIMESTAMP - INTERVAL '9 days'),
(2, 2, CURRENT_TIMESTAMP - INTERVAL '14 days'),
(2, 4, CURRENT_TIMESTAMP - INTERVAL '13 days'),
-- alice 私密歌单 plid=2 收录 sid=12「已下架的歌」：收录发生在下架前，
-- 用于验证歌单详情对失效歌曲"不剔除、仅标 playable=false"的策略（同收藏模块）
(2, 12, CURRENT_TIMESTAMP - INTERVAL '12 days'),
(3, 5, CURRENT_TIMESTAMP - INTERVAL '11 days'),
(3, 6, CURRENT_TIMESTAMP - INTERVAL '11 days'),
(4, 4, CURRENT_TIMESTAMP - INTERVAL '7 days'),
(4, 8, CURRENT_TIMESTAMP - INTERVAL '6 days'),
(5, 5, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(5, 6, CURRENT_TIMESTAMP - INTERVAL '4 days');

-- --------------------------------------------------------------------
-- 9. 收藏 favorite（用户 n:m 音乐；(uid,sid) 唯一，不可重复收藏）
-- --------------------------------------------------------------------
INSERT INTO favorite (uid, sid, fav_time) VALUES
(5, 2, CURRENT_TIMESTAMP - INTERVAL '10 days'),
(5, 7, CURRENT_TIMESTAMP - INTERVAL '8 days'),
(5, 4, CURRENT_TIMESTAMP - INTERVAL '5 days'),
(6, 2, CURRENT_TIMESTAMP - INTERVAL '9 days'),
(6, 5, CURRENT_TIMESTAMP - INTERVAL '6 days'),
(7, 4, CURRENT_TIMESTAMP - INTERVAL '7 days'),
(7, 8, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(8, 2, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(8, 1, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(6, 7, CURRENT_TIMESTAMP - INTERVAL '1 day'),
-- carol(uid=7) 收藏了 sid=12「已下架的歌」：收藏发生在下架前，
-- 现该歌 is_deleted=true。用于验证"收藏列表仍展示失效歌、但 playable=false"
(7, 12, CURRENT_TIMESTAMP - INTERVAL '12 days');

-- --------------------------------------------------------------------
-- 9b. 评论点赞 comment_like（用户 n:m 评论；(uid,cid) 唯一，不可重复点赞）
--     点赞数实时 COUNT(*) 统计，与 comment.like_count 冗余字段无关。
--     覆盖：主评论被多人赞、回复也可被赞；故意让某用户既点过赞又没点过，
--     便于验证列表里 likedByMe 标志逐条正确。
--     仅用正常普通用户(5/6/7/8)，避开封禁(9)与软删(10)。
-- --------------------------------------------------------------------
INSERT INTO comment_like (uid, cid, like_time) VALUES
-- cid=1（晴天主评论）热门：4 人点赞
(5, 1, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(6, 1, CURRENT_TIMESTAMP - INTERVAL '4 days'),
(7, 1, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(8, 1, CURRENT_TIMESTAMP - INTERVAL '2 days'),
-- cid=2（晴天主评论）：2 人点赞
(5, 2, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(7, 2, CURRENT_TIMESTAMP - INTERVAL '2 days'),
-- cid=3（回复也能被点赞）：1 人点赞
(8, 3, CURRENT_TIMESTAMP - INTERVAL '2 days'),
-- cid=5（告白气球主评论）：1 人点赞
(6, 5, CURRENT_TIMESTAMP - INTERVAL '1 day'),
-- cid=7（浮夸主评论）：1 人点赞
(5, 7, CURRENT_TIMESTAMP - INTERVAL '1 day');

-- =====================================================================
-- 10. 对账：用 play_record 实际行数回填冗余字段 song.play_count
--     演示反规范化字段的一致性维护(见设计文档 5.4)
-- =====================================================================
UPDATE song s SET play_count = (
    SELECT COUNT(*) FROM play_record p WHERE p.sid = s.sid
);

-- =====================================================================
-- 11. 校正 IDENTITY 序列
--     因测试数据显式写入了主键 ID，需把序列推进到当前最大值之后，
--     否则后续应用 INSERT(不带 ID)会从 1 开始重试而撞主键冲突。
-- =====================================================================
SELECT setval(pg_get_serial_sequence('app_user','uid'),        (SELECT MAX(uid)  FROM app_user));
SELECT setval(pg_get_serial_sequence('album','aid'),           (SELECT MAX(aid)  FROM album));
SELECT setval(pg_get_serial_sequence('song','sid'),            (SELECT MAX(sid)  FROM song));
SELECT setval(pg_get_serial_sequence('play_record','pid'),     (SELECT MAX(pid)  FROM play_record));
SELECT setval(pg_get_serial_sequence('comment','cid'),         (SELECT MAX(cid)  FROM comment));
SELECT setval(pg_get_serial_sequence('rating','rid'),          (SELECT MAX(rid)  FROM rating));
SELECT setval(pg_get_serial_sequence('playlist','plid'),       (SELECT MAX(plid) FROM playlist));
SELECT setval(pg_get_serial_sequence('playlist_detail','pdid'),(SELECT MAX(pdid) FROM playlist_detail));
SELECT setval(pg_get_serial_sequence('favorite','fid'),        (SELECT MAX(fid)  FROM favorite));
SELECT setval(pg_get_serial_sequence('comment_like','clid'),   (SELECT MAX(clid) FROM comment_like));

-- =====================================================================
-- 12. 验证查询（可选执行，用于观察排行榜与统计效果）
--     前台口径统一过滤：audit_status=1 AND is_deleted=FALSE
-- =====================================================================

-- 【总榜 TOP10】基于冗余字段 play_count，O(1) 读取
-- 预期：晴天(sid=2) 居首
/*
SELECT s.sid, s.title, u.nickname AS uploader, s.play_count
FROM song s JOIN app_user u ON u.uid = s.uploader_uid
WHERE s.audit_status = 1 AND s.is_deleted = FALSE
ORDER BY s.play_count DESC, s.sid
LIMIT 10;
*/

-- 【日榜 TOP10】实时聚合 play_record 当天数据
-- 预期：晴天(2)、告白气球(7)、简单爱(1) 居前
/*
SELECT s.sid, s.title, COUNT(*) AS plays_today
FROM play_record p JOIN song s ON s.sid = p.sid
WHERE p.play_time >= date_trunc('day', CURRENT_TIMESTAMP)
  AND s.audit_status = 1 AND s.is_deleted = FALSE
GROUP BY s.sid, s.title
ORDER BY plays_today DESC, s.sid
LIMIT 10;
*/

-- 【周榜 TOP10】最近 7 天聚合（演示周榜口径）
/*
SELECT s.sid, s.title, COUNT(*) AS plays_week
FROM play_record p JOIN song s ON s.sid = p.sid
WHERE p.play_time >= CURRENT_TIMESTAMP - INTERVAL '7 days'
  AND s.audit_status = 1 AND s.is_deleted = FALSE
GROUP BY s.sid, s.title
ORDER BY plays_week DESC, s.sid
LIMIT 10;
*/

-- 【用户活跃度】按点唱次数排名
/*
SELECT u.uid, u.nickname, COUNT(p.pid) AS play_times
FROM app_user u LEFT JOIN play_record p ON p.uid = u.uid
WHERE u.is_deleted = FALSE
GROUP BY u.uid, u.nickname
ORDER BY play_times DESC;
*/

-- 【上传者贡献】音乐数量 + 总播放量
/*
SELECT u.uid, u.nickname,
       COUNT(s.sid) AS song_count,
       COALESCE(SUM(s.play_count),0) AS total_plays
FROM app_user u JOIN song s ON s.uploader_uid = u.uid
WHERE u.role = 1 AND s.is_deleted = FALSE
GROUP BY u.uid, u.nickname
ORDER BY total_plays DESC;
*/

-- 【某首歌平均分与评分数】演示评分与评论解耦
/*
SELECT s.sid, s.title, ROUND(AVG(r.score),2) AS avg_score, COUNT(r.rid) AS rate_count
FROM song s LEFT JOIN rating r ON r.sid = s.sid
WHERE s.audit_status = 1 AND s.is_deleted = FALSE
GROUP BY s.sid, s.title
ORDER BY avg_score DESC NULLS LAST, rate_count DESC;
*/









