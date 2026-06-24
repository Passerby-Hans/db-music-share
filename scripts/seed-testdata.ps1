# 导入测试数据(sql/03_seed.sql)到发行版 compose 的 postgres。
# 用法：仓库根执行  ./scripts/seed-testdata.ps1   (postgres 容器须在运行)
# 注意：03_seed 仅含数据库行，不含真实媒体；种子歌曲可浏览但播放/封面会 404。
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "-> 拷贝 03_seed.sql 进 postgres 容器 ..."
docker compose cp sql/03_seed.sql postgres:/tmp/03_seed.sql
Write-Host "-> 执行导入 ..."
docker compose exec -T postgres psql -U music -d music_share -f /tmp/03_seed.sql
Write-Host "[OK] 测试数据导入完成。"
