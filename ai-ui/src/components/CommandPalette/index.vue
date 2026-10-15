<template>
  <el-dialog
    :visible.sync="visible"
    width="600px"
    top="10vh"
    custom-class="cmd-palette"
    :show-close="false"
    append-to-body
    @opened="focusInput"
  >
    <el-input
      ref="input"
      v-model="keyword"
      size="medium"
      clearable
      prefix-icon="el-icon-search"
      placeholder="搜索菜单 / 工单号 / 知识条目 / 来电号码，回车直达"
      @input="onKeywordInput"
      @keydown.native.down.prevent="move(1)"
      @keydown.native.up.prevent="move(-1)"
      @keydown.enter.native="enterGo"
    />
    <div class="cmd-results">
      <template v-for="group in groups">
        <div :key="group.type" class="cmd-group-title">{{ group.title }}</div>
        <div
          v-for="item in group.items"
          :key="group.type + '-' + item.key"
          class="cmd-item"
          :class="{ active: item.flatIndex === activeIndex }"
          @click="go(item)"
          @mouseenter="activeIndex = item.flatIndex"
        >
          <i :class="item.icon" class="cmd-icon" />
          <span class="cmd-item-title">{{ item.title }}</span>
          <span v-if="item.sub" class="cmd-item-sub">{{ item.sub }}</span>
        </div>
      </template>
      <div v-if="!groups.length" class="cmd-empty">
        {{ keyword ? '无匹配结果' : '输入关键词搜索；↑↓ 选择，Enter 打开，Esc 关闭' }}
      </div>
    </div>
    <div class="cmd-footer">回车直达 · Esc 关闭 · Ctrl+K 唤起</div>
  </el-dialog>
</template>

<script>
/**
 * U3 命令面板（Ctrl+K 直达）：
 *  - 菜单：前端本地路由树扁平匹配，回车跳转；
 *  - 工单号 / 知识条目 / 来电号码：走 /lawyers/search/palette 聚合接口
 *    （后端按用户权限逐域裁剪，话单号码走 PII 盲 token 检索并脱敏返回）；
 *  - 远程结果点击 → 跳对应列表页并预填查询条件（页面读取 $route.query 自动查询）。
 *
 * 挂载点：layout/index.vue（登录后布局内全局可用）。
 */
import { searchPalette } from '@/api/lawyers/palette'

/** 各远程域直达目标路由（query 传查询条件，页面 created 时读取） */
const REMOTE_TARGET = {
  ticket: { path: '/business/workOrder', queryKey: 'ticketNo' },
  record: { path: '/inbound/callRecord', queryKey: 'callerNumber' },
  knowledge: { path: '/legal/knowledge', queryKey: 'title' }
}

/** 远程域展示配置：icon / 组标题 */
const REMOTE_META = {
  tickets: { type: 'ticket', title: '工单', icon: 'el-icon-tickets' },
  knowledges: { type: 'knowledge', title: '知识条目', icon: 'el-icon-reading' },
  records: { type: 'record', title: '来电记录', icon: 'el-icon-phone' }
}

