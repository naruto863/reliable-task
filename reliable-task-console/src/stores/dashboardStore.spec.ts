import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { ApiClientError } from '@/api/client'
import type { FailureTop, TaskFailure, TaskStats } from '@/api/types'
import { useDashboardStore } from './dashboardStore'

const stats: TaskStats = {
  statusCount: {
    0: 7,
    3: 111,
    5: 2,
  },
  totalTasks: 120,
  todayNewTasks: 12,
  todaySuccessTasks: 9,
  todayFailedTasks: 3,
  pendingTasks: 7,
  deadTasks: 2,
  oldestPendingAgeSeconds: 3661,
  taskTypeStats: {
    EMAIL: 80,
    SHIPMENT: 40,
  },
}

const failureTop: FailureTop[] = [
  {
    taskType: 'EMAIL',
    errorCode: 'SMTP_TIMEOUT',
    failureCount: 5,
    createTimeStart: '2026-06-13T00:00:00',
    createTimeEnd: '2026-06-13T22:00:00',
  },
]

const recentFailures: TaskFailure[] = [
  {
    taskId: 42,
    taskType: 'SHIPMENT',
    bizType: 'ORDER',
    bizId: 'ORD-42',
    statusAfter: 'DEAD',
    errorCode: 'REMOTE_500',
    errorMsg: 'carrier failed',
    durationMs: 1400,
    workerId: 'worker-a',
    traceId: 'trace-42',
    executeTime: '2026-06-13T21:30:00',
  },
]

describe('dashboard store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads stats, failure top, and recent failures with bounded defaults', async () => {
    const store = useDashboardStore()
    const api = {
      getTaskStats: vi.fn().mockResolvedValue(stats),
      listFailureTop: vi.fn().mockResolvedValue(failureTop),
      listRecentFailures: vi.fn().mockResolvedValue(recentFailures),
    }

    await store.loadDashboard(api)

    expect(store.stats).toEqual(stats)
    expect(store.failureTop).toEqual(failureTop)
    expect(store.recentFailures).toEqual(recentFailures)
    expect(api.listFailureTop).toHaveBeenCalledWith({
      groupBy: 'taskType,errorCode',
      limit: 5,
    })
    expect(api.listRecentFailures).toHaveBeenCalledWith({ limit: 5 })
  })

  it('keeps the app shell alive when dashboard APIs fail', async () => {
    const store = useDashboardStore()
    const api = {
      getTaskStats: vi.fn().mockRejectedValue(
        new ApiClientError({
          kind: 'network',
          message: 'Failed to fetch',
        }),
      ),
      listFailureTop: vi.fn(),
      listRecentFailures: vi.fn(),
    }

    await store.loadDashboard(api)

    expect(store.loading).toBe(false)
    expect(store.error).toMatchObject({
      kind: 'network',
      title: 'API unreachable',
    })
  })
})
