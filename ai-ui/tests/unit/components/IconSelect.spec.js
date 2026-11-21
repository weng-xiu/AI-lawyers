'use strict'

/**
 * IconSelect 图标选择单测：锁定图标过滤、重置与选中事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import IconSelect from '@/components/IconSelect/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

jest.mock('@/components/IconSelect/requireIcons', () => ['user', 'search', 'system'])

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('svg-icon', SvgIcon)

function mountIconSelect(propsData = {}) {
  return mount(IconSelect, { localVue, propsData })
}

describe('IconSelect', () => {
  test('默认渲染全部图标并为当前图标高亮', () => {
    const wrapper = mountIconSelect({ activeIcon: 'search' })

    expect(wrapper.findAll('.icon-item-wrapper')).toHaveLength(3)
    expect(wrapper.find('.icon-item.active span').text()).toBe('search')
  })

  test('按名称过滤图标列表', () => {
    const wrapper = mountIconSelect()

    wrapper.vm.name = 'sys'
    wrapper.vm.filterIcons()

    expect(wrapper.vm.iconList).toEqual(['system'])
  })

  test('清空关键字后恢复全部图标', () => {
    const wrapper = mountIconSelect()

    wrapper.vm.name = 'user'
    wrapper.vm.filterIcons()
    expect(wrapper.vm.iconList).toEqual(['user'])

    wrapper.vm.name = ''
    wrapper.vm.filterIcons()
    expect(wrapper.vm.iconList).toHaveLength(3)
  })

  test('点击图标抛出 selected 事件并关闭浮层', () => {
    const bodyClick = jest.spyOn(document.body, 'click').mockImplementation(() => {})
    const wrapper = mountIconSelect()

    wrapper.findAll('.icon-item-wrapper').at(1).trigger('click')

    expect(wrapper.emitted('selected')).toEqual([['search']])
    expect(bodyClick).toHaveBeenCalledTimes(1)

    bodyClick.mockRestore()
  })

  test('reset 清空关键字并还原列表', () => {
    const wrapper = mountIconSelect()
    wrapper.vm.name = 'us'
    wrapper.vm.filterIcons()

    wrapper.vm.reset()

    expect(wrapper.vm.name).toBe('')
    expect(wrapper.vm.iconList).toHaveLength(3)
  })
})
