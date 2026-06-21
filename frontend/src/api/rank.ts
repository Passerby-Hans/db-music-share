import http from './http'
import type { RankBoard, RankItemVO } from './types'

/**
 * 排行榜接口（2.0 第②块）。日/周/总榜 TOP10，公开白名单（无需 token）。
 * 数据为准实时：Redis ZSET 缓存 + 空/异常降级聚合 play_record + 定时对账（见 docs/api/rank.md）。
 */

/**
 * 取某榜 TOP10（公开）。
 * @param board 榜单类型
 */
export function getRank(board: RankBoard): Promise<RankItemVO[]> {
  return http.get(`/rank/${board}`) as unknown as Promise<RankItemVO[]>
}
