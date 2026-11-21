'use strict'

/**
 * RightToolbar 表格工具栏单测：锁定搜索/刷新事件、列显隐切换与全选逻辑。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import RightToolbar from '@/components/RightToolbar/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountToolbar(propsData = {}) {
  return mount(RightToolbar, {
    localVue,
    propsData: {
      columns: [
        { key: 'name', label: '姓名', visible: true },
        { key: 'phone', label: '电话', visible: false }
      ],
      ...propsData
    }
  })
}

describe('RightToolbar', () => {
  test('按 gutter 计算右外边距', () => {
    const wrapper = mountToolbar({ gutter: 20 })

    expect(wrapper.vm.style).toEqual({ marginRight: '10px' })
  })

  test('点击搜索按钮切换搜索区显示状态', async () => {
    const wrapper = mountToolbar({ showSearch: true })

    await wrapper.findAllComponents({ name: 'ElButton' }).at(0).vm.$emit('click')

    expect(wrapper.emitted('update:showSearch')).toEqual([[false]])
  })

  test('点击刷新按钮抛出 queryTable 事件', async () => {
    const wrapper = mountToolbar({ search: false })

    await wrapper.findAllComponents({ name: 'ElButton' }).at(0).vm.$emit('click')

    expect(wrapper.emitted('queryTable')).toHaveLength(1)
  })

  test('穿梭框模式初始化隐藏列并支持打开弹层', () => {
    const wrapper = mountToolbar({ showColumnsType: 'transfer' })

    expect(wrapper.vm.value).toEqual([1])

    wrapper.vm.showColumn()
    expect(wrapper.vm.open).toBe(true)
  })

  test('穿梭框数据变化时反向更新列显隐', () => {
    const wrapper = mountToolbar({ showColumnsType: 'transfer' })

    wrapper.vm.dataChange(['phone'])

    expect(wrapper.vm.columns[0].visible).toBe(true)
    expect(wrapper.vm.columns[1].visible).toBe(false)
  })

  test('复选框模式按 label 更新单列显隐', () => {
    const wrapper = mountToolbar()

    wrapper.vm.checkboxChange(true, '电话')

    expect(wrapper.vm.columns[1].visible).toBe(true)
    expect(wrapper.vm.isChecked).toBe(true)
    expect(wrapper.vm.isIndeterminate).toBe(false)
  })

  test('全选按钮在部分选中时判为不确定态并可全部隐藏', () => {
    const wrapper = mountToolbar()

    expect(wrapper.vm.isChecked).toBe(false)
    expect(wrapper.vm.isIndeterminate).toBe(true)

    wrapper.vm.toggleCheckAll()

    expect(wrapper.vm.columns.map(col => col.visible)).toEqual([true, true])
    expect(wrapper.vm.isChecked).toBe(true)

    wrapper.vm.toggleCheckAll()

    expect(wrapper.vm.columns.map(col => col.visible)).toEqual([false, false])
  })
})
