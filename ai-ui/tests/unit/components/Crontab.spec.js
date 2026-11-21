'use strict'

/**
 * Crontab 表达式编辑器单测：锁定表达式反解析、字段联动、隐藏配置与提交事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Crontab from '@/components/Crontab/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountCrontab(propsData = {}) {
  return mount(Crontab, {
    localVue,
    propsData,
    stubs: { CrontabResult: true }
  })
}

describe('Crontab', () => {
  test('默认生成秒级六位表达式', () => {
    const wrapper = mountCrontab()

    expect(wrapper.vm.crontabValueObj).toEqual({
      second: '*',
      min: '*',
      hour: '*',
      day: '*',
      month: '*',
      week: '?',
      year: ''
    })
    expect(wrapper.vm.crontabValueString).toBe('* * * * * ?')
  })

  test('反解析七位表达式填充各字段', () => {
    const wrapper = mountCrontab({ expression: '0 15 10 * * ? 2026' })

    expect(wrapper.vm.crontabValueObj).toEqual({
      second: '0',
      min: '15',
      hour: '10',
      day: '*',
      month: '*',
      week: '?',
      year: '2026'
    })
    expect(wrapper.vm.crontabValueString).toBe('0 15 10 * * ? 2026')
  })

  test('hideComponent 控制对应面板显隐', () => {
    const wrapper = mountCrontab({ hideComponent: ['second', 'year'] })

    expect(wrapper.vm.shouldHide('second')).toBe(false)
    expect(wrapper.vm.shouldHide('year')).toBe(false)
    expect(wrapper.vm.shouldHide('min')).toBe(true)
  })

  test('子组件更新字段，来源不同时联动刷新', () => {
    const wrapper = mountCrontab()
    const spy = jest.spyOn(wrapper.vm, 'changeRadio')

    wrapper.vm.updateCrontabValue('min', '5')
    expect(wrapper.vm.crontabValueObj.min).toBe('5')
    expect(spy).not.toHaveBeenCalled()

    wrapper.vm.updateCrontabValue('hour', '9', 'day')
    expect(wrapper.vm.crontabValueObj.hour).toBe('9')
    expect(spy).toHaveBeenCalledWith('hour', '9')
  })

  test('确定提交当前表达式并关闭弹窗', () => {
    const wrapper = mountCrontab({ expression: '0 15 10 * * ? 2026' })

    wrapper.vm.submitFill()

    expect(wrapper.emitted('fill')).toEqual([['0 15 10 * * ? 2026']])
    expect(wrapper.emitted('hide')).toHaveLength(1)
  })

  test('取消仅抛出 hide 事件', () => {
    const wrapper = mountCrontab()

    wrapper.vm.hidePopup()

    expect(wrapper.emitted('hide')).toHaveLength(1)
    expect(wrapper.emitted('fill')).toBeUndefined()
  })

  test('重置还原为默认表达式', () => {
    const wrapper = mountCrontab()
    wrapper.vm.crontabValueObj = {
      second: '1',
      min: '2',
      hour: '3',
      day: '4',
      month: '5',
      week: '6',
      year: '2026'
    }

    wrapper.vm.clearCron()

    expect(wrapper.vm.crontabValueString).toBe('* * * * * ?')
  })

  test('数值校验取整并限制范围', () => {
    const wrapper = mountCrontab()

    expect(wrapper.vm.checkNumber(5.8, 0, 10)).toBe(5)
    expect(wrapper.vm.checkNumber(-3, 0, 10)).toBe(0)
    expect(wrapper.vm.checkNumber(42, 0, 10)).toBe(10)
  })
})
