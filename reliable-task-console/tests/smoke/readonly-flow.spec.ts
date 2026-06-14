import { expect, type Page, test } from '@playwright/test'

const success = (data: unknown) => ({
  code: 200,
  message: 'success',
  data,
})

async function mockReliableTaskApi(page: Page) {
  await page.route('**/api/reliable-task/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/api/reliable-task', '')

    if (path === '/console/capabilities') {
      await route.fulfill({
        json: success({
          adminEnabled: true,
          writeEnabled: false,
          authEnabled: true,
          auditEnabled: true,
          batchEnabled: true,
          maxPageSize: 50,
          maxBatchLimit: 100,
          payloadPlaintextEnabled: false,
          payloadRevealAllowed: false,
          payloadPreviewLength: 512,
          writeConfirmationRequired: true,
        }),
      })
      return
    }

    if (path === '/tasks/stats') {
      await route.fulfill({
        json: success({
          statusCount: { 0: 7, 5: 2 },
          totalTasks: 120,
          todayNewTasks: 12,
          todaySuccessTasks: 9,
          todayFailedTasks: 3,
          pendingTasks: 7,
          deadTasks: 2,
          oldestPendingAgeSeconds: 3661,
          taskTypeStats: { ORDER_EMAIL: 2 },
        }),
      })
      return
    }

    if (path === '/tasks/failure-top') {
      await route.fulfill({
        json: success([
          {
            taskType: 'ORDER_EMAIL',
            errorCode: 'SMTP_TIMEOUT',
            failureCount: 5,
            createTimeStart: null,
            createTimeEnd: null,
          },
        ]),
      })
      return
    }

    if (path === '/tasks/recent-failures') {
      await route.fulfill({
        json: success([
          {
            taskId: 42,
            taskType: 'ORDER_EMAIL',
            bizType: 'ORDER',
            bizId: 'ORD-42',
            statusAfter: 'DEAD',
            errorCode: 'SMTP_TIMEOUT',
            errorMsg: 'SMTP timeout',
            durationMs: 1400,
            workerId: 'worker-a',
            traceId: 'trace-42',
            executeTime: '2026-06-14T09:00:00',
          },
        ]),
      })
      return
    }

    if (path === '/tasks') {
      await route.fulfill({
        json: success({
          records: [
            {
              id: 42,
              taskType: 'ORDER_EMAIL',
              bizType: 'ORDER',
              bizId: 'ORD-42',
              statusCode: 5,
              statusDesc: 'dead',
              priority: 10,
              executeCount: 2,
              maxRetryCount: 5,
              errorMsg: 'SMTP timeout',
              workerId: 'worker-a',
              traceId: 'trace-42',
              tenantId: 'tenant-a',
              nextExecuteTime: '2026-06-14T09:10:00',
              createTime: '2026-06-14T09:00:00',
              updateTime: '2026-06-14T09:05:00',
              finishTime: null,
            },
          ],
          total: 1,
          pageNum: 1,
          pageSize: 20,
        }),
      })
      return
    }

    if (path === '/console/tasks/42') {
      await route.fulfill({
        json: success({
          id: 42,
          taskType: 'ORDER_EMAIL',
          bizType: 'ORDER',
          bizId: 'ORD-42',
          bizUniqueKey: 'ORDER:ORD-42',
          statusCode: 5,
          statusDesc: 'dead',
          priority: 10,
          payloadView: {
            payloadVisible: true,
            payloadMasked: true,
            payloadPreview: '{"orderId":"ORD-42","token":"***"}',
            payloadLength: 128,
            payloadRevealAllowed: false,
            payloadPlaintext: '{"token":"plain-secret"}',
          },
          executeCount: 2,
          maxRetryCount: 5,
          retryStrategy: 'FIXED',
          retryIntervalMs: 30000,
          nextExecuteTime: '2026-06-14T09:10:00',
          shardKey: 'order-42',
          tenantId: 'tenant-a',
          workerId: 'worker-a',
          errorMsg: 'SMTP timeout',
          traceId: 'trace-42',
          createTime: '2026-06-14T09:00:00',
          updateTime: '2026-06-14T09:05:00',
          finishTime: null,
        }),
      })
      return
    }

    if (path === '/tasks/42/logs') {
      await route.fulfill({
        json: success([
          {
            id: 201,
            taskId: 42,
            attemptNo: 2,
            statusBefore: 'RUNNING',
            statusAfter: 'DEAD',
            executeTime: '2026-06-14T09:05:00',
            durationMs: 1400,
            status: 5,
            statusDesc: 'dead',
            errorCode: 'SMTP_TIMEOUT',
            errorMsg: 'SMTP timeout',
            workerId: 'worker-a',
            traceId: 'trace-42',
            createTime: '2026-06-14T09:05:01',
          },
        ]),
      })
      return
    }

    if (path === '/tasks/42/timeline') {
      await route.fulfill({
        json: success([
          {
            source: 'LOG',
            eventType: 'TASK_EXECUTION',
            sourceId: 201,
            taskId: 42,
            eventTime: '2026-06-14T09:05:00',
            statusBefore: 'RUNNING',
            statusAfter: 'DEAD',
            statusCode: 5,
            statusDesc: 'dead',
            attemptNo: 2,
            durationMs: 1400,
            errorCode: 'SMTP_TIMEOUT',
            errorMsg: 'SMTP timeout',
            workerId: 'worker-a',
            operator: null,
            operationType: null,
            requestSummary: null,
            result: null,
            traceId: 'trace-42',
          },
        ]),
      })
      return
    }

    if (path === '/tasks/42/audit-logs') {
      await route.fulfill({
        json: success([
          {
            id: 301,
            operationType: 'TASK_RETRY',
            operator: 'ops',
            targetType: 'TASK',
            targetId: '42',
            taskId: 42,
            batchOperationId: null,
            requestSummary: 'retry task',
            result: 'SUCCESS',
            errorMsg: null,
            traceId: 'trace-42',
            createTime: '2026-06-14T09:06:00',
          },
        ]),
      })
      return
    }

    if (path === '/workers') {
      await route.fulfill({
        json: success([
          {
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
          },
        ]),
      })
      return
    }

    if (path === '/workers/stale') {
      await route.fulfill({
        json: success([
          {
            workerId: 'worker-stale',
            appName: 'demo-app',
            hostName: 'host-b',
            ipAddress: '127.0.0.2',
            processId: '12346',
            status: 'STALE',
            runningTaskCount: 0,
            maxConcurrency: 10,
            availableCapacity: 0,
            lastHeartbeatTime: '2026-06-14T08:50:00',
            startTime: '2026-06-14T08:00:00',
          },
        ]),
      })
      return
    }

    if (path === '/audit-logs') {
      await route.fulfill({
        json: success({
          records: [
            {
              id: 301,
              operationType: 'TASK_RETRY',
              operator: 'ops',
              targetType: 'TASK',
              targetId: '42',
              taskId: 42,
              batchOperationId: null,
              requestSummary: 'retry task',
              result: 'SUCCESS',
              errorMsg: null,
              traceId: 'trace-42',
              createTime: '2026-06-14T09:06:00',
            },
          ],
          total: 1,
          pageNum: 1,
          pageSize: 20,
        }),
      })
      return
    }

    await route.fulfill({
      status: 404,
      json: {
        code: 404,
        message: `No smoke mock for ${path}`,
        data: null,
      },
    })
  })
}

