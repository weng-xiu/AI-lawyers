<template>
  <div class="navbar">
    <hamburger id="hamburger-container" :is-active="sidebar.opened" class="hamburger-container" @toggleClick="toggleSideBar" />

    <breadcrumb v-if="!topNav" id="breadcrumb-container" class="breadcrumb-container" />
    <top-nav v-if="topNav" id="topmenu-container" class="topmenu-container" />

    <div class="right-menu">
      <template v-if="device!=='mobile'">
        <search id="header-search" class="right-menu-item" />

        <el-tooltip content="源码地址" effect="dark" placement="bottom">
          <ruo-yi-git id="ruoyi-git" class="right-menu-item hover-effect" />
        </el-tooltip>

        <el-tooltip content="文档地址" effect="dark" placement="bottom">
          <ruo-yi-doc id="ruoyi-doc" class="right-menu-item hover-effect" />
        </el-tooltip>

        <screenfull id="screenfull" class="right-menu-item hover-effect" />

        <el-tooltip content="布局大小" effect="dark" placement="bottom">
          <size-select id="size-select" class="right-menu-item hover-effect" />
        </el-tooltip>

        <!-- F2 坐席端关怀模式：三档字号 + 高对比（工位浏览器本地持久化） -->
        <el-popover placement="bottom" width="280" trigger="click" popper-class="care-settings-popover">
          <div class="care-panel">
            <div class="care-panel-title">显示设置</div>
            <div class="care-panel-row">
              <span class="care-panel-label" id="careFontLabel">字号</span>
              <el-radio-group
                :value="care.fontScale"
                size="small"
                aria-labelledby="careFontLabel"
                @input="onFontScale">
                <el-radio-button label="normal">标准</el-radio-button>
                <el-radio-button label="large">大字</el-radio-button>
                <el-radio-button label="xlarge">超大</el-radio-button>
              </el-radio-group>
            </div>
            <div class="care-panel-row">
              <span class="care-panel-label" id="careContrastLabel">高对比</span>
              <el-switch
                :value="care.highContrast"
                active-color="#1A3C6E"
                aria-labelledby="careContrastLabel"
                @change="onHighContrast" />
            </div>
            <div class="care-panel-tip">标准14px / 大字18px / 超大22px；高对比主题正文对比度≥4.5:1。设置仅保存在当前工位浏览器，适用于视障坐席与投屏场景。</div>
          </div>
          <button
            slot="reference"
            type="button"
            id="care-settings"
            class="right-menu-item hover-effect care-entry"
            :class="{ 'is-active': care.fontScale !== 'normal' || care.highContrast }"
            aria-label="显示设置：字号与高对比度">
            <span class="care-glyph" aria-hidden="true">A<sup class="care-glyph-plus">+</sup></span>
          </button>
        </el-popover>

        <el-popover placement="bottom-end" width="360" trigger="click" popper-class="message-bell-popover" @show="fetchRecent">
          <div class="msg-popover">
            <div class="msg-popover-header">
              <span>站内消息</span>
              <el-button type="text" size="mini" @click="handleReadAll" v-if="unread > 0">全部已读</el-button>
            </div>
            <div class="msg-popover-body">
              <div v-if="recentMessages.length === 0" class="msg-empty">暂无消息</div>
              <div v-for="m in recentMessages" :key="m.messageId" class="msg-item" :class="{ unread: m.isRead === '0' }" @click="handleOpenMessage(m)">
                <div class="msg-item-title">
                  <el-tag size="mini" :type="msgTagType(m.msgType)" effect="plain">{{ msgTypeText(m.msgType) }}</el-tag>
                  <span class="msg-title-text">{{ m.title }}</span>
                </div>
                <div class="msg-item-time">{{ m.createTime }}</div>
              </div>
            </div>
            <div class="msg-popover-footer">
              <el-button type="text" size="mini" @click="goMessageCenter">查看全部消息</el-button>
            </div>
          </div>
          <div slot="reference" id="message-bell" class="right-menu-item hover-effect msg-bell">
            <el-badge :value="unread" :hidden="unread === 0" :max="99" class="msg-badge">
              <svg-icon icon-class="message" class="msg-bell-icon" />
            </el-badge>
          </div>
        </el-popover>

      </template>

      <el-dropdown class="avatar-container right-menu-item hover-effect" trigger="hover">
        <div class="avatar-wrapper">
          <img :src="avatar" class="user-avatar">
          <span class="user-nickname"> {{ nickName }} </span>
        </div>
        <el-dropdown-menu slot="dropdown">
          <router-link to="/user/profile">
            <el-dropdown-item>个人中心</el-dropdown-item>
          </router-link>
          <el-dropdown-item divided @click.native="logout">
            <span>退出登录</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>

      <div class="right-menu-item hover-effect setting" @click="setLayout" v-if="setting">
        <svg-icon icon-class="more-up" />
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import Breadcrumb from '@/components/Breadcrumb'
import TopNav from '@/components/TopNav'
import Hamburger from '@/components/Hamburger'
import Screenfull from '@/components/Screenfull'
import SizeSelect from '@/components/SizeSelect'
import Search from '@/components/HeaderSearch'
import RuoYiGit from '@/components/RuoYi/Git'
import RuoYiDoc from '@/components/RuoYi/Doc'
import callSocket from '@/utils/callSocket'
import { unreadCount, listMessage, readAllMessage } from '@/api/lawyers/message'

