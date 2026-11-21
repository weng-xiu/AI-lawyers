'use strict'
const path = require('path')

// P5 视觉回归：管理端 40 个业务页面快照
// - 后端接口全部 mock，无需 Java 服务；
// - 基线按平台分别保存（Playwright 默认文件名含 platform），首次需执行：
//     npm run test:visual:update
// - 日常比对：npm run test:visual
module.exports = {
  testDir: __dirname,
  testMatch: 'visual.spec.js',
  timeout: 180000,
  expect: { timeout: 20000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['json', { outputFile: '../visual-results/raw-results.json' }]],
  use: {
    baseURL: process.env.VISUAL_BASE_URL || 'http://localhost:8093',
    headless: true,
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    actionTimeout: 15000
  },
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } }
  ],
  webServer: process.env.VISUAL_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --port 8093',
        url: 'http://localhost:8093',
        timeout: 300000,
        reuseExistingServer: !process.env.CI,
        env: { NODE_OPTIONS: '--openssl-legacy-provider', npm_config_port: '8093' }
      }
}
