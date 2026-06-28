import { defineStore } from 'pinia'

import { adminApiClient, type AdminApiClient } from '@/api/client'
import type { PageResult, TaskListQuery, TaskSummary } from '@/api/types'
import { toAppErrorState, type AppErrorState } from '@/utils/errorState'

export type TaskFilterKey =
  | 'status'
  | 'taskType'
  | 'bizType'
  | 'bizId'
  | 'workerId'
  | 'traceId'
  | 'tenantId'
  | 'createTimeStart'
  | 'createTimeEnd'

type TaskListApiClient = Pick<AdminApiClient, 'listTasks'>

interface TaskFilters {
  status: number | null
  taskType: string
  bizType: string
  bizId: string
  workerId: string
  traceId: string
  tenantId: string
  createTimeStart: string
  createTimeEnd: string
}

const DEFAULT_PAGE_SIZE = 20
const DEFAULT_MAX_PAGE_SIZE = 200

function emptyFilters(): TaskFilters {
  return {
    status: null,
    taskType: '',
    bizType: '',
    bizId: '',
    workerId: '',
    traceId: '',
    tenantId: '',
    createTimeStart: '',
    createTimeEnd: '',
  }
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizePositiveInt(value: unknown, fallback: number): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 1) {
    return fallback
  }
  return Math.trunc(parsed)
}

function clampPageSize(value: unknown, maxPageSize = DEFAULT_MAX_PAGE_SIZE): number {
  const normalized = normalizePositiveInt(value, DEFAULT_PAGE_SIZE)
  return Math.min(normalized, Math.max(maxPageSize, 1))
}

function compactQuery(query: TaskListQuery): TaskListQuery {
  // 路由和表单状态里会保留空字符串，真正请求前统一压缩，避免后端把空值当过滤条件。
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== '' && value !== null && value !== undefined),
  ) as TaskListQuery
}

export const useTaskListStore = defineStore('taskList', {
  state: () => ({
    filters: emptyFilters(),
    pageNum: 1,
    pageSize: DEFAULT_PAGE_SIZE,
    result: null as PageResult<TaskSummary> | null,
    loading: false,
    error: null as AppErrorState | null,
  }),
  getters: {
    records: (state) => state.result?.records || [],
    total: (state) => state.result?.total || 0,
    hasFilters: (state) =>
      Object.values(state.filters).some((value) => value !== '' && value !== null),
  },
  actions: {
    setFilter(key: TaskFilterKey, value: string | number | null) {
      if (key === 'status') {
        this.filters.status = value === null || value === '' ? null : Number(value)
      } else {
        this.filters[key] = normalizeString(value)
      }
      this.pageNum = 1
    },
    setPage(pageNum: number) {
      this.pageNum = normalizePositiveInt(pageNum, 1)
    },
    setPageSize(pageSize: number, maxPageSize = DEFAULT_MAX_PAGE_SIZE) {
      this.pageSize = clampPageSize(pageSize, maxPageSize)
      this.pageNum = 1
    },
    resetFilters() {
      this.filters = emptyFilters()
      this.pageNum = 1
    },
    applyRouteQuery(query: Record<string, unknown>, maxPageSize = DEFAULT_MAX_PAGE_SIZE) {
      // 任务列表筛选与 URL query 双向同步，刷新页面或复制链接时可以恢复同一筛选视图。
      this.pageNum = normalizePositiveInt(query.pageNum, 1)
      this.pageSize = clampPageSize(query.pageSize, maxPageSize)
      this.filters = {
        ...emptyFilters(),
        status: query.status === undefined ? null : Number(query.status),
        taskType: normalizeString(query.taskType),
        bizType: normalizeString(query.bizType),
        bizId: normalizeString(query.bizId),
        workerId: normalizeString(query.workerId),
        traceId: normalizeString(query.traceId),
        tenantId: normalizeString(query.tenantId),
        createTimeStart: normalizeString(query.createTimeStart),
        createTimeEnd: normalizeString(query.createTimeEnd),
      }
      if (!Number.isFinite(this.filters.status)) {
        this.filters.status = null
      }
    },
    toRouteQuery(): Record<string, string> {
      const query = this.buildQuery()
      return Object.fromEntries(Object.entries(query).map(([key, value]) => [key, String(value)]))
    },
    buildQuery(): TaskListQuery {
      // pageNum/pageSize 始终随请求发送；筛选项为空时会被 compactQuery 移除。
      return compactQuery({
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        ...this.filters,
      })
    },
    async loadTasks(api: TaskListApiClient = adminApiClient) {
      this.loading = true
      this.error = null

      try {
        this.result = await api.listTasks(this.buildQuery())
      } catch (error) {
        this.error = toAppErrorState(error)
      } finally {
        this.loading = false
      }
    },
  },
})
