import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { ApiClientError } from '@/api/client'
import type { AuditLog, PageResult } from '@/api/types'
import { useAuditLogStore } from './auditLogStore'

const audit: AuditLog = {
  id: 301,
  operationType: 'TASK_RETRY',
  operator: 'ops',
  targetType: 'TASK',
  targetId: '101',
  taskId: 101,
  batchOperationId: null,
  requestSummary: 'retry task',
  result: 'SUCCESS',
  errorMsg: null,
  traceId: 'trace-101',
  createTime: '2026-06-14T09:00:00',
}

const page: PageResult<AuditLog> = {
  records: [audit],
  total: 1,
  pageNum: 1,
  pageSize: 20,
}

describe('audit log store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads audit logs with filters and pagination', async () => {
    const store = useAuditLogStore()
    const api = {
      listAuditLogs: vi.fn().mockResolvedValue(page),
    }

    store.setFilter('operator', 'ops')
    store.setFilter('createTimeStart', '2026-06-14T00:00')
    store.setPage(2)
    await store.loadAuditLogs(api)

    expect(api.listAuditLogs).toHaveBeenCalledWith({
      pageNum: 2,
      pageSize: 20,
      operator: 'ops',
      createTimeStart: '2026-06-14T00:00',
    })
    expect(store.records).toEqual([audit])
  })

  it('serializes route query and clamps page size', () => {
    const store = useAuditLogStore()

    store.applyRouteQuery(
      {
        pageNum: '3',
        pageSize: '500',
        operator: 'ops',
      },
      50,
    )

    expect(store.pageNum).toBe(3)
    expect(store.pageSize).toBe(50)
    expect(store.toRouteQuery()).toEqual({
      pageNum: '3',
      pageSize: '50',
      operator: 'ops',
    })
  })

  it('records audit disabled as a not found error state', async () => {
    const store = useAuditLogStore()
    const api = {
      listAuditLogs: vi.fn().mockRejectedValue(
        new ApiClientError({
          kind: 'business',
          code: 404,
          message: 'Audit log is disabled',
        }),
      ),
    }

    await store.loadAuditLogs(api)

    expect(store.error?.kind).toBe('notFound')
    expect(store.error?.message).toBe('Audit log is disabled')
  })
})