export default {
  name: 'CommandPalette',
  data() {
    return {
      visible: false,
      keyword: '',
      remoteLoading: false,
      remoteGroups: [],
      activeIndex: 0,
      remoteSeq: 0
    }
  },
  computed: {
    /** 本地菜单扁平项（隐藏项/按钮不进面板） */
    menuItems() {
      const routes = (this.$store.state.permission && this.$store.state.permission.routes) || []
      const items = []
      const walk = (nodes, parentPath, parentTitle) => {
        (nodes || []).forEach(route => {
          if (route.hidden) return
          const title = route.meta && route.meta.title
          const path = this.resolvePath(parentPath, route.path)
          if (route.children && route.children.length) {
            walk(route.children, path, parentTitle)
          } else if (title) {
            items.push({
              key: path,
              type: 'menu',
              title: parentTitle && title !== parentTitle ? parentTitle + ' / ' + title : title,
              sub: path,
              path,
              icon: (route.meta && route.meta.icon) || 'el-icon-menu'
            })
          }
        })
      }
      walk(routes, '', '')
      return items
    },
    /** 分组结果：菜单（本地）+ 远程域，按序扁平编号供 ↑↓ 导航 */
    groups() {
      const kw = this.keyword.trim().toLowerCase()
      const groups = []
      let flat = 0
      const mark = items => items.forEach(it => { it.flatIndex = flat++ })
      const menuMatched = kw
        ? this.menuItems.filter(m => (m.title + ' ' + m.sub).toLowerCase().indexOf(kw) !== -1).slice(0, 8)
        : []
      if (menuMatched.length) {
        mark(menuMatched)
        groups.push({ type: 'menu', title: '菜单', items: menuMatched })
      }
      this.remoteGroups.forEach(g => {
        mark(g.items)
        groups.push(g)
      })
      return groups
    },
    flatItems() {
      const list = []
      this.groups.forEach(g => list.push(...g.items))
      return list
    }
  },
  mounted() {
    window.addEventListener('keydown', this.onGlobalKey)
  },
  beforeDestroy() {
    window.removeEventListener('keydown', this.onGlobalKey)
  },
  methods: {
    /** 全局快捷键：Ctrl+K / Cmd+K 唤起 */
    onGlobalKey(e) {
      if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault()
        this.open()
      }
    },
    open() {
      this.keyword = ''
      this.remoteGroups = []
      this.activeIndex = 0
      this.visible = true
    },
    focusInput() {
      this.$nextTick(() => {
        this.$refs.input && this.$refs.input.focus()
      })
    },
    onKeywordInput() {
      const seq = ++this.remoteSeq
      const kw = this.keyword.trim()
      this.activeIndex = 0
      clearTimeout(this._searchTimer)
      if (!kw) {
        this.remoteGroups = []
        this.remoteLoading = false
        return
      }
      this._searchTimer = setTimeout(() => {
        this.remoteLoading = true
        searchPalette(kw, 5).then(res => {
          if (seq !== this.remoteSeq) return // 仅采纳最新关键词结果
          const data = res.data || {}
          this.remoteGroups = Object.keys(REMOTE_META)
            .filter(field => data[field] && data[field].length)
            .map(field => {
              const meta = REMOTE_META[field]
              return {
                type: meta.type,
                title: meta.title,
                items: data[field].map(r => ({
                  key: String(r.id),
                  type: meta.type,
                  icon: meta.icon,
                  title: String(r.title || ''),
                  sub: r.sub || '',
                  no: String(r.title || '')
                }))
              }
            })
        }).catch(() => {
          if (seq === this.remoteSeq) this.remoteGroups = []
        }).finally(() => {
          if (seq === this.remoteSeq) this.remoteLoading = false
        })
      }, 300)
    },
    move(delta) {
      const total = this.flatItems.length
      if (!total) return
      this.activeIndex = (this.activeIndex + delta + total) % total
    },
    enterGo() {
      const item = this.flatItems[this.activeIndex]
      if (item) this.go(item)
    },
    go(item) {
      this.visible = false
      if (item.type === 'menu') {
        this.$router.push(item.path).catch(() => {})
        return
      }
      const target = REMOTE_TARGET[item.type]
      if (!target) return
      const query = {}
      query[target.queryKey] = item.no || item.title
      this.$router.push({ path: target.path, query }).catch(() => {})
    },
    resolvePath(parentPath, routePath) {
      if (!routePath) return parentPath || '/'
      if (/^https?:|^\/{2}/.test(routePath)) return routePath
      if (routePath.startsWith('/')) return routePath
      return (parentPath ? parentPath.replace(/\/$/, '') : '') + '/' + routePath
    }
  }
}
</script>

<style lang="scss" scoped>
.cmd-palette {
  .el-dialog__body {
    padding: 12px 16px 8px;
  }
}

.cmd-results {
  max-height: 380px;
  overflow-y: auto;
  margin-top: 10px;
}

.cmd-group-title {
  font-size: 12px;
  color: #8a94a6;
  padding: 8px 8px 4px;
  border-bottom: 1px solid #f0f2f6;
}

.cmd-item {
  display: flex;
  align-items: center;
  padding: 9px 10px;
  border-radius: 4px;
  cursor: pointer;
  border-left: 3px solid transparent;

  .cmd-icon {
    margin-right: 10px;
    color: #1a3c6e;
    font-size: 15px;
  }

  .cmd-item-title {
    color: #1f2a3a;
    font-size: 14px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .cmd-item-sub {
    margin-left: auto;
    padding-left: 12px;
    color: #9aa4b5;
    font-size: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 45%;
  }

  &:hover,
  &.active {
    background-color: #f0f4fa;
    border-left-color: #1a3c6e;
  }
}

.cmd-empty {
  padding: 28px 0;
  text-align: center;
  color: #9aa4b5;
  font-size: 13px;
}

.cmd-footer {
  padding: 8px 0 2px;
  text-align: right;
  color: #b9c0cc;
  font-size: 12px;
}
</style>