test('covers read-only troubleshooting flow', async ({ page }) => {
  await mockReliableTaskApi(page)

  await page.goto('/')
  await expect(page.locator('#dashboard-title')).toBeVisible()
  await expect(page.getByRole('link', { name: /Backlog growth/ })).toBeVisible()

  await page.getByRole('link', { name: /Dead task spike/ }).click()
  await expect(page).toHaveURL(/\/tasks\?status=5/)
  await expect(page.locator('#tasks-title')).toBeVisible()
  await expect(page.getByText('ORDER_EMAIL')).toBeVisible()

  await page.getByRole('link', { name: '#42' }).click()
  await expect(page.getByRole('heading', { name: 'Task #42' })).toBeVisible()
  await expect(page.getByText('Masked preview')).toBeVisible()
  await expect(page.getByText('plain-secret')).toHaveCount(0)
  await expect(page.getByText('TASK_EXECUTION')).toBeVisible()

  await page.getByRole('link', { name: 'Workers' }).click()
  await expect(page.locator('#workers-title')).toBeVisible()
  await expect(page.getByText('worker-stale')).toBeVisible()

  await page.getByRole('link', { name: 'Audit Logs' }).click()
  await expect(page.locator('#audit-logs-title')).toBeVisible()
  await expect(page.getByText('TASK_RETRY')).toBeVisible()
  await expect(page.getByText('trace-42')).toBeVisible()
})
