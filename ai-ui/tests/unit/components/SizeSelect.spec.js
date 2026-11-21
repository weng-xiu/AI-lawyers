'use strict'

/**
 * SizeSelect 尺寸切换单测：锁定选项禁用态、store 同步与切换后刷新视图。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import SizeSelect from '@/components/SizeSelect/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('svg-icon', SvgIcon)

function mountSizeSelect(size = 'small') {
  const dispatch = jest.fn()
  const replace = jest.fn()
  const message = jest.fn()
  const $ELEMENT = { size: '' }
  const $route = { fullPath: '/system/user' }

  const wrapper = mount(SizeSelect, {
    localVue,
    mocks: {
      $store: { getters: { size }, dispatch },
      $route,
      $router: { replace },
      $ELEMENT,
      $message: message
    }
  })

  return { wrapper, dispatch, replace, message, $ELEMENT, $route }
}

describe('SizeSelect', () => {
  test('渲染四个尺寸选项并按 store 禁用当前尺寸', () => {
    const { wrapper } = mountSizeSelect('small')

    expect(wrapper.vm.sizeOptions).toHaveLength(4)
    expect(wrapper.vm.size).toBe('small')

    const items = wrapper.findAll('.el-dropdown-menu__item')
    expect(items).toHaveLength(4)
    expect(items.at(2).classes()).toContain('is-disabled')
    expect(items.at(0).classes()).not.toContain('is-disabled')
  })

  test('选择尺寸后同步 UI 库、store 并提示成功', async () => {
    const { wrapper, dispatch, message, $ELEMENT } = mountSizeSelect('small')

    wrapper.vm.handleSetSize('mini')

    expect($ELEMENT.size).toBe('mini')
    expect(dispatch).toHaveBeenCalledWith('app/setSize', 'mini')
    expect(message).toHaveBeenCalledWith({ message: 'Switch Size Success', type: 'success' })
  })

  test('切换尺寸时清理缓存并跳转 redirect 路由', async () => {
    const { wrapper, dispatch, replace, $route } = mountSizeSelect('small')

    wrapper.vm.handleSetSize('medium')
    await wrapper.vm.$nextTick()

    expect(dispatch).toHaveBeenCalledWith('tagsView/delAllCachedViews', $route)
    expect(replace).toHaveBeenCalledWith({ path: '/redirect/system/user' })
  })
})
