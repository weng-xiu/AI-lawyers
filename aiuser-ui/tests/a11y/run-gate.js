'use strict'
// 公众端无障碍 CI 门禁入口：设置 A11Y_GATE=1 后调用 Playwright，
// 任一页面出现 critical/serious 违规即返回非 0，供 CI 直接阻断。
// 跨平台实现：直接用当前 node 进程执行 @playwright/test 的 CLI，避免 shell 差异。
const { spawnSync } = require('child_process')
const path = require('path')

const uiRoot = path.join(__dirname, '..', '..')
const cli = require.resolve('@playwright/test/cli')
const args = ['test', '--config=playwright.config.js', ...process.argv.slice(2)]

const result = spawnSync(process.execPath, [cli, ...args], {
  cwd: uiRoot,
  stdio: 'inherit',
  env: { ...process.env, A11Y_GATE: '1' }
})

process.exit(result.status === null ? 1 : result.status)
