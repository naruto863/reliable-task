import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { router } from '@/router'
import { useBatchOperationStore } from '@/stores/batchOperationStore'
import { useConsoleStore } from '@/stores/consoleStore'
import { useTaskListStore } from '@/stores/taskListStore'
import TasksView from './TasksView.vue'

function setCapabilities(writeEnabled: boolean, batchEnabled = true) {
  const consoleStore = useConsoleStore()
  consoleStore.capabilities = {
    adminEnabled: true,
    writeEnabled,
    authEnabled: true,
    auditEnabled: true,
    batchEnabled,
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

describe('TasksView', () => {
  it('renders task rows with status, retry count, worker, and safe error summary', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useTaskListStore()
    store.result = {
      records: [
        {
          id: 101,
          taskType: 'ORDER_EMAIL',
          bizType: 'ORDER',
          bizId: 'ORD-101',
          statusCode: 5,
          statusDesc: '死亡/需人工干预',
          priority: 10,
          executeCount: 2,
          maxRetryCount: 5,
          errorMsg: 'SMTP timeout while sending a notification email',
          workerId: 'worker-a',
          traceId: 'trace-101',
          tenantId: 'tenant-a',
          nextExecuteTime: '2026-06-13T22:30:00',
          createTime: '2026-06-13T22:00:00',
          updateTime: '2026-06-13T22:10:00',
          finishTime: null,
        },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 20,
    }
    await router.push('/tasks')
    await router.isReady()

    const wrapper = mount(TasksView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('ORDER_EMAIL')
    expect(wrapper.text()).toContain('DEAD')
    expect(wrapper.text()).toContain('2 / 5')
    expect(wrapper.text()).toContain('worker-a')
    expect(wrapper.text()).toContain('SMTP timeout')
    expect(wrapper.find('a[href="/tasks/101"]').exists()).toBe(true)
  })

  it('renders an empty state when no task matches the filters', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useTaskListStore()
    store.result = {
      records: [],
      total: 0,
      pageNum: 1,
      pageSize: 20,
    }
    await router.push('/tasks')
    await router.isReady()

    const wrapper = mount(TasksView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('No tasks')
    expect(wrapper.text()).toContain('No task records match the current filters.')
  })

  it('renders forbidden and network error states clearly', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useTaskListStore()
    store.error = {
      kind: 'forbidden',
      title: 'Access denied',
      message: 'permission denied',
      statusCode: 403,
    }
    await router.push('/tasks')
    await router.isReady()

    const wrapper = mount(TasksView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('Access denied')
    expect(wrapper.text()).toContain('permission denied')

    store.error = {
      kind: 'network',
      title: 'API unreachable',
      message: 'Failed to fetch',
    }
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('API unreachable')
    expect(wrapper.text()).toContain('Failed to fetch')
  })

  it('restores filters from URL query and clamps page size by capabilities', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const consoleStore = useConsoleStore()
    const store = useTaskListStore()
    consoleStore.capabilities = {
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
    await router.push({
      name: 'tasks',
      query: {
        pageNum: '3',
        pageSize: '500',
        status: '5',
        taskType: 'ORDER_EMAIL',
      },
    })
    await router.isReady()

    mount(TasksView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(store.pageNum).toBe(3)
    expect(store.pageSize).toBe(50)
    expect(store.filters.status).toBe(5)
    expect(store.filters.taskType).toBe('ORDER_EMAIL')
  })

  it('disables batch operations when capabilities are unsafe', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    setCapabilities(false)
    await router.push('/tasks')
    await router.isReady()

    const wrapper = mount(TasksView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('Batch operations unavailable')
    expect(buttonByText(wrapper, 'Preview batch').attributes('disabled')).toBeDefined()
  })

  it('requires preview and confirmation before executing batch requeue', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    setCapabilities(true)
    const batchStore = useBatchOperationStore()
    batchStore.preview = vi.fn().mockImplementation(async () => {
      batchStore.previewResult = {
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
    })
    batchStore.execute = vi.fn().mockResolvedValue(undefined)
    await router.push('/tasks')
    await router.isReady()

    const wrapper = mount(TasksView, {
      global: {
        plugins: [pinia, router],
      },
    })

    await buttonByText(wrapper, 'Requeue DEAD').trigger('click')
    expect(batchStore.execute).not.toHaveBeenCalled()

    await buttonByText(wrapper, 'Preview batch').trigger('click')
    expect(batchStore.preview).toHaveBeenCalled()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('12 matched')

    await buttonByText(wrapper, 'Requeue DEAD').trigger('click')
    await wrapper.get('input[aria-label="Batch confirmation text"]').setValue('CONFIRM')
    await buttonByText(wrapper, 'Confirm batch operation').trigger('click')

    expect(batchStore.execute).toHaveBeenCalledWith('requeue')
  })
})
