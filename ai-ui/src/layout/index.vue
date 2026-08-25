<template>
  <div :class="classObj" class="app-wrapper" :style="{'--current-color': theme}">
    <div v-if="device==='mobile'&&sidebar.opened" class="drawer-bg" @click="handleClickOutside"/>
    <sidebar v-if="!sidebar.hide" class="sidebar-container"/>
      <div :class="{hasTagsView:needTagsView,sidebarHide:sidebar.hide}" class="main-container">
      <div :class="{'fixed-header':fixedHeader}">
        <navbar @setLayout="setLayout"/>
        <agent-status-bar />
        <tags-view v-if="needTagsView"/>
      </div>
      <app-main/>
      <settings ref="settingRef"/>
      <draggable-dialer
        :visible="dialerVisible && !!agent"
        :agent="agent"
        :contacts="dialerContacts"
        @close="onDialerClose"
        @call-made="onCallMade"
      />
    </div>
  </div>
</template>

<script>
import { AppMain, Navbar, Settings, Sidebar, TagsView } from './components'
import ResizeMixin from './mixin/ResizeHandler'
import { mapState } from 'vuex'
import variables from '@/assets/styles/variables.scss'
import AgentStatusBar from '@/components/CallCenter/AgentStatusBar'
import DraggableDialer from '@/components/CallCenter/DraggableDialer'

export default {
  name: 'Layout',
  components: {
    AppMain,
    AgentStatusBar,
    DraggableDialer,
    Navbar,
    Settings,
    Sidebar,
    TagsView
  },
  mixins: [ResizeMixin],
  data() {
    return {
      // 内置常用联系人示例（可替换为后端接口数据）
      dialerContacts: [
        { id: 1, name: '12348 法律服务热线', phone: '12348' },
        { id: 2, name: '12345 政务服务热线', phone: '12345' },
        { id: 3, name: '测试分机 1001', phone: '1001' },
        { id: 4, name: '测试分机 1002', phone: '1002' },
        { id: 5, name: '测试分机 1003', phone: '1003' }
      ]
    }
  },
  computed: {
    ...mapState({
      theme: state => state.settings.theme,
      sideTheme: state => state.settings.sideTheme,
      sidebar: state => state.app.sidebar,
      device: state => state.app.device,
      needTagsView: state => state.settings.tagsView,
      fixedHeader: state => state.settings.fixedHeader
    }),
    classObj() {
      return {
        hideSidebar: !this.sidebar.opened,
        openSidebar: this.sidebar.opened,
        withoutAnimation: this.sidebar.withoutAnimation,
        mobile: this.device === 'mobile'
      }
    },
    variables() {
      return variables
    },
    // 当前签入的坐席
    agent() {
      return this.$store.state.agent.agent
    },
    // 拖拽拨号框显隐（由坐席状态栏"外呼"按钮打开）
    dialerVisible() {
      return this.$store.state.agent.dialerVisible
    }
  },
  methods: {
    handleClickOutside() {
      this.$store.dispatch('app/closeSideBar', { withoutAnimation: false })
    },
    setLayout() {
      this.$refs.settingRef.openSetting()
    },
    onDialerClose() {
      this.$store.dispatch('agent/closeDialer')
    },
    // 外呼成功后跳转到通话弹屏页
    onCallMade({ phone }) {
      this.$router.push({
        path: '/inbound/callPopup',
        query: { callerNumber: phone, direction: 'out', callStatus: '1' }
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
  @import "~@/assets/styles/mixin.scss";
  @import "~@/assets/styles/variables.scss";

  .app-wrapper {
    @include clearfix;
    position: relative;
    height: 100%;
    width: 100%;

    &.mobile.openSidebar {
      position: fixed;
      top: 0;
    }
  }

  .drawer-bg {
    background: #000;
    opacity: 0.3;
    width: 100%;
    top: 0;
    height: 100%;
    position: absolute;
    z-index: 999;
  }

  .fixed-header {
    position: fixed;
    top: 0;
    right: 0;
    z-index: 9;
    width: calc(100% - #{$base-sidebar-width});
    transition: width 0.28s;
  }

  .hideSidebar .fixed-header {
    width: calc(100% - 54px);
  }

  .sidebarHide .fixed-header {
    width: 100%;
  }

  .mobile .fixed-header {
    width: 100%;
  }
</style>
