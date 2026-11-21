'use strict'

/**
 * Pagination 分页组件单测：锁定分页字段双向同步、翻页/切换每页条数事件与自动滚动。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Pagination from '@/components/Pagination/index.vue'
import { scrollTo } from '@/utils/scroll-to'

jest.mock('@/utils/scroll-to', () => ({ scrollTo: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountPagination(propsData = {}) {
  return mount(Pagination, {
    localVue,
    propsData: { total: 100, ...propsData }
  })
}

beforeEach(() => {
  scrollTo.mockClear()
})

describe('Pagination', () => {
  test('默认页码与每页条数取自 props', () => {
    const wrapper = mountPagination()

    expect(wrapper.vm.currentPage).toBe(1)
    expect(wrapper.vm.pageSize).toBe(20)
    expect(wrapper.find('.pagination-container').exists()).toBe(true)
  })

  test('hidden 为真时附加隐藏类名', () => {
    const wrapper = mountPagination({ hidden: true })

    expect(wrapper.find('.pagination-container').classes()).toContain('hidden')
  })

  test('修改页码与每页条数触发 update 事件', () => {
    const wrapper = mountPagination()

    wrapper.vm.currentPage = 3
    wrapper.vm.pageSize = 50

    expect(wrapper.emitted('update:page').pop()).toEqual([3])
    expect(wrapper.emitted('update:limit').pop()).toEqual([50])
  })

  test('切换页大小后触发分页事件并自动滚动', () => {
    const wrapper = mountPagination({ page: 1, limit: 20 })

    wrapper.vm.handleSizeChange(10)

    expect(wrapper.emitted('pagination')).toEqual([[{ page: 1, limit: 10 }]])
    expect(scrollTo).toHaveBeenCalledWith(0, 800)
  })

  test('页大小变更后当前页超出总页数时重置为第一页', () => {
    const wrapper = mountPagination({ page: 10, limit: 20, total: 50 })

    wrapper.vm.handleSizeChange(20)

    expect(wrapper.emitted('update:page').pop()).toEqual([1])
  })

  test('翻页触发分页事件，关闭自动滚动时不滚动', () => {
    const wrapper = mountPagination({ autoScroll: false })

    wrapper.vm.handleCurrentChange(4)

    expect(wrapper.emitted('pagination')).toEqual([[{ page: 4, limit: 20 }]])
    expect(scrollTo).not.toHaveBeenCalled()
  })
})
