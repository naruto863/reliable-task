import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { router } from '@/router'
import { useAuditLogStore } from '@/stores/auditLogStore'
import AuditLogsView from './AuditLogsView.vue'

describe('AuditLogsView', () => {
  it('renders audit rows with operator, result, pagination, and trace id', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAuditLogStore()
    store.result = {
      records: [
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
          createTime: '2026-06-14T09:00:00',
        },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 20,
    }
    await router.push('/audit-logs')
    await router.isReady()

    const wrapper = mount(AuditLogsView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('TASK_RETRY')
    expect(wrapper.text()).toContain('ops')
    expect(wrapper.text()).toContain('SUCCESS')
    expect(wrapper.text()).toContain('trace-101')
    expect(wrapper.text()).toContain('Total 1')
  })

  it('renders audit disabled errors clearly', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAuditLogStore()
    store.error = {
      kind: 'notFound',
      title: 'Resource not found',
      message: 'Audit log is disabled',
      statusCode: 404,
    }
    await router.push('/audit-logs')
    await router.isReady()

    const wrapper = mount(AuditLogsView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('Resource not found')
    expect(wrapper.text()).toContain('Audit log is disabled')
  })
})
