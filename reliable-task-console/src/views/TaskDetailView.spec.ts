import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { router } from '@/router'
import { useConsoleStore } from '@/stores/consoleStore'
import { useTaskDetailStore } from '@/stores/taskDetailStore'
import TaskDetailView from './TaskDetailView.vue'

function seedDetailStore() {
  const store = useTaskDetailStore()
  store.detail = {
    id: 101,
    taskType: 'ORDER_EMAIL',
    bizType: 'ORDER',
    bizId: 'ORD-101',
    bizUniqueKey: 'ORDER:ORD-101',
    statusCode: 5,
    statusDesc: 'dead',
    priority: 10,
    payloadView: {
      payloadVisible: true,
      payloadMasked: true,
      payloadPreview: '{"orderId":"ORD-101","token":"***"}',
      payloadLength: 256,
      payloadRevealAllowed: false,
      payloadPlaintext: '{"orderId":"ORD-101","token":"plain-secret"}',
    },
    executeCount: 2,
    maxRetryCount: 5,
    retryStrategy: 'FIXED',
    retryIntervalMs: 30000,
    nextExecuteTime: '2026-06-13T22:30:00',
    shardKey: 'order-101',
    tenantId: 'tenant-a',
    workerId: 'worker-a',
    errorMsg: 'SMTP timeout while sending a notification email',
    traceId: 'trace-101',
    createTime: '2026-06-13T22:00:00',
    updateTime: '2026-06-13T22:10:00',
    finishTime: null,
  }
  store.logs = [
    {
      id: 201,
      taskId: 101,
      attemptNo: 2,
      statusBefore: 'RUNNING',
      statusAfter: 'DEAD',
      executeTime: '2026-06-13T22:10:00',
      durationMs: 1500,
      status: 5,
      statusDesc: 'dead',
      errorCode: 'SocketTimeoutException',
      errorMsg: 'SMTP timeout',
      workerId: 'worker-a',
      traceId: 'trace-101',
      createTime: '2026-06-13T22:10:01',
    },
  ]
  store.timeline = [
    {
      source: 'LOG',
      eventType: 'TASK_EXECUTION',
      sourceId: 201,
      taskId: 101,
      eventTime: '2026-06-13T22:10:00',
      statusBefore: 'RUNNING',
      statusAfter: 'DEAD',
      statusCode: 5,
      statusDesc: 'dead',
      attemptNo: 2,
      durationMs: 1500,
      errorCode: 'SocketTimeoutException',
      errorMsg: 'SMTP timeout',
      workerId: 'worker-a',
      operator: null,
      operationType: null,
      requestSummary: null,
      result: null,
      traceId: 'trace-101',
    },
  ]
  store.auditLogs = [
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
      createTime: '2026-06-13T22:11:00',
    },
  ]
  return store
}

function setCapabilities(writeEnabled: boolean) {
  const consoleStore = useConsoleStore()
  consoleStore.capabilities = {
    adminEnabled: true,
    writeEnabled,
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
}

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text() === text)
  if (!button) {
    throw new Error(`Button not found: ${text}`)
  }
  return button
}

describe('TaskDetailView', () => {
  it('renders console-safe task detail and hides plaintext payload by default', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    seedDetailStore()
    await router.push('/tasks/101')
    await router.isReady()

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('ORDER_EMAIL')
    expect(wrapper.text()).toContain('DEAD')
    expect(wrapper.text()).toContain('trace-101')
    expect(wrapper.text()).toContain('Masked preview')
    expect(wrapper.text()).toContain('"token":"***"')
    expect(wrapper.text()).not.toContain('plain-secret')
    expect(wrapper.text()).toContain('Reveal disabled')
  })

  it('renders execution logs, timeline, and task audit entries', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    seedDetailStore()
    await router.push('/tasks/101')
    await router.isReady()

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('Attempt 2')
    expect(wrapper.text()).toContain('SocketTimeoutException')
    expect(wrapper.text()).toContain('TASK_EXECUTION')
    expect(wrapper.text()).toContain('TASK_RETRY')
    expect(wrapper.text()).toContain('ops')
  })

  it('renders empty states for logs, timeline, and audit logs', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    seedDetailStore()
    const store = useTaskDetailStore()
    store.logs = []
    store.timeline = []
    store.auditLogs = []
    await router.push('/tasks/101')
    await router.isReady()

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('No execution logs')
    expect(wrapper.text()).toContain('No timeline events')
    expect(wrapper.text()).toContain('No task audit logs')
  })

  it('renders a not found state when the console-safe detail is missing', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useTaskDetailStore()
    store.error = {
      kind: 'notFound',
      title: 'Resource not found',
      message: 'Task not found: 404',
      statusCode: 404,
    }
    await router.push('/tasks/404')
    await router.isReady()

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('Resource not found')
    expect(wrapper.text()).toContain('Task not found: 404')
  })

  it('disables write actions when capabilities are not safe', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    seedDetailStore()
    setCapabilities(false)
    await router.push('/tasks/101')
    await router.isReady()

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('Write actions unavailable')
    expect(buttonByText(wrapper, 'Retry').attributes('disabled')).toBeDefined()
    expect(buttonByText(wrapper, 'Cancel').attributes('disabled')).toBeDefined()
  })

  it('requires explicit confirmation before retrying a task', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = seedDetailStore()
    setCapabilities(true)
    store.runTaskOperation = vi.fn().mockResolvedValue(undefined)
    await router.push('/tasks/101')
    await router.isReady()

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [pinia, router],
      },
    })

    await buttonByText(wrapper, 'Retry').trigger('click')
    expect(wrapper.text()).toContain('Confirm retry')
    expect(store.runTaskOperation).not.toHaveBeenCalled()

    await wrapper.get('input[aria-label="Confirmation text"]').setValue('CONFIRM')
    await buttonByText(wrapper, 'Confirm operation').trigger('click')

    expect(store.runTaskOperation).toHaveBeenCalledWith('retry')
  })

  it('validates update payload JSON before sending a write request', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = seedDetailStore()
    setCapabilities(true)
    store.detail!.statusCode = 0
    store.detail!.statusDesc = 'pending'
    store.updatePayload = vi.fn().mockResolvedValue(undefined)
    await router.push('/tasks/101')
    await router.isReady()

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [pinia, router],
      },
    })

    await buttonByText(wrapper, 'Update payload').trigger('click')
    await wrapper.get('textarea[aria-label="New payload JSON"]').setValue('{bad json')
    await wrapper.get('input[aria-label="Confirmation text"]').setValue('CONFIRM')
    await buttonByText(wrapper, 'Confirm operation').trigger('click')

    expect(wrapper.text()).toContain('Payload must be valid JSON')
    expect(store.updatePayload).not.toHaveBeenCalled()
  })
})
