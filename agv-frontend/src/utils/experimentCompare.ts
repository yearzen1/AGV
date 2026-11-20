import type { ExperimentReport } from '../api/sim.ts'
import { formatMetric } from './format.ts'

export type MetricKey = 'makespan' | 'totalPathLen' | 'totalWaitTick' | 'conflictCount'

export interface MetricDef {
  key: MetricKey
  title: string
  unit: string
  precision: number
}

export interface ComparisonRow {
  key: MetricKey
  metric: string
  first: string
  second: string
  delta: string
  winner: number
  winnerLabel: string
  valid: boolean
}

export const COMPARISON_METRICS: MetricDef[] = [
  { key: 'makespan', title: '完成时间', unit: 'tick', precision: 0 },
  { key: 'totalPathLen', title: '总路径长', unit: 'm', precision: 1 },
  { key: 'totalWaitTick', title: '总等待', unit: 'tick', precision: 0 },
  { key: 'conflictCount', title: '冲突次数', unit: '次', precision: 0 }
]

export function formatTickSeconds(value: number | null | undefined) {
  if (value == null) return ''
  const seconds = Math.round(Number(value) / 10)
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return minutes > 0 ? `${minutes} 分 ${rest} 秒` : `${rest} 秒`
}

function formatDelta(delta: number, pct: number | null, precision: number) {
  const sign = delta > 0 ? '+' : ''
  const text = `${sign}${delta.toFixed(precision)}`
  if (pct == null) return text
  return `${text}（${pct > 0 ? '+' : ''}${pct.toFixed(1)}%）`
}

export function buildComparisonRows(results: ExperimentReport[]): ComparisonRow[] {
  if (results.length < 2) return []
  const [first, second] = results
  return COMPARISON_METRICS.map((metric) => {
    const valueA = first[metric.key]
    const valueB = second[metric.key]
    const valid = valueA != null && valueB != null
    const delta = valid ? Number(valueB) - Number(valueA) : null
    const pct = valid && Number(valueA) !== 0 ? (delta! / Number(valueA)) * 100 : null
    const winner = valid ? (delta! < 0 ? 1 : delta! > 0 ? 0 : -1) : -1
    return {
      key: metric.key,
      metric: `${metric.title}（${metric.unit}）`,
      first: formatMetric(valueA, metric.precision),
      second: formatMetric(valueB, metric.precision),
      delta: valid ? formatDelta(delta!, pct, metric.precision) : '-',
      winner,
      winnerLabel:
        !valid ? '—' : winner === -1 ? '持平' : winner === 0 ? first.algorithmName : second.algorithmName,
      valid
    }
  })
}

export function buildComparisonSummary(
  results: ExperimentReport[],
  rows: ComparisonRow[],
  statusLabel: (status: string) => string
) {
  if (results.length < 2) return ''
  const validRows = rows.filter((row) => row.valid)
  const missing = results.filter((row) => row.makespan == null)
  const missingText = missing.length
    ? `${missing.map((row) => `${row.algorithmName}（${statusLabel(row.status)}）`).join('、')} 没有完整指标，已从对比中排除。`
    : ''
  if (validRows.length === 0) {
    return missingText || '两条实验都没有完整指标，无法对比。'
  }

  const [first, second] = results
  const firstWins = validRows.filter((row) => row.winner === 0).length
  const secondWins = validRows.filter((row) => row.winner === 1).length

  let makespanText = ''
  if (first.makespan != null && second.makespan != null) {
    if (first.makespan === second.makespan) {
      makespanText = '两套算法的完成时间相同。'
    } else {
      const winner = first.makespan < second.makespan ? first.algorithmName : second.algorithmName
      const loser = first.makespan < second.makespan ? second.algorithmName : first.algorithmName
      const diff = Math.abs(second.makespan - first.makespan)
      const pct =
        Math.max(first.makespan, second.makespan) === 0
          ? 0
          : (diff / Math.max(first.makespan, second.makespan)) * 100
      makespanText = `完成时间 ${winner} 比 ${loser} 少 ${diff} tick（${pct.toFixed(1)}%）。`
    }
  }

  if (firstWins === validRows.length) {
    return `${missingText}${first.algorithmName} 在 ${validRows.length} 项可比指标上均更优，推荐优先使用。${makespanText}`
  }
  if (secondWins === validRows.length) {
    return `${missingText}${second.algorithmName} 在 ${validRows.length} 项可比指标上均更优，推荐优先使用。${makespanText}`
  }

  const firstKeys = validRows
    .filter((row) => row.winner === 0)
    .map((row) => row.metric.replace(/（.*?）/, ''))
    .join('、')
  const secondKeys = validRows
    .filter((row) => row.winner === 1)
    .map((row) => row.metric.replace(/（.*?）/, ''))
    .join('、')
  return `${missingText}两套算法各有优势：${first.algorithmName} 在 ${firstKeys || '部分指标'} 更优，${second.algorithmName} 在 ${secondKeys || '部分指标'} 更优。${makespanText}`
}

export function comparisonSummaryType(rows: ComparisonRow[]) {
  const validRows = rows.filter((row) => row.valid)
  if (validRows.length < 4) return 'warning'
  const winners = new Set(validRows.map((row) => row.winner))
  return winners.size === 1 && !winners.has(-1) ? 'success' : 'info'
}
