import { expect, test } from '@playwright/test'

test('renders the console shell', async ({ page }) => {
  await page.route('**/api/reliable-task/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/api/reliable-task', '')
    const payload = {
      code: 200,
      message: 'success',
      data:
        path === '/console/capabilities'
          ? {
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
            }
          : path === '/tasks/stats'
            ? {
                statusCount: {},
                totalTasks: 0,
                todayNewTasks: 0,
                todaySuccessTasks: 0,
                todayFailedTasks: 0,
                pendingTasks: 0,
                deadTasks: 0,
                oldestPendingAgeSeconds: 0,
                taskTypeStats: {},
              }
            : [],
    }

    await route.fulfill({ json: payload })
  })

  await page.goto('/')

  await expect(page.getByRole('link', { name: 'ReliableTask Console' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Tasks', exact: true })).toBeVisible()
  await expect(page.locator('#dashboard-title')).toBeVisible()
})
