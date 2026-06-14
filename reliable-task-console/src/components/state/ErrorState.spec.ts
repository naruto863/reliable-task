import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ErrorState from './ErrorState.vue'
import ForbiddenState from './ForbiddenState.vue'

describe('state components', () => {
  it('renders network errors with a retry action', async () => {
    const wrapper = mount(ErrorState, {
      props: {
        state: {
          kind: 'network',
          title: 'API unreachable',
          message: 'Check the Vite proxy target.',
        },
      },
    })

    expect(wrapper.text()).toContain('API unreachable')
    expect(wrapper.text()).toContain('Check the Vite proxy target.')

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('renders forbidden errors as an access state', () => {
    const wrapper = mount(ForbiddenState, {
      props: {
        message: 'admin access denied',
      },
    })

    expect(wrapper.text()).toContain('Access denied')
    expect(wrapper.text()).toContain('admin access denied')
  })
})
