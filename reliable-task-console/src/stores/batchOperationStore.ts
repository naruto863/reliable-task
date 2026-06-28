import { defineStore } from 'pinia'

import { adminApiClient, type AdminApiClient } from '@/api/client'
import type { BatchOperationRequest, BatchOperationResult } from '@/api/types'
import type { AppErrorState } from '@/utils/errorState'
import { toAppErrorState } from '@/utils/errorState'

export type BatchFilterKey = 'taskType' | 'status' | 'createTimeStart' | 'createTimeEnd'
export type BatchExecuteAction = 'requeue' | 'cancel'

interface BatchFilters {
  taskType: string
  status: number | null
  createTimeStart: string
  createTimeEnd: string
}

type BatchApiClient = Pick<AdminApiClient, 'previewBatch' | 'batchRequeue' | 'batchCancel'>

const DEFAULT_LIMIT = 20
const DEFAULT_MAX_BATCH_LIMIT = 1000

function emptyFilters(): BatchFilters {
  return {
    taskType: '',
    status: null,
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

function clampLimit(value: unknown, maxLimit = DEFAULT_MAX_BATCH_LIMIT): number {
  const normalized = normalizePositiveInt(value, DEFAULT_LIMIT)
  return Math.min(normalized, Math.max(maxLimit, 1))
}

function compactRequest(request: BatchOperationRequest): BatchOperationRequest {
  // 批量请求只发送用户明确填写的条件，避免空字符串扩大或扭曲后端匹配范围。
  return Object.fromEntries(
    Object.entries(request).filter(([, value]) => value !== '' && value !== null && value !== undefined),
  ) as BatchOperationRequest
}

function localError(message: string): AppErrorState {
  return {
    kind: 'business',
    title: 'Request failed',
    message,
  }
}

export const useBatchOperationStore = defineStore('batchOperation', {
  state: () => ({
    filters: emptyFilters(),
    limit: DEFAULT_LIMIT,
    previewResult: null as BatchOperationResult | null,
    executeResult: null as BatchOperationResult | null,
    loading: false,
    error: null as AppErrorState | null,
  }),
  actions: {
    setFilter(key: BatchFilterKey, value: string | number | null) {
      if (key === 'status') {
        this.filters.status = value === null || value === '' ? null : Number(value)
      } else {
        this.filters[key] = normalizeString(value)
      }
      this.previewResult = null
      this.executeResult = null
    },
    setLimit(value: number, maxLimit = DEFAULT_MAX_BATCH_LIMIT) {
      this.limit = clampLimit(value, maxLimit)
      this.previewResult = null
      this.executeResult = null
    },
    buildRequest(dryRun: boolean): BatchOperationRequest {
      // dryRun=true 用于预览，dryRun=false 用于真正执行；其余筛选条件必须保持一致。
      return compactRequest({
        taskType: this.filters.taskType,
        status: this.filters.status,
        createTimeStart: this.filters.createTimeStart,
        createTimeEnd: this.filters.createTimeEnd,
        limit: this.limit,
        dryRun,
      })
    },
    async preview(api: BatchApiClient = adminApiClient) {
      this.loading = true
      this.error = null
      this.executeResult = null

      try {
        this.previewResult = await api.previewBatch(this.buildRequest(true))
      } catch (error) {
        this.previewResult = null
        this.error = toAppErrorState(error)
      } finally {
        this.loading = false
      }
    },
    async execute(action: BatchExecuteAction, api: BatchApiClient = adminApiClient) {
      if (!this.previewResult) {
        // 前端要求先 preview 再执行，配合后端 dryRun/audit 让批量写入具备可复核入口。
        this.error = localError('Preview required before executing a batch operation.')
        return
      }

      this.loading = true
      this.error = null

      try {
        const request = this.buildRequest(false)
        this.executeResult =
          action === 'requeue' ? await api.batchRequeue(request) : await api.batchCancel(request)
      } catch (error) {
        this.executeResult = null
        this.error = toAppErrorState(error)
      } finally {
        this.loading = false
      }
    },
  },
})
