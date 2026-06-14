import { describe, expect, it, vi } from 'vitest'

import { ApiClientError, createAdminApiClient } from './client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json',
    },
  })
}

describe('admin api client', () => {
  it('unwraps successful Result envelopes and sends the operator header', async () => {
    const fetcher = vi.fn().mockResolvedValue(
      jsonResponse({
        code: 200,
        message: 'success',
        data: {
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
        },
      }),
    )

    const client = createAdminApiClient({
      baseUrl: '/api/reliable-task',
      fetcher,
      operatorProvider: () => 'ops-user',
    })

    const capabilities = await client.getConsoleCapabilities()

    expect(capabilities.adminEnabled).toBe(true)
    expect(fetcher).toHaveBeenCalledTimes(1)
    const [url, init] = fetcher.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/reliable-task/console/capabilities')
    const headers = new Headers(init.headers)
    expect(headers.get('X-Operator')).toBe('ops-user')
    expect(headers.get('Accept')).toBe('application/json')
  })

  it('rejects business errors when Result code is not 200', async () => {
    const fetcher = vi.fn().mockResolvedValue(
      jsonResponse({
        code: 403,
        message: 'admin access denied',
        data: null,
      }),
    )
    const client = createAdminApiClient({ fetcher })

    await expect(client.getConsoleCapabilities()).rejects.toMatchObject({
      kind: 'business',
      code: 403,
      message: 'admin access denied',
    })
  })

  it('classifies fetch failures as network errors', async () => {
    const fetcher = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    const client = createAdminApiClient({ fetcher })

    await expect(client.getConsoleCapabilities()).rejects.toMatchObject({
      kind: 'network',
      message: 'Failed to fetch',
    })
  })

  it('adds trace and confirmation headers for reserved write requests', async () => {
    const fetcher = vi.fn().mockResolvedValue(
      jsonResponse({
        code: 200,
        message: 'success',
        data: true,
      }),
    )
    const client = createAdminApiClient({
      fetcher,
      operatorProvider: () => 'ops-user',
      traceIdFactory: () => 'trace-1',
    })

    const result = await client.post<boolean>('/tasks/1/retry', undefined, {
      write: true,
      confirmOperation: true,
    })

    expect(result).toBe(true)
    const [, init] = fetcher.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.get('X-Operator')).toBe('ops-user')
    expect(headers.get('X-Trace-Id')).toBe('trace-1')
    expect(headers.get('X-Confirm-Operation')).toBe('true')
  })

  it('exposes ApiClientError for explicit error checks', () => {
    const error = new ApiClientError({
      kind: 'business',
      code: 404,
      message: 'Task not found',
    })

    expect(error).toBeInstanceOf(Error)
    expect(error.code).toBe(404)
  })
})
