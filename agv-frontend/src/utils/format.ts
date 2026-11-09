/**
 * 把后端返回的 ISO 时间格式化为 `YYYY-MM-DD HH:mm:ss`。
 * 空值返回 `-`，避免表格出现无意义的空白单元格。
 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  return normalized.length > 19 ? normalized.slice(0, 19) : normalized
}

/**
 * 格式化汇总指标。未完成或失败的实验没有指标（后端返回 null），
 * 统一显示 `-`，与真实数值 0 区分开。
 */
export function formatMetric(value?: number | null, digits?: number): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '-'
  return digits === undefined ? String(value) : Number(value).toFixed(digits)
}

/**
 * 任务集下拉标签。名称里已经带「（20 单）」时不再重复追加，
 * 避免出现「演示任务集（20 单）（20 单）」。
 */
export function taskSetLabel(name: string, taskCount?: number | null): string {
  if (taskCount === null || taskCount === undefined) return name
  return name.includes(`${taskCount} 单`) ? name : `${name}（${taskCount} 单）`
}
