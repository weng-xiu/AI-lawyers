'use strict'

/**
 * 通用工具单测：锁定日期格式化、字典回显、参数序列化与树构建等跨页面复用的行为，
 * 这些函数被列表页/导出/路由广泛依赖，回归影响面大。
 */
import {
  parseTime,
  addDateRange,
  selectDictLabel,
  selectDictLabels,
  sprintf,
  parseStrEmpty,
  mergeRecursive,
  handleTree,
  tansParams,
  getNormalPath,
  blobValidate
} from '@/utils/ruoyi'

describe('parseTime 日期格式化', () => {
  const date = new Date(2024, 0, 2, 3, 4, 5)

  test('空入参返回 null', () => {
    expect(parseTime()).toBeNull()
    expect(parseTime(null)).toBeNull()
  })

  test('默认格式输出补零的日期时间', () => {
    expect(parseTime(date)).toBe('2024-01-02 03:04:05')
    expect(parseTime(date.getTime(), '{y}/{m}/{d}')).toBe('2024/01/02')
  })

  test('10 位秒级时间戳自动换算为毫秒', () => {
    const seconds = Math.floor(date.getTime() / 1000)
    expect(parseTime(seconds, '{y}-{m}-{d}')).toBe('2024-01-02')
  })

  test('字符串日期兼容短横线与 ISO 毫秒写法', () => {
    expect(parseTime('2024-01-02 03:04:05', '{y}-{m}-{d} {h}:{i}:{s}')).toBe('2024-01-02 03:04:05')
    expect(parseTime('2024-01-02T03:04:05.000', '{y}-{m}-{d}')).toBe('2024-01-02')
  })

  test('星期占位符输出中文', () => {
    const sunday = new Date(2024, 0, 7)
    expect(parseTime(sunday, '{a}')).toBe('日')
  })
})

describe('addDateRange 日期范围参数', () => {
  test('默认追加 beginTime/endTime', () => {
    const params = { pageNum: 1 }
    expect(addDateRange(params, ['2024-01-01', '2024-01-31'])).toEqual({
      pageNum: 1,
      params: { beginTime: '2024-01-01', endTime: '2024-01-31' }
    })
  })

  test('自定义字段名追加 beginXxx/endXxx', () => {
    const params = { params: { status: '1' } }
    const result = addDateRange(params, ['2024-01-01', '2024-01-31'], 'CreateTime')

    expect(result.params).toEqual({
      status: '1',
      beginCreateTime: '2024-01-01',
      endCreateTime: '2024-01-31'
    })
  })

  test('params 非法类型时重置为空对象，日期缺省置空', () => {
    expect(addDateRange({ params: 'bad' }, null)).toEqual({
      params: { beginTime: undefined, endTime: undefined }
    })
    expect(addDateRange({ params: [1, 2] }, 'bad').params).toEqual({
      beginTime: undefined,
      endTime: undefined
    })
  })
})

describe('字典回显', () => {
  const dict = [
    { value: '0', label: '正常' },
    { value: '1', label: '停用' }
  ]

  test('selectDictLabel 支持数字与字符串值', () => {
    expect(selectDictLabel(dict, 1)).toBe('停用')
    expect(selectDictLabel(dict, '0')).toBe('正常')
  })

  test('selectDictLabel 未命中回显原值，undefined 返回空串', () => {
    expect(selectDictLabel(dict, '9')).toBe('9')
    expect(selectDictLabel(dict, undefined)).toBe('')
  })

  test('selectDictLabels 支持数组与自定义分隔符', () => {
    expect(selectDictLabels(dict, ['0', '1'])).toBe('正常,停用')
    expect(selectDictLabels(dict, '0;1', ';')).toBe('正常;停用')
  })

  test('selectDictLabels 未命中保留原值，空值返回空串', () => {
    expect(selectDictLabels(dict, '0,9')).toBe('正常,9')
    expect(selectDictLabels(dict, '')).toBe('')
    expect(selectDictLabels(dict, undefined)).toBe('')
  })
})

describe('字符串与数据合并工具', () => {
  test('sprintf 按顺序替换 %s，参数不足返回空串', () => {
    expect(sprintf('%s-%s', 'a', 'b')).toBe('a-b')
    expect(sprintf('%s-%s', 'a')).toBe('')
    expect(sprintf('无占位符')).toBe('无占位符')
  })

  test('parseStrEmpty 归一化空值', () => {
    expect(parseStrEmpty(undefined)).toBe('')
    expect(parseStrEmpty(null)).toBe('')
    expect(parseStrEmpty('undefined')).toBe('')
    expect(parseStrEmpty('null')).toBe('')
    expect(parseStrEmpty('值')).toBe('值')
  })

  test('mergeRecursive 递归合并对象，非对象直接覆盖', () => {
    const source = { a: { x: 1 }, b: 1, c: { y: 2 } }
    const result = mergeRecursive(source, { a: { y: 2 }, b: 3, c: null })

    expect(result).toEqual({ a: { x: 1, y: 2 }, b: 3, c: null })
  })
})

describe('handleTree 树构建', () => {
  test('按默认 id/parentId 组装树并补 children', () => {
    const tree = handleTree([
      { id: 1, parentId: 0, name: '根' },
      { id: 2, parentId: 1, name: '子' }
    ])

    expect(tree).toHaveLength(1)
    expect(tree[0].children).toHaveLength(1)
    expect(tree[0].children[0]).toMatchObject({ id: 2, name: '子', children: [] })
  })

  test('支持自定义字段名', () => {
    const tree = handleTree(
      [
        { code: 'A', pid: null },
        { code: 'B', pid: 'A' }
      ],
      'code',
      'pid',
      'nodes'
    )

    expect(tree).toHaveLength(1)
    expect(tree[0].nodes[0].code).toBe('B')
  })
})

describe('tansParams 参数序列化', () => {
  test('跳过 null 与空串并编码特殊字符', () => {
    const result = tansParams({ a: 1, b: null, c: '', d: undefined, e: '中 文' })

    expect(result).toBe('a=1&e=%E4%B8%AD%20%E6%96%87&')
  })

  test('嵌套对象展开为 params[key] 形式', () => {
    const result = tansParams({ params: { beginTime: '2024-01-01', empty: null }, pageNum: 1 })

    expect(result).toBe('params%5BbeginTime%5D=2024-01-01&pageNum=1&')
  })
})

describe('路径与响应类型工具', () => {
  test('getNormalPath 收敛双斜杠与末尾斜杠', () => {
    expect(getNormalPath('')).toBe('')
    expect(getNormalPath('undefined')).toBe('undefined')
    expect(getNormalPath('//system/user')).toBe('/system/user')
    expect(getNormalPath('/system/user/')).toBe('/system/user')
  })

  test('blobValidate 以 JSON 类型判定非文件流', () => {
    expect(blobValidate({ type: 'application/json' })).toBe(false)
    expect(blobValidate({ type: 'application/octet-stream' })).toBe(true)
  })
})
