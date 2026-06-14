import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { router } from '@/router'
import { useWorkerStore } from '@/stores/workerStore'
import WorkersView from './WorkersView.vue'

describe('WorkersView', () => {
  it('renders worker capacity and stale workers with nullable fields', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useWorkerStore()
    store.workers = [
      {
        workerId: 'worker-a',
        appName: 'demo-app',
        hostName: null,
        ipAddress: null,
        processId: null,
        status: 'ONLINE',
        runningTaskCount: 3,
        maxConcurrency: 10,
        availableCapacity: 7,
        lastHeartbeatTime: '2026-06-14T09:00:00',
        startTime: '2026-06-14T08:00:00',
      },
    ]
    store.staleWorkers = [
      {
        ...store.workers[0],
        workerId: 'worker-stale',
        status: 'STALE',
      },
    ]
    await router.push('/workers')
    await router.isReady()

    const wrapper = mount(WorkersView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('worker-a')
    expect(wrapper.text()).toContain('demo-app')
    expect(wrapper.text()).toContain('3 / 10')
    expect(wrapper.text()).toContain('7 available')
    expect(wrapper.text()).toContain('worker-stale')
    expect(wrapper.text()).toContain('Stale workers')
  })

  it('renders an empty worker state', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push('/workers')
    await router.isReady()

    const wrapper = mount(WorkersView, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('No workers')
  })
})
