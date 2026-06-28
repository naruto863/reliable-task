import { defineStore } from 'pinia'

import { adminApiClient, type AdminApiClient } from '@/api/client'
import type { FailureTop, FailureTopQuery, RecentFailuresQuery, TaskFailure, TaskStats } from '@/api/types'
import { toAppErrorState, type AppErrorState } from '@/utils/errorState'

const DEFAULT_LIMIT = 5
const MAX_LIMIT = 50
const MAX_WINDOW_HOURS = 168

type DashboardApiClient = Pick<AdminApiClient, 'getTaskStats' | 'listFailureTop' | 'listRecentFailures'>

function clampLimit(value: number): number {
  if (!Number.isFinite(value)) {
    return DEFAULT_LIMIT
  }
  return Math.min(Math.max(Math.trunc(value), 1), MAX_LIMIT)
}

function clampWindowHours(value: number | null): number | null {
  if (value === null || !Number.isFinite(value)) {
    return null
  }
  return Math.min(Math.max(Math.trunc(value), 1), MAX_WINDOW_HOURS)
}

function toLocalDateTime(value: Date): string {
  return value.toISOString().slice(0, 19)
}

function buildWindowQuery(limit: number, windowHours: number | null): RecentFailuresQuery {
  const query: RecentFailuresQuery = {
    limit,
  }

  if (windowHours !== null) {
    // 控制台使用浏览器当前时间构造短窗口查询；后端仍会按 AdminQueryGuard 限制最大窗口。
    const end = new Date()
    const start = new Date(end.getTime() - windowHours * 60 * 60 * 1000)
    query.createTimeStart = toLocalDateTime(start)
    query.createTimeEnd = toLocalDateTime(end)
  }

  return query
}

export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    stats: null as TaskStats | null,
    failureTop: [] as FailureTop[],
    recentFailures: [] as TaskFailure[],
    loading: false,
    error: null as AppErrorState | null,
    limit: DEFAULT_LIMIT,
    windowHours: null as number | null,
  }),
  getters: {
    hasData: (state) =>
      Boolean(state.stats || state.failureTop.length > 0 || state.recentFailures.length > 0),
  },
  actions: {
    setLimit(value: number) {
      this.limit = clampLimit(value)
    },
    setWindowHours(value: number | null) {
      this.windowHours = clampWindowHours(value)
    },
    async loadDashboard(api: DashboardApiClient = adminApiClient) {
      this.loading = true
      this.error = null

      try {
        const query = buildWindowQuery(this.limit, this.windowHours)
        // 首页同时拉取总览、失败聚合和最近失败，三者互不依赖，可并行降低首屏等待时间。
        const failureTopQuery: FailureTopQuery = {
          groupBy: 'taskType,errorCode',
          ...query,
        }
        const [stats, failureTop, recentFailures] = await Promise.all([
          api.getTaskStats(),
          api.listFailureTop(failureTopQuery),
          api.listRecentFailures(query),
        ])
        this.stats = stats
        this.failureTop = failureTop
        this.recentFailures = recentFailures
      } catch (error) {
        this.error = toAppErrorState(error)
      } finally {
        this.loading = false
      }
    },
  },
})

export { DEFAULT_LIMIT as DEFAULT_DASHBOARD_LIMIT }
