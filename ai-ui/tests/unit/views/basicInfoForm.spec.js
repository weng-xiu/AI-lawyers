'use strict'

/**
 * BasicInfoForm 表信息表单单测：锁定必填校验与字段渲染。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import BasicInfoForm from '@/views/tool/gen/basicInfoForm.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function validate(wrapper) {
  return new Promise(resolve => {
    wrapper.vm.$refs.basicInfoForm.validate(valid => resolve(valid))
  })
}

describe('BasicInfoForm', () => {
  test('渲染五项表信息字段', () => {
    const wrapper = mount(BasicInfoForm, {
      localVue,
      propsData: { info: {} }
    })

    const labels = wrapper.findAll('.el-form-item__label').wrappers.map(item => item.text())
    expect(labels).toEqual(['表名称', '表描述', '实体类名称', '作者', '备注'])
  })

  test('必填字段为空时校验不通过', async () => {
    const wrapper = mount(BasicInfoForm, {
      localVue,
      propsData: { info: {} }
    })

    await expect(validate(wrapper)).resolves.toBe(false)
  })

  test('必填字段齐备时校验通过，备注可留空', async () => {
    const wrapper = mount(BasicInfoForm, {
      localVue,
      propsData: {
        info: {
          tableName: 'law_case',
          tableComment: '案件表',
          className: 'LawCase',
          functionAuthor: 'developer',
          remark: ''
        }
      }
    })

    await expect(validate(wrapper)).resolves.toBe(true)
  })
})
