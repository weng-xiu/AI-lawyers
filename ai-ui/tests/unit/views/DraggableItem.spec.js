'use strict'

/**
 * 拖拽表单项单测：锁定单列表单项与行容器的渲染、选中态样式及复制/删除事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import DraggableItem from '@/views/tool/build/DraggableItem.vue'

jest.mock('vuedraggable', () => ({
  __esModule: true,
  default: {
    name: 'Draggable',
    props: ['list', 'animation', 'group'],
    render(h) {
      return h('div', { class: 'draggable-stub' }, this.$slots.default)
    }
  }
}))

jest.mock('@/utils/generator/render', () => ({
  __esModule: true,
  default: {
    name: 'Render',
    props: ['conf'],
    render(h) {
      return h('div', { class: 'render-stub' })
    }
  }
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const colElement = {
  layout: 'colFormItem',
  formId: 'input1',
  span: 12,
  label: '姓名',
  labelWidth: 100,
  required: true,
  renderKey: 1,
  defaultValue: ''
}

function mountItem(element, extraProps = {}, listeners = {}) {
  const drawingList = [element]
  const wrapper = mount(DraggableItem, {
    localVue,
    propsData: {
      element,
      index: 0,
      drawingList,
      activeId: '',
      formConf: { unFocusedComponentBorder: false },
      ...extraProps
    },
    listeners: {
      activeItem: jest.fn(),
      copyItem: jest.fn(),
      deleteItem: jest.fn(),
      ...listeners
    },
    stubs: {
      'el-form-item': {
        name: 'ElFormItem',
        props: ['label', 'labelWidth', 'required'],
        template: '<div class="form-item-stub">{{ label }}<slot /></div>'
      }
    }
  })
  return { wrapper, drawingList }
}

describe('DraggableItem 可拖拽表单项', () => {
  test('单列表单项渲染标签与动态组件', () => {
    const { wrapper } = mountItem(colElement)

    const col = wrapper.find('.drawing-item')
    expect(col.exists()).toBe(true)
    expect(col.classes()).not.toContain('active-from-item')
    expect(wrapper.find('.render-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('姓名')
    expect(wrapper.findComponent({ name: 'ElCol' }).props('span')).toBe(12)
  })

  test('选中与未聚焦边框样式按配置叠加', () => {
    const { wrapper } = mountItem(colElement, {
      activeId: 'input1',
      formConf: { unFocusedComponentBorder: true }
    })

    const col = wrapper.find('.drawing-item')
    expect(col.classes()).toContain('active-from-item')
    expect(col.classes()).toContain('unfocus-bordered')
  })

  test('点击组件触发选中，点击按钮触发复制与删除', () => {
    const activeItem = jest.fn()
    const copyItem = jest.fn()
    const deleteItem = jest.fn()
    const { wrapper, drawingList } = mountItem(colElement, {}, { activeItem, copyItem, deleteItem })

    wrapper.find('.drawing-item').trigger('click')
    expect(activeItem).toHaveBeenCalledWith(colElement)

    wrapper.find('.drawing-item-copy').trigger('click')
    expect(copyItem).toHaveBeenCalledWith(colElement, drawingList)

    wrapper.find('.drawing-item-delete').trigger('click')
    expect(deleteItem).toHaveBeenCalledWith(0, drawingList)
  })

  test('行容器渲染组件名并递归渲染子项', () => {
    const rowElement = {
      layout: 'rowFormItem',
      formId: 'row1',
      span: 24,
      gutter: 10,
      type: 'default',
      componentName: '行容器',
      children: [colElement]
    }
    const { wrapper } = mountItem(rowElement)

    expect(wrapper.find('.drawing-row-item').exists()).toBe(true)
    expect(wrapper.find('.component-name').text()).toBe('行容器')
    expect(wrapper.findAll('.drawing-item')).toHaveLength(1)
    expect(wrapper.find('.draggable-stub').exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'ElRow' })).toHaveLength(1)
  })

  test('弹性布局行容器额外包裹一行 flex 布局', () => {
    const rowElement = {
      layout: 'rowFormItem',
      formId: 'row2',
      span: 24,
      gutter: 10,
      type: 'flex',
      justify: 'center',
      align: 'middle',
      componentName: '弹性行',
      children: []
    }
    const { wrapper } = mountItem(rowElement)

    const rows = wrapper.findAllComponents({ name: 'ElRow' })
    expect(rows).toHaveLength(2)
    expect(rows.at(1).props('type')).toBe('flex')
    expect(rows.at(1).props('justify')).toBe('center')
    expect(rows.at(1).props('align')).toBe('middle')
  })

  test('行容器无子项时不渲染子组件', () => {
    const rowElement = {
      layout: 'rowFormItem',
      formId: 'row3',
      span: 24,
      gutter: 10,
      type: 'default',
      componentName: '空行',
      children: undefined
    }
    const { wrapper } = mountItem(rowElement)

    expect(wrapper.find('.drawing-row-item').exists()).toBe(true)
    expect(wrapper.findAll('.drawing-item')).toHaveLength(0)
  })
})
