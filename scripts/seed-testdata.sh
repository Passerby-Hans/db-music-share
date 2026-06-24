#!/usr/bin/env bash
# 导入测试数据(sql/03_seed.sql)到发行版 compose 的 postgres。
# 用法：仓库根执行  ./scripts/seed-testdata.sh   (postgres 容器须在运行)
# 注意：03_seed 仅含数据库行(用户/歌曲/榜单等)，不含真实媒体文件——
#       种子歌曲可浏览/进榜/可审核，但点播放与封面会 404。演示播放请用 app 自行上传。
set -euo pipefail
# Git Bash(Windows)会把 /tmp/... 这类参数误转成 Windows 路径(MSYS path 转换)，禁用之；
# 真 Linux 上该变量被忽略，无副作用。
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."

echo "→ 拷贝 03_seed.sql 进 postgres 容器 ..."
docker compose cp sql/03_seed.sql postgres:/tmp/03_seed.sql
echo "→ 执行导入 ..."
docker compose exec -T postgres psql -U music -d music_share -f /tmp/03_seed.sql
echo "✅ 测试数据导入完成。"
