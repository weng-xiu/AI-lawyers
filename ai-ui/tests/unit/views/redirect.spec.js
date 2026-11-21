'use strict'

/**
 * Redirect 中转页单测：锁定按路由参数重定向并透传查询条件。
 */
import { mount } from '@vue/test-utils'
import Redirect from '@/views/redirect.vue'

describe('Redirect', () => {
  test('挂载后替换到目标路径并保留 query', () => {
    const replace = jest.fn()
    mount(Redirect, {
      mocks: {
        $route: { params: { path: 'system/user' }, query: { id: '1' } },
        $router: { replace }
      }
    })

    expect(replace).toHaveBeenCalledWith({ path: '/system/user', query: { id: '1' } })
  })
})
