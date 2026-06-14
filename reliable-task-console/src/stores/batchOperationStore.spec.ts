import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import type { BatchOperationResult } from '@/api/types'
import { useBatchOperationStore } from './batchOperationStore'

const previewResult: BatchOperationResult = {
  batchOperationId: 501,
  totalCount: 12,
  successCount: 0,
  failCount: 0,
  failedTaskIds: [],
  failedSummary: null,
  dryRun: true,
  success: true,
  errorMsg: null,
}

describe('batch operation store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('previews batch conditions with clamped limit', async () => {
    const store = useBatchOperationStore()
    const api = {
      previewBatch: vi.fn().mockResolvedValue(previewResult),
      batchRequeue: vi.fn(),
      batchCancel: vi.fn(),
    }

    store.setFilter('taskType', 'ORDER_EMAIL')
    store.setFilter('status', 5)
    store.setLimit(500, 100)
    await store.preview(api)

    expect(store.limit).toBe(100)
    expect(api.previewBatch).toHaveBeenCalledWith({
      taskType: 'ORDER_EMAIL',
      status: 5,
      limit: 100,
      dryRun: true,
    })
    expect(store.previewResult).toEqual(previewResult)
  })

  it('requires preview before executing a batch operation', async () => {
    const store = useBatchOperationStore()
    const api = {
      previewBatch: vi.fn(),
      batchRequeue: vi.fn(),
      batchCancel: vi.fn(),
    }

    await store.execute('requeue', api)

    expect(api.batchRequeue).not.toHaveBeenCalled()
    expect(store.error?.message).toContain('Preview required')
  })

  it('executes batch requeue after preview', async () => {
    const store = useBatchOperationStore()
    const executeResult = {
      ...previewResult,
      dryRun: false,
      successCount: 12,
    }
    const api = {
      previewBatch: vi.fn().mockResolvedValue(previewResult),
      batchRequeue: vi.fn().mockResolvedValue(executeResult),
      batchCancel: vi.fn(),
    }

    store.setFilter('status', 5)
    await store.preview(api)
    await store.execute('requeue', api)

    expect(api.batchRequeue).toHaveBeenCalledWith({
      status: 5,
      limit: 20,
      dryRun: false,
    })
    expect(store.executeResult).toEqual(executeResult)
  })
})