export default {
  emits: ['setLayout'],
  components: {
    Breadcrumb,
    TopNav,
    Hamburger,
    Screenfull,
    SizeSelect,
    Search,
    RuoYiGit,
    RuoYiDoc
  },
  data() {
    return {
      unread: 0,
      recentMessages: []
    }
  },
  created() {
    this.fetchUnread()
    // 站内信实时推送：刷新角标与最近消息
    callSocket.on('MESSAGE_NOTIFY', () => {
      this.fetchUnread()
      this.fetchRecent()
    })
  },
  computed: {
    ...mapGetters([
      'sidebar',
      'avatar',
      'device',
      'nickName'
    ]),
    // F2 坐席端关怀模式（utils/careMode.js 全局单例）
    care() {
      return this.$care.state
    },
    setting: {
      get() {
        return this.$store.state.settings.showSettings
      }
    },
    topNav: {
      get() {
        return this.$store.state.settings.topNav
      }
    }
  },
  methods: {
    // F2 坐席端关怀模式
    onFontScale(val) {
      this.$care.setFontScale(val)
    },
    onHighContrast(val) {
      this.$care.setHighContrast(val)
    },
    fetchUnread() {
      unreadCount().then(res => {
        this.unread = res.data || 0
      }).catch(() => {})
    },
    fetchRecent() {
      listMessage({ pageNum: 1, pageSize: 5 }).then(res => {
        this.recentMessages = res.rows || []
      }).catch(() => {})
    },
    msgTypeText(type) {
      const map = { '1': '系统', '2': '待办', '3': '风险', '4': '质检', '5': '工单', '9': '其他' }
      return map[type] || '消息'
    },
    msgTagType(type) {
      const map = { '1': 'info', '2': 'primary', '3': 'danger', '4': 'warning', '5': 'success', '9': 'info' }
      return map[type] || 'info'
    },
    handleReadAll() {
      readAllMessage().then(() => {
        this.$modal.msgSuccess('已全部标记为已读')
        this.fetchUnread()
        this.fetchRecent()
      })
    },
    handleOpenMessage(row) {
      // 有关联业务则直达业务页面，否则进入消息中心
      const map = {
        'quality': '/quality',
        'ticket': '/lawyers/callCenter/callTicket',
        'warning': '/lawyers/callCenter/riskWarning',
        'outbound': '/lawyers/callCenter/outbound/task'
      };
      const route = map[row.bizType];
      if (route) {
        this.$router.push(route);
      } else {
        this.$router.push('/lawyers/message');
      }
    },
    goMessageCenter() {
      this.$router.push('/lawyers/message')
    },
    toggleSideBar() {
      this.$store.dispatch('app/toggleSideBar')
    },
    setLayout(event) {
      this.$emit('setLayout')
    },
    logout() {
      this.$confirm('确定注销并退出系统吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$store.dispatch('LogOut').then(() => {
          location.href = '/index'
        })
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.navbar {
  height: 56px;
  overflow: hidden;
  position: relative;
  background: #1A3C6E;
  border-bottom: 1px solid #255A99;

  .hamburger-container {
    line-height: 56px;
    height: 100%;
    float: left;
    cursor: pointer;
    transition: background .3s;
    -webkit-tap-highlight-color:transparent;
    color: #DCE2EB;

    &:hover {
      background: rgba(255, 255, 255, 0.08)
    }
  }

  .breadcrumb-container {
    float: left;
  }

  .topmenu-container {
    position: absolute;
    left: 50px;
  }

  .errLog-container {
    display: inline-block;
    vertical-align: top;
  }

  .right-menu {
    float: right;
    height: 100%;
    line-height: 56px;

    &:focus {
      outline: none;
    }

    .right-menu-item {
      display: inline-block;
      padding: 0 8px;
      height: 100%;
      font-size: 18px;
      color: #DCE2EB;
      vertical-align: text-bottom;

      &.hover-effect {
        cursor: pointer;
        transition: background .3s;

        &:hover {
          background: rgba(255, 255, 255, 0.08)
        }
      }
    }

    .msg-bell {
      .msg-bell-icon {
        font-size: 18px;
        color: #DCE2EB;
        vertical-align: middle;
      }
      .msg-badge {
        line-height: normal;
      }
    }

    /* F2 关怀模式入口：A+ 字形按钮（复用 right-menu-item 深底悬停态） */
    .care-entry {
      padding: 0 10px;
      border: none;
      background: transparent;
      vertical-align: top;

      .care-glyph {
        font-size: 16px;
        font-weight: 700;
        font-style: normal;
        color: #DCE2EB;
        line-height: 1;

        .care-glyph-plus {
          font-size: 11px;
          margin-left: 1px;
        }
      }

      &.is-active .care-glyph,
      &.is-active {
        color: #FFD04B;
      }

      &:focus-visible {
        outline: 3px solid #FFD04B;
        outline-offset: -3px;
      }
    }

    .avatar-container {
      margin-right: 0px;
      padding-right: 0px;

      .avatar-wrapper {
        margin-top: 13px;
        position: relative;

        .user-avatar {
          cursor: pointer;
          width: 30px;
          height: 30px;
          border-radius: 50%;
          border: 1px solid rgba(255, 255, 255, 0.2);
        }

        .user-nickname{
          position: relative;
          bottom: 10px;
          font-size: 14px;
          font-weight: bold;
          color: #FFFFFF;
        }

        .el-icon-caret-bottom {
          cursor: pointer;
          position: absolute;
          right: -20px;
          top: 25px;
          font-size: 12px;
          color: #DCE2EB;
        }
      }
    }
  }
}
</style>

<style lang="scss">
/* F2 关怀模式设置面板（el-popover 挂 body，需全局样式） */
.care-settings-popover {
  .care-panel {
    .care-panel-title {
      font-size: 15px;
      font-weight: 700;
      color: #1F2A3A;
      padding-bottom: 10px;
      margin-bottom: 12px;
      border-bottom: 1px solid #EBEEF5;
    }

    .care-panel-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 14px;

      .care-panel-label {
        font-size: 14px;
        color: #303133;
      }
    }

    .care-panel-tip {
      margin-top: 4px;
      padding-top: 10px;
      border-top: 1px dashed #DCE2EB;
      font-size: 12px;
      line-height: 1.6;
      color: #909399;
    }
  }
}

.message-bell-popover {
  padding: 0 !important;

  .msg-popover {
    .msg-popover-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 14px;
      font-weight: bold;
      border-bottom: 1px solid #EBEEF5;
    }

    .msg-popover-body {
      max-height: 320px;
      overflow-y: auto;

      .msg-empty {
        text-align: center;
        color: #909399;
        padding: 24px 0;
        font-size: 13px;
      }

      .msg-item {
        padding: 10px 14px;
        cursor: pointer;
        border-bottom: 1px solid #F2F6FC;

        &:hover {
          background: #F5F7FA;
        }

        &.unread .msg-title-text {
          font-weight: bold;
          color: #303133;
        }

        .msg-item-title {
          display: flex;
          align-items: center;
          gap: 6px;

          .msg-title-text {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-size: 13px;
            color: #606266;
          }
        }

        .msg-item-time {
          margin-top: 4px;
          font-size: 12px;
          color: #909399;
        }
      }
    }

    .msg-popover-footer {
      text-align: center;
      padding: 8px 0;
      border-top: 1px solid #EBEEF5;
    }
  }
}
</style>
