'use strict'

/**
 * 代码生成配置表单单测：锁定菜单归一化、模板切换清理关联项、子表外键联动与条件区块渲染。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import GenInfoForm from '@/views/tool/gen/genInfoForm.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

const tables = [
  {
    tableName: 'sys_user',
    tableComment: '用户表',
    columns: [
      { columnName: 'user_id', columnComment: '用户ID' },
      { columnName: 'dept_id', columnComment: '部门ID' }
    ]
  }
]

function makeInfo(overrides = {}) {
  return Object.assign({
    tplCategory: 'crud',
    tplWebType: 'element-ui',
    packageName: 'com.ruoyi.system',
    moduleName: 'system',
    businessName: 'user',
    functionName: '用户',
    genType: '0',
    genPath: '/',
    parentMenuId: 1,
    subTableName: '',
    subTableFkName: '',
    columns: [
      { columnName: 'user_id', columnComment: '用户ID' },
      { columnName: 'dept_id', columnComment: '部门ID' }
    ]
  }, overrides)
}

function mountForm(overrides) {
  return mount(GenInfoForm, {
    localVue,
    propsData: {
      info: makeInfo(overrides),
      tables,
      menus: [{ menuId: 1, menuName: '系统管理', children: [] }]
    },
    stubs: {
      treeselect: {
        name: 'Treeselect',
        props: ['value', 'options', 'normalizer', 'appendToBody', 'showCount', 'placeholder'],
        template: '<div class="treeselect-stub" />'
      }
    }
  })
}

describe('GenInfoForm 代码生成配置表单', () => {
  test('normalizer 将菜单转换为树组件结构并移除空 children', () => {
    const wrapper = mountForm()

    expect(wrapper.vm.normalizer({ menuId: 1, menuName: '系统管理', children: [] })).toEqual({
      id: 1,
      label: '系统管理',
      children: undefined
    })
    expect(wrapper.vm.normalizer({ menuId: 2, menuName: '用户管理', children: [{ menuId: 3 }] })).toEqual({
      id: 2,
      label: '用户管理',
      children: [{ menuId: 3 }]
    })
  })

  test('切换到非主子表时清空关联子表配置', () => {
    const wrapper = mountForm({ tplCategory: 'sub', subTableName: 'sys_user', subTableFkName: 'user_id' })

    wrapper.vm.tplSelectChange('crud')

    expect(wrapper.vm.info.subTableName).toBe('')
    expect(wrapper.vm.info.subTableFkName).toBe('')
  })

  test('切换子表时清空已选外键并按表名联动列', async () => {
    const wrapper = mountForm({ tplCategory: 'sub', subTableFkName: 'user_id' })

    wrapper.vm.subSelectChange('sys_user')
    expect(wrapper.vm.info.subTableFkName).toBe('')

    wrapper.vm.info.subTableName = 'sys_user'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.subColumns).toHaveLength(2)
    expect(wrapper.vm.subColumns[0].columnName).toBe('user_id')

    wrapper.vm.info.subTableName = 'unknown'
    await wrapper.vm.$nextTick()
    // 未匹配到子表时保留上一次列集合
    expect(wrapper.vm.subColumns).toHaveLength(2)
  })

  test('默认渲染单表配置，自定义路径随生成方式显隐', async () => {
    const wrapper = mountForm({ genType: '0' })

    expect(wrapper.vm.info.tplCategory).toBe('crud')
    expect(wrapper.text()).not.toContain('最近路径快速选择')

    wrapper.vm.info.genType = '1'
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('最近路径快速选择')
  })

  test('树表模板展示树字段区块，主子表展示关联区块', async () => {
    const wrapper = mountForm({ tplCategory: 'crud' })
    const treeRow = wrapper.findAll('.form-header').wrappers[0].element.parentElement
    const subRow = wrapper.findAll('.form-header').wrappers[1].element.parentElement

    expect(treeRow.style.display).toBe('none')
    expect(subRow.style.display).toBe('none')

    wrapper.vm.info.tplCategory = 'tree'
    await wrapper.vm.$nextTick()
    expect(treeRow.style.display).toBe('')

    wrapper.vm.info.tplCategory = 'sub'
    await wrapper.vm.$nextTick()
    expect(subRow.style.display).toBe('')
  })
})
