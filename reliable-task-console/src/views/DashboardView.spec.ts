import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { router } from '@/router'
import { useDashboardStore } from '@/stores/dashboardStore'
import DashboardView from './DashboardView.vue'

describe('DashboardView', () => {
  it('renders stats, failure top, and recent failure entries', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const dashboard = useDashboardStore()

    dashboard.stats = {
      statusCount: {},
      totalTasks: 120,
      todayNewTasks: 12,
      todaySuccessTasks: 9,
      todayFailedTasks: 3,
      pendingTasks: 7,
      deadTasks: 2,
      oldestPendingAgeSeconds: 3661,
      taskTypeStats: {},
    }
    dashboard.failureTop = [
      {
        taskType: 'EMAIL',
        errorCode: 'SMTP_TIMEOUT',
        failureCount: 5,
        createTimeStart: null,
        createTimeEnd: null,
      },
    ]
    dashboard.recentFailures = [
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
    await router.push('/')
    await router.isReady()

    const wrapper = mount(DashboardView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('120')
    expect(wrapper.text()).toContain('7')
    expect(wrapper.text()).toContain('1h 1m')
    expect(wrapper.text()).toContain('EMAIL')
    expect(wrapper.text()).toContain('SMTP_TIMEOUT')
    expect(wrapper.text()).toContain('SHIPMENT')
    expect(wrapper.text()).toContain('carrier failed')
    expect(wrapper.find('a[href="/tasks/42"]').exists()).toBe(true)
  })

  it('renders runbook troubleshooting paths', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push('/')
    await router.isReady()

    const wrapper = mount(DashboardView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('Backlog growth')
    expect(wrapper.text()).toContain('Dead task spike')
    expect(wrapper.text()).toContain('Retry storm')
    expect(wrapper.text()).toContain('Worker stale or missing')
    expect(wrapper.find('a[href="/tasks?status=0"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/tasks?status=5"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/tasks?status=4"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/workers"]').exists()).toBe(true)
  })
})
