'use strict'

/**
 * 401 页面单测：锁定无权提示、返回逻辑与禁用回退场景。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Page401 from '@/views/error/401.vue'

jest.mock('@/assets/401_images/401.gif', () => '401.gif')

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountPage401(query = {}) {
  const push = jest.fn()
  const go = jest.fn()
  const wrapper = mount(Page401, {
    localVue,
    stubs: { 'router-link': true },
    mocks: {
      $route: { query },
      $router: { push, go }
    }
  })
  return { wrapper, push, go }
}

describe('Page401', () => {
  test('渲染 401 提示与带版本参数的图片', () => {
    const { wrapper } = mountPage401()

    expect(wrapper.text()).toContain('401错误!')
    expect(wrapper.text()).toContain('您没有访问权限！')
    expect(wrapper.find('img').attributes('src')).toMatch(/^401\.gif\?\d+$/)
  })

  test('默认返回上一页', () => {
    const { wrapper, go, push } = mountPage401()

    wrapper.vm.back()

    expect(go).toHaveBeenCalledWith(-1)
    expect(push).not.toHaveBeenCalled()
  })

  test('禁止回退时回到首页', () => {
    const { wrapper, go, push } = mountPage401({ noGoBack: '1' })

    wrapper.vm.back()

    expect(push).toHaveBeenCalledWith({ path: '/' })
    expect(go).not.toHaveBeenCalled()
  })
})
