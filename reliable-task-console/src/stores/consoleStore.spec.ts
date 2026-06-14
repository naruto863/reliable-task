import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { ApiClientError } from '@/api/client'
import type { ConsoleCapabilities } from '@/api/types'
import { useConsoleStore } from './consoleStore'

const capabilities: ConsoleCapabilities = {
  adminEnabled: true,
  writeEnabled: false,
  authEnabled: true,
  auditEnabled: false,
  batchEnabled: true,
  maxPageSize: 200,
  maxBatchLimit: 1000,
  payloadPlaintextEnabled: false,
  payloadRevealAllowed: false,
  payloadPreviewLength: 512,
  writeConfirmationRequired: true,
}

describe('console store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('loads and stores console capabilities', async () => {
    const store = useConsoleStore()
    const api = {
      getConsoleCapabilities: vi.fn().mockResolvedValue(capabilities),
    }

    await store.loadCapabilities(api)

    expect(store.capabilities).toEqual(capabilities)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('maps 403 business errors to forbidden UI state', async () => {
    const store = useConsoleStore()
    const api = {
      getConsoleCapabilities: vi.fn().mockRejectedValue(
        new ApiClientError({
          kind: 'business',
          code: 403,
          message: 'admin access denied',
        }),
      ),
    }

    await store.loadCapabilities(api)

    expect(store.capabilities).toBeNull()
    expect(store.loading).toBe(false)
    expect(store.error).toMatchObject({
      kind: 'forbidden',
      statusCode: 403,
      message: 'admin access denied',
    })
  })

  it('normalizes blank operators to the local demo default', () => {
    const store = useConsoleStore()

    store.setOperator('  ops-user  ')
    expect(store.operator).toBe('ops-user')

    store.setOperator('   ')
    expect(store.operator).toBe('local-demo')
  })
})
