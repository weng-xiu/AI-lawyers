'use strict'

/**
 * IconSelect/requireIcons 单测：以伪 require.context 加载真实源码，
 * 验证 svg 文件名提取与导出结果。
 */
const fs = require('fs')
const path = require('path')

const SOURCE = path.resolve(__dirname, '../../../src/components/IconSelect/requireIcons.js')

function loadIcons(keys) {
  const raw = fs.readFileSync(SOURCE, 'utf8')
  // 仅替换 ESM 导出语法，保留待测的 require.context 取值与文件名映射逻辑
  const code = raw.replace(/export\s+default\s+icons/, 'module.exports = icons')
  expect(code).not.toBe(raw)

  const fakeRequire = () => { throw new Error('requireIcons 不应加载其他模块') }
  fakeRequire.context = () => ({ keys: () => keys })

  const module = { exports: {} }
  new Function('require', 'module', 'exports', code)(fakeRequire, module, module.exports)
  return module.exports
}

describe('IconSelect/requireIcons', () => {
  test('提取 svg 文件名并去掉目录前缀与扩展名', () => {
    const icons = loadIcons(['./user.svg', './setting.svg', './dashboard.svg'])

    expect(icons).toEqual(['user', 'setting', 'dashboard'])
  })

  test('没有图标时导出空数组', () => {
    expect(loadIcons([])).toEqual([])
  })

  test('文件名中的路径部分保留，仅去掉 ./ 前缀与 .svg 后缀', () => {
    expect(loadIcons(['./icons/user.svg'])).toEqual(['icons/user'])
  })
})
