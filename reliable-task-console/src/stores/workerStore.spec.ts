import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { ApiClientError } from '@/api/client'
import type { WorkerHeartbeat } from '@/api/types'
import { useWorkerStore } from './workerStore'

const worker: WorkerHeartbeat = {
  workerId: 'worker-a',
  appName: 'demo-app',
  hostName: 'host-a',
  ipAddress: '127.0.0.1',
  processId: '12345',
  status: 'ONLINE',
  runningTaskCount: 3,
  maxConcurrency: 10,
  availableCapacity: 7,
  lastHeartbeatTime: '2026-06-14T09:00:00',
  startTime: '2026-06-14T08:00:00',
}

describe('worker store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads workers and stale workers', async () => {
    const store = useWorkerStore()
    const api = {
      listWorkers: vi.fn().mockResolvedValue([worker]),
      listStaleWorkers: vi.fn().mockResolvedValue([{ ...worker, workerId: 'worker-stale' }]),
    }

    await store.loadWorkers(api)

    expect(api.listWorkers).toHaveBeenCalled()
    expect(api.listStaleWorkers).toHaveBeenCalled()
    expect(store.workers).toHaveLength(1)
    expect(store.staleWorkers).toHaveLength(1)
    expect(store.totalCapacity).toBe(10)
    expect(store.availableCapacity).toBe(7)
  })

  it('records worker API errors', async () => {
    const store = useWorkerStore()
    const api = {
      listWorkers: vi.fn().mockRejectedValue(
        new ApiClientError({
          kind: 'network',
          message: 'Failed to fetch',
        }),
      ),
      listStaleWorkers: vi.fn(),
    }

    await store.loadWorkers(api)

    expect(store.error?.kind).toBe('network')
    expect(api.listStaleWorkers).not.toHaveBeenCalled()
  })
})
