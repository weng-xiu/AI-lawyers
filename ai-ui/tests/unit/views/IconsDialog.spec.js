'use strict'

/**
 * IconsDialog 图标选择弹窗单测：锁定当前图标回显、关键字过滤与选择事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import IconsDialog from '@/views/tool/build/IconsDialog.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountDialog(propsData = {}, attrs = {}) {
  return mount(IconsDialog, { localVue, propsData, attrs })
}

describe('IconsDialog', () => {
  test('打开时回显当前图标并清空关键字', async () => {
    const wrapper = mountDialog({ current: 'el-icon-user' })

    wrapper.vm.onOpen()

    expect(wrapper.vm.active).toBe('el-icon-user')
    expect(wrapper.vm.key).toBe('')
  })

  test('按关键字过滤图标列表，清空后恢复全量', async () => {
    const wrapper = mountDialog()
    const total = wrapper.vm.iconList.length

    wrapper.vm.key = 'el-icon-user'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.iconList.length).toBeGreaterThan(0)
    expect(wrapper.vm.iconList.length).toBeLessThan(total)
    expect(wrapper.vm.iconList.every(icon => icon.indexOf('el-icon-user') > -1)).toBe(true)

    wrapper.vm.key = ''
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.iconList).toHaveLength(total)
  })

  test('选择图标后抛出 select 并请求关闭', () => {
    const wrapper = mountDialog()

    wrapper.vm.onSelect('el-icon-search')

    expect(wrapper.vm.active).toBe('el-icon-search')
    expect(wrapper.emitted('select').pop()).toEqual(['el-icon-search'])
    expect(wrapper.emitted('update:visible').pop()).toEqual([false])
  })

  test('可见时渲染图标列表并高亮当前项', async () => {
    const wrapper = mountDialog({ current: 'el-icon-user' }, { visible: true })
    wrapper.vm.onOpen()
    await wrapper.vm.$nextTick()

    const items = wrapper.findAll('.icon-ul li')
    expect(items.length).toBeGreaterThan(100)
    expect(wrapper.findAll('.active-item')).toHaveLength(1)
    expect(wrapper.find('.active-item').text()).toContain('el-icon-user')
  })
})
