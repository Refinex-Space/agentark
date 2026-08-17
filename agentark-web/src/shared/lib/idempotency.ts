/**
 * 为浏览器发起的单次写命令创建稳定格式的幂等键。
 *
 * 调用方必须在重试同一逻辑请求时复用返回值，不能每次重试重新生成。
 */
export function createIdempotencyKey(): string {
  return crypto.randomUUID();
}
