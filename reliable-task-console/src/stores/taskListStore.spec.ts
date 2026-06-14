import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import type { PageResult, TaskSummary } from '@/api/types'
import { useTaskListStore } from './taskListStore'

const task: TaskSummary = {
  id: 101,
  taskType: 'ORDER_EMAIL',
  bizType: 'ORDER',
  bizId: 'ORD-101',
  statusCode: 5,
  statusDesc: '死亡/需人工干预',
  priority: 10,
  executeCount: 2,
  maxRetryCount: 5,
  errorMsg: 'SMTP timeout',
  workerId: 'worker-a',
  traceId: 'trace-101',
  tenantId: 'tenant-a',
  nextExecuteTime: '2026-06-13T22:30:00',
  createTime: '2026-06-13T22:00:00',
  updateTime: '2026-06-13T22:10:00',
  finishTime: null,
}

const page: PageResult<TaskSummary> = {
  records: [task],
  total: 1,
  pageNum: 1,
  pageSize: 20,
}

describe('task list store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads tasks with filters and pagination', async () => {
    const store = useTaskListStore()
    const api = {
      listTasks: vi.fn().mockResolvedValue(page),
    }

    store.setFilter('status', 5)
    store.setFilter('taskType', 'ORDER_EMAIL')
    store.setFilter('bizId', 'ORD-101')
    await store.loadTasks(api)

    expect(api.listTasks).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 20,
      status: 5,
      taskType: 'ORDER_EMAIL',
      bizId: 'ORD-101',
    })
    expect(store.records).toEqual([task])
    expect(store.total).toBe(1)
  })

  it('clamps page size to the configured max page size', () => {
    const store = useTaskListStore()

    store.setPageSize(500, 100)

    expect(store.pageSize).toBe(100)
    expect(store.pageNum).toBe(1)
  })

  it('serializes filters to route query without empty fields', () => {
    const store = useTaskListStore()

    store.setFilter('taskType', 'ORDER_EMAIL')
    store.setFilter('workerId', '   ')
    store.setPage(3)

    expect(store.toRouteQuery()).toEqual({
      pageNum: '3',
      pageSize: '20',
      taskType: 'ORDER_EMAIL',
    })
  })

  it('applies route query using the configured max page size', () => {
    const store = useTaskListStore()

    store.applyRouteQuery(
      {
        pageNum: '2',
        pageSize: '500',
        status: '4',
        traceId: 'trace-007',
        createTimeStart: '2026-06-13T20:00',
      },
      50,
    )

    expect(store.pageNum).toBe(2)
    expect(store.pageSize).toBe(50)
    expect(store.filters.status).toBe(4)
    expect(store.filters.traceId).toBe('trace-007')
    expect(store.filters.createTimeStart).toBe('2026-06-13T20:00')
  })
})
