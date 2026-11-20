import { test } from 'node:test'
import assert from 'node:assert/strict'
import type { ExperimentReport } from '../api/sim.ts'
import {
  buildComparisonRows,
  buildComparisonSummary,
  comparisonSummaryType,
  formatTickSeconds
} from './experimentCompare.ts'

function report(overrides: Partial<ExperimentReport> = {}): ExperimentReport {
  return {
    id: 1,
    roadnetId: 1,
    taskSetId: 1,
    algorithm: 'nearest',
    algorithmName: '就近分配',
    agvCount: 3,
    seed: 20261107,
    status: 'FINISHED',
    makespan: 2608,
    totalPathLen: 708,
    totalWaitTick: 779,
    conflictCount: 12,
    engineVersion: '2',
    createTime: '2026-11-20 21:00:00',
    ...overrides
  }
}

const nearest = report()
const greedy = report({
  id: 2,
  algorithm: 'greedy-eta',
  algorithmName: '贪心 ETA',
  makespan: 2327,
  totalPathLen: 628,
  totalWaitTick: 427,
  conflictCount: 8
})

function statusLabel(status: string) {
  return status === 'FINISHED' ? '已完成' : status === 'DEADLOCK' ? '死锁' : status
}

test('buildComparisonRows marks the faster algorithm and keeps B - A signs', () => {
  const rows = buildComparisonRows([nearest, greedy])
  assert.equal(rows.length, 4)
  assert.equal(rows[0].delta, '-281（-10.8%）')
  assert.equal(rows[0].winner, 1)
  assert.equal(rows[0].winnerLabel, '贪心 ETA')
  assert.equal(rows[1].delta, '-80.0（-11.3%）')
  assert.equal(rows[2].delta, '-352（-45.2%）')
  assert.equal(rows[3].delta, '-4（-33.3%）')
})

test('buildComparisonRows handles reversed selection order', () => {
  const rows = buildComparisonRows([greedy, nearest])
  assert.equal(rows[0].delta, '+281（+12.1%）')
  assert.equal(rows[0].winner, 0)
  assert.equal(rows[0].winnerLabel, '贪心 ETA')
})

test('summary recommends the algorithm that wins all comparable metrics', () => {
  const rows = buildComparisonRows([nearest, greedy])
  const summary = buildComparisonSummary([nearest, greedy], rows, statusLabel)
  assert.match(summary, /贪心 ETA 在 4 项可比指标上均更优/)
  assert.match(summary, /比 就近分配 少 281 tick（10\.8%）/)
  assert.equal(comparisonSummaryType(rows), 'success')
})

test('summary stays readable when the selected rows are reversed', () => {
  const rows = buildComparisonRows([greedy, nearest])
  const summary = buildComparisonSummary([greedy, nearest], rows, statusLabel)
  assert.match(summary, /贪心 ETA 在 4 项可比指标上均更优/)
  assert.match(summary, /比 就近分配 少 281 tick（10\.8%）/)
  assert.doesNotMatch(summary, /\+281/)
})

test('deadlock rows are excluded and the summary is a warning', () => {
  const deadlock = report({
    id: 3,
    algorithm: 'greedy-eta',
    algorithmName: '贪心 ETA',
    status: 'DEADLOCK',
    makespan: null,
    totalPathLen: null,
    totalWaitTick: null,
    conflictCount: null
  })
  const rows = buildComparisonRows([nearest, deadlock])
  const summary = buildComparisonSummary([nearest, deadlock], rows, statusLabel)
  assert.match(summary, /贪心 ETA（死锁） 没有完整指标/)
  assert.equal(comparisonSummaryType(rows), 'warning')
  assert.equal(rows[0].valid, false)
  assert.equal(rows[0].delta, '-')
})

test('tick values also show minutes and seconds', () => {
  assert.equal(formatTickSeconds(2608), '4 分 21 秒')
  assert.equal(formatTickSeconds(779), '1 分 18 秒')
  assert.equal(formatTickSeconds(5), '1 秒')
  assert.equal(formatTickSeconds(null), '')
})
