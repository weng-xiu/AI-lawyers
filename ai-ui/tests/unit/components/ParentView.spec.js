'use strict'

/**
 * ParentView 单测：确认父级路由视图占位正常渲染。
 */
import { mount } from '@vue/test-utils'
import ParentView from '@/components/ParentView/index.vue'

describe('ParentView', () => {
  test('渲染 router-view 占位', () => {
    const wrapper = mount(ParentView, { stubs: { 'router-view': true } })

    expect(wrapper.find('router-view-stub').exists()).toBe(true)
  })
})
