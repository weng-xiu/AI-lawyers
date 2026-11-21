'use strict'

/**
 * RuoYi 帮助入口单测：锁定文档与仓库图标跳转地址。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import RuoYiDoc from '@/components/RuoYi/Doc/index.vue'
import RuoYiGit from '@/components/RuoYi/Git/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

const localVue = createLocalVue()
localVue.component('svg-icon', SvgIcon)

describe('RuoYi 帮助入口', () => {
  test('文档入口使用 question 图标并打开官方文档', async () => {
    const open = jest.spyOn(window, 'open').mockImplementation(() => {})
    const wrapper = mount(RuoYiDoc, { localVue })

    expect(wrapper.find('use').element.getAttribute('xlink:href')).toBe('#icon-question')
    await wrapper.find('svg').trigger('click')

    expect(open).toHaveBeenCalledWith('http://doc.ruoyi.vip/ruoyi-vue')
    open.mockRestore()
  })

  test('仓库入口使用 github 图标并打开源码地址', async () => {
    const open = jest.spyOn(window, 'open').mockImplementation(() => {})
    const wrapper = mount(RuoYiGit, { localVue })

    expect(wrapper.find('use').element.getAttribute('xlink:href')).toBe('#icon-github')
    await wrapper.find('svg').trigger('click')

    expect(open).toHaveBeenCalledWith('https://gitee.com/y_project/RuoYi-Vue')
    open.mockRestore()
  })
})
