import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Layout, Menu, Tag } from 'ant-design-vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import { router } from './router'

describe('App shell', () => {
  it('renders the console navigation', async () => {
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router, Layout, Menu, Tag],
      },
    })

    expect(wrapper.text()).toContain('ReliableTask Console')
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).toContain('Tasks')
    expect(wrapper.text()).toContain('Workers')
    expect(wrapper.text()).toContain('Audit Logs')
  })
})
