'use strict'

/**
 * P8 组件单测：V3 ProTable（查询区/工具条/表格/分页一体化封装）
 * 覆盖：列渲染、查询控件 v-model、query/reset 事件、插槽与 formatter、
 *       选择事件透传、空态、密度/列设置持久化（U5）、筛选条件持久化（U5）。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

/** 全局注册组件（right-toolbar/pagination/TableEmpty）统一 stub，聚焦 ProTable 自身逻辑 */
const globalStubs = {
  'right-toolbar': { template: '<div class="rt-stub"><slot name="toolbar-right" /></div>' },
  pagination: { template: '<div class="pg-stub" />' },
  TableEmpty: {
    template: '<button class="empty-stub" @click="$emit(\'action\')">{{ text }}</button>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  }
}

const baseColumns = [
  { prop: 'name', label: '姓名' },
  { prop: 'status', label: '状态', slot: true },
  { prop: 'amount', label: '金额', formatter: row => '¥' + row.amount }
]
const baseQueryItems = [
  { prop: 'keyword', label: '关键字' },
  { prop: 'type', label: '类型', type: 'select', options: [{ label: 'A', value: 'a' }, { label: 'B', value: 'b' }] }
]

function mountTable(overrides = {}) {
  return mount(ProTable, {
    localVue,
    stubs: globalStubs,
    propsData: Object.assign({
      columns: baseColumns,
      queryFormItems: baseQueryItems,
      queryParams: { pageNum: 1, pageSize: 10, keyword: '', type: '' },
      data: [
        { id: 1, name: '张三', status: '1', amount: 100 },
        { id: 2, name: '李四', status: '0', amount: 200 }
      ],
      total: 2
    }, overrides)
  })
}

describe('ProTable', () => {
  beforeEach(() => {
    localStorage.clear()
    jest.spyOn(console, 'warn').mockImplementation(() => {})
  })
  afterEach(() => jest.restoreAllMocks())

  it('按 columns 渲染表头与数据行，formatter 生效', async () => {
    const wrapper = mountTable()
    await wrapper.vm.$nextTick()
    const headerText = wrapper.find('.el-table__header-wrapper').text()
    expect(headerText).toContain('姓名')
    expect(headerText).toContain('状态')
    const bodyText = wrapper.find('.el-table__body-wrapper').text()
    expect(bodyText).toContain('张三')
    expect(bodyText).toContain('¥100') // formatter 输出
  })

  it('column-{prop} 插槽渲染自定义列内容', async () => {
    const wrapper = mount(ProTable, {
      localVue,
      stubs: globalStubs,
      propsData: {
        columns: baseColumns,
        queryFormItems: [],
        queryParams: { pageNum: 1, pageSize: 10 },
        data: [{ id: 1, name: '王五', status: 'done', amount: 1 }],
        total: 1
      },
      scopedSlots: {
        'column-status': '<span class="status-tag">已完成</span>'
      }
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.status-tag').exists()).toBe(true)
    expect(wrapper.find('.status-tag').text()).toBe('已完成')
  })

  it('查询区 input 控件双向绑定 queryParams，回车触发 query 事件', async () => {
    const wrapper = mountTable()
    const input = wrapper.find('.el-form .el-input input')
    await input.setValue('劳动合同')
    expect(wrapper.props('queryParams').keyword).toBe('劳动合同')
    await input.trigger('keyup.enter')
    expect(wrapper.emitted('query')).toBeTruthy()
  })

  it('select 控件选项由 queryFormItems 驱动', () => {
    const wrapper = mountTable()
    const options = wrapper.findAll('.el-select .el-select-dropdown__item')
    expect(options.length).toBe(2)
    expect(options.at(0).text()).toBe('A')
  })

  it('搜索按钮 emit query，重置按钮 emit reset', async () => {
    const wrapper = mountTable()
    const buttons = wrapper.findAll('.el-form button')
    // 第一个为搜索，第二个为重置
    await buttons.at(0).trigger('click')
    expect(wrapper.emitted('query')).toBeTruthy()
    await buttons.at(1).trigger('click')
    expect(wrapper.emitted('reset')).toBeTruthy()
  })

  it('行点击事件透传 row-click', async () => {
    const wrapper = mountTable()
    await wrapper.find('.el-table__body-wrapper tbody tr').trigger('click')
    expect(wrapper.emitted('row-click')).toBeTruthy()
    expect(wrapper.emitted('row-click')[0][0].name).toBe('张三')
  })

  it('空数据时渲染内建空态文案', () => {
    const wrapper = mountTable({ data: [], total: 0, emptyText: '没有查询到记录' })
    expect(wrapper.find('.empty-stub').text()).toBe('没有查询到记录')
  })

  it('U7：空态主操作事件透传为 empty-action', async () => {
    const wrapper = mountTable({ data: [], total: 0, emptyActionText: '新增工单' })
    await wrapper.find('.empty-stub').trigger('click')
    expect(wrapper.emitted('empty-action')).toHaveLength(1)
  })

  it('U5：密度切换持久化到 localStorage', async () => {
    const wrapper = mountTable({ persistKey: 'unit-test' })
    await wrapper.vm.handleSizeCommand('small')
    expect(wrapper.vm.tableSize).toBe('small')
    const saved = JSON.parse(localStorage.getItem('proTable:tools:unit-test'))
    expect(saved.size).toBe('small')
  })

  it('U5：列设置持久化——恢复时剔除已失效 key，隐藏列生效，重置后全列恢复', async () => {
    localStorage.setItem('proTable:tools:persist-cols', JSON.stringify({
      size: 'default',
      cols: ['name', 'ghost-col'] // ghost-col 已不存在，应被剔除
    }))
    const wrapper = mountTable({ persistKey: 'persist-cols' })
    await wrapper.vm.$nextTick()
    // created 阶段 restoreTools：失效 key 被剔除，仅保留 name
    expect(wrapper.vm.visibleCols).toEqual(['name'])
    expect(wrapper.vm.displayColumns.map(c => c.prop)).toEqual(['name'])
    // 重置列显隐后全列恢复并回写 localStorage（watcher 持久化异步落盘）
    await wrapper.vm.resetColumns()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.displayColumns.length).toBe(baseColumns.length)
    const saved = JSON.parse(localStorage.getItem('proTable:tools:persist-cols'))
    expect(saved.cols.sort()).toEqual(['amount', 'name', 'status'])
  })

  it('U5：persistQuery 开启时从 localStorage 恢复筛选并 emit persist-restored', async () => {
    localStorage.setItem('proTable:query:pq', JSON.stringify({ keyword: '历史值', unknownKey: 'x' }))
    const queryParams = { pageNum: 1, pageSize: 10, keyword: '', type: '' }
    const wrapper = mountTable({ persistKey: 'pq', persistQuery: true, queryParams })
    await flushPromises()
    expect(queryParams.keyword).toBe('历史值')
    expect(queryParams.unknownKey).toBeUndefined() // 未声明的 key 不回填
    expect(wrapper.emitted('persist-restored')).toBeTruthy()
  })

  it('hasIndex/hasSelection 渲染序号列与多选列', async () => {
    const wrapper = mountTable({ hasIndex: true, hasSelection: true })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-table__header-wrapper .el-table-column--selection').exists()).toBe(true)
    const firstRowCells = wrapper.findAll('.el-table__body-wrapper tbody tr').at(0).findAll('td')
    expect(firstRowCells.length).toBe(baseColumns.length + 2)
  })
})
