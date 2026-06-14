import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { ApiClientError } from '@/api/client'
import type { AuditLog, ConsoleTaskDetail, TaskExecutionLog, TaskTimelineItem } from '@/api/types'
import { useTaskDetailStore } from './taskDetailStore'

const detail: ConsoleTaskDetail = {
  id: 101,
  taskType: 'ORDER_EMAIL',
  bizType: 'ORDER',
  bizId: 'ORD-101',
  bizUniqueKey: 'ORDER:ORD-101',
  statusCode: 5,
  statusDesc: 'dead',
  priority: 10,
  payloadView: {
    payloadVisible: true,
    payloadMasked: true,
    payloadPreview: '{"orderId":"ORD-101","token":"***"}',
    payloadLength: 256,
    payloadRevealAllowed: false,
    payloadPlaintext: '{"orderId":"ORD-101","token":"plain-secret"}',
  },
  executeCount: 2,
  maxRetryCount: 5,
  retryStrategy: 'FIXED',
  retryIntervalMs: 30000,
  nextExecuteTime: '2026-06-13T22:30:00',
  shardKey: 'order-101',
  tenantId: 'tenant-a',
  workerId: 'worker-a',
  errorMsg: 'SMTP timeout',
  traceId: 'trace-101',
  createTime: '2026-06-13T22:00:00',
  updateTime: '2026-06-13T22:10:00',
  finishTime: null,
}

const logs: TaskExecutionLog[] = [
  {
    id: 201,
    taskId: 101,
    attemptNo: 2,
    statusBefore: 'RUNNING',
    statusAfter: 'DEAD',
    executeTime: '2026-06-13T22:10:00',
    durationMs: 1500,
    status: 5,
    statusDesc: 'dead',
    errorCode: 'SocketTimeoutException',
    errorMsg: 'SMTP timeout',
    workerId: 'worker-a',
    traceId: 'trace-101',
    createTime: '2026-06-13T22:10:01',
  },
]

const timeline: TaskTimelineItem[] = [
  {
    source: 'LOG',
    eventType: 'TASK_EXECUTION',
    sourceId: 201,
    taskId: 101,
    eventTime: '2026-06-13T22:10:00',
    statusBefore: 'RUNNING',
    statusAfter: 'DEAD',
    statusCode: 5,
    statusDesc: 'dead',
    attemptNo: 2,
    durationMs: 1500,
    errorCode: 'SocketTimeoutException',
    errorMsg: 'SMTP timeout',
    workerId: 'worker-a',
    operator: null,
    operationType: null,
    requestSummary: null,
    result: null,
    traceId: 'trace-101',
  },
]

const auditLogs: AuditLog[] = [
  {
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
    createTime: '2026-06-13T22:11:00',
  },
]

describe('task detail store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads console-safe detail, logs, timeline, and task audit logs', async () => {
    const store = useTaskDetailStore()
    const api = {
      getConsoleTaskDetail: vi.fn().mockResolvedValue(detail),
      getTaskLogs: vi.fn().mockResolvedValue(logs),
      getTaskTimeline: vi.fn().mockResolvedValue(timeline),
      getTaskAuditLogs: vi.fn().mockResolvedValue(auditLogs),
    }

    await store.loadTask(101, api)

    expect(api.getConsoleTaskDetail).toHaveBeenCalledWith(101)
    expect(api.getTaskLogs).toHaveBeenCalledWith(101)
    expect(api.getTaskTimeline).toHaveBeenCalledWith(101)
    expect(api.getTaskAuditLogs).toHaveBeenCalledWith(101)
    expect(store.detail).toEqual(detail)
    expect(store.logs).toEqual(logs)
    expect(store.timeline).toEqual(timeline)
    expect(store.auditLogs).toEqual(auditLogs)
    expect(store.error).toBeNull()
  })

  it('stops loading related panels when detail returns 404', async () => {
    const store = useTaskDetailStore()
    const api = {
      getConsoleTaskDetail: vi.fn().mockRejectedValue(
        new ApiClientError({
          kind: 'business',
          code: 404,
          message: 'Task not found: 404',
        }),
      ),
      getTaskLogs: vi.fn(),
      getTaskTimeline: vi.fn(),
      getTaskAuditLogs: vi.fn(),
    }

    await store.loadTask(404, api)

    expect(store.error?.kind).toBe('notFound')
    expect(store.detail).toBeNull()
    expect(api.getTaskLogs).not.toHaveBeenCalled()
    expect(api.getTaskTimeline).not.toHaveBeenCalled()
    expect(api.getTaskAuditLogs).not.toHaveBeenCalled()
  })

  it('keeps the detail visible when an optional panel fails', async () => {
    const store = useTaskDetailStore()
    const api = {
      getConsoleTaskDetail: vi.fn().mockResolvedValue(detail),
      getTaskLogs: vi.fn().mockRejectedValue(
        new ApiClientError({
          kind: 'network',
          message: 'Failed to fetch',
        }),
      ),
      getTaskTimeline: vi.fn().mockResolvedValue(timeline),
      getTaskAuditLogs: vi.fn().mockResolvedValue([]),
    }

    await store.loadTask(101, api)

    expect(store.detail).toEqual(detail)
    expect(store.logsError?.kind).toBe('network')
    expect(store.timeline).toEqual(timeline)
    expect(store.auditLogs).toEqual([])
  })

  it('runs a task write operation and refreshes detail panels', async () => {
    const store = useTaskDetailStore()
    store.taskId = 101
    const api = {
      getConsoleTaskDetail: vi.fn().mockResolvedValue(detail),
      getTaskLogs: vi.fn().mockResolvedValue(logs),
      getTaskTimeline: vi.fn().mockResolvedValue(timeline),
      getTaskAuditLogs: vi.fn().mockResolvedValue(auditLogs),
      retryTask: vi.fn().mockResolvedValue(true),
      cancelTask: vi.fn(),
      requeueTask: vi.fn(),
      updateTaskPayload: vi.fn(),
    }

    await store.runTaskOperation('retry', api)

    expect(api.retryTask).toHaveBeenCalledWith(101)
    expect(api.getConsoleTaskDetail).toHaveBeenCalledWith(101)
    expect(store.operationSuccess).toContain('retry')
    expect(store.operationError).toBeNull()
  })

  it('updates payload through the write API and refreshes detail panels', async () => {
    const store = useTaskDetailStore()
    store.taskId = 101
    const api = {
      getConsoleTaskDetail: vi.fn().mockResolvedValue(detail),
      getTaskLogs: vi.fn().mockResolvedValue(logs),
      getTaskTimeline: vi.fn().mockResolvedValue(timeline),
      getTaskAuditLogs: vi.fn().mockResolvedValue(auditLogs),
      retryTask: vi.fn(),
      cancelTask: vi.fn(),
      requeueTask: vi.fn(),
      updateTaskPayload: vi.fn().mockResolvedValue(true),
    }

    await store.updatePayload('{"orderId":"ORD-101"}', api)

    expect(api.updateTaskPayload).toHaveBeenCalledWith(101, '{"orderId":"ORD-101"}')
    expect(api.getConsoleTaskDetail).toHaveBeenCalledWith(101)
    expect(store.operationSuccess).toContain('payload')
  })
})
