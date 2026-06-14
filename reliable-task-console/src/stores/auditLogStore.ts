import { defineStore } from 'pinia'

import { adminApiClient, type AdminApiClient } from '@/api/client'
import type { AuditLog, AuditLogQuery, PageResult } from '@/api/types'
import { toAppErrorState, type AppErrorState } from '@/utils/errorState'

type AuditLogApiClient = Pick<AdminApiClient, 'listAuditLogs'>
type AuditFilterKey = 'operator' | 'createTimeStart' | 'createTimeEnd'

interface AuditFilters {
  operator: string
  createTimeStart: string
  createTimeEnd: string
}

const DEFAULT_PAGE_SIZE = 20
const DEFAULT_MAX_PAGE_SIZE = 200

function emptyFilters(): AuditFilters {
  return {
    operator: '',
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

function compactQuery(query: AuditLogQuery): AuditLogQuery {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== '' && value !== null && value !== undefined),
  ) as AuditLogQuery
}

export const useAuditLogStore = defineStore('auditLogs', {
  state: () => ({
    filters: emptyFilters(),
    pageNum: 1,
    pageSize: DEFAULT_PAGE_SIZE,
    result: null as PageResult<AuditLog> | null,
    loading: false,
    error: null as AppErrorState | null,
  }),
  getters: {
    records: (state) => state.result?.records || [],
    total: (state) => state.result?.total || 0,
  },
  actions: {
    setFilter(key: AuditFilterKey, value: string) {
      this.filters[key] = normalizeString(value)
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
      this.pageNum = normalizePositiveInt(query.pageNum, 1)
      this.pageSize = clampPageSize(query.pageSize, maxPageSize)
      this.filters = {
        operator: normalizeString(query.operator),
        createTimeStart: normalizeString(query.createTimeStart),
        createTimeEnd: normalizeString(query.createTimeEnd),
      }
    },
    toRouteQuery(): Record<string, string> {
      const query = this.buildQuery()
      return Object.fromEntries(Object.entries(query).map(([key, value]) => [key, String(value)]))
    },
    buildQuery(): AuditLogQuery {
      return compactQuery({
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        ...this.filters,
      })
    },
    async loadAuditLogs(api: AuditLogApiClient = adminApiClient) {
      this.loading = true
      this.error = null

      try {
        this.result = await api.listAuditLogs(this.buildQuery())
      } catch (error) {
        this.error = toAppErrorState(error)
      } finally {
        this.loading = false
      }
    },
  },
})
