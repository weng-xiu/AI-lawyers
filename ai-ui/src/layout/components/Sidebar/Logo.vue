<template>
  <div class="sidebar-logo-container" :class="{'collapse':collapse}" :style="{ backgroundColor: variables.logoBackground }">
    <transition name="sidebarLogoFade">
      <router-link class="sidebar-logo-link" to="/">
        <img class="sidebar-logo-img" :class="{'sidebar-logo-img--collapse': collapse}" src="@/assets/images/callcenterlogo.png" alt="系统logo" />
        <h1 v-if="!collapse" class="sidebar-title">{{ title }}</h1>
      </router-link>
    </transition>
  </div>
</template>

<script>
import variables from '@/assets/styles/variables.scss'

export default {
  name: 'SidebarLogo',
  props: {
    collapse: {
      type: Boolean,
      required: true
    }
  },
  computed: {
    variables() {
      return variables
    }
  },
  data() {
    return {
      title: process.env.VUE_APP_TITLE
    }
  }
}
</script>

<style lang="scss" scoped>
.sidebarLogoFade-enter-active {
  transition: opacity 0.35s;
}

.sidebarLogoFade-enter,
.sidebarLogoFade-leave-to {
  opacity: 0;
}

.sidebar-logo-container {
  position: relative;
  width: 100%;
  height: 60px;
  line-height: 60px;
  background: #1A3C6E;
  text-align: center;
  overflow: hidden;
  border-bottom: 1px solid #16335C;

  .sidebar-logo-link {
    height: 100%;
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    text-decoration: none;

    .sidebar-logo-img {
      width: 42px;
      height: 42px;
      object-fit: contain;
      flex-shrink: 0;
      vertical-align: middle;
      border-radius: 6px;
      background: #FFFFFF;
      padding: 2px;
      box-sizing: border-box;
    }

    .sidebar-logo-img--collapse {
      width: 38px;
      height: 38px;
    }

    .sidebar-title {
      display: inline-block;
      margin: 0 0 0 10px;
      color: #FFFFFF;
      font-weight: 600;
      line-height: 60px;
      font-size: 15px;
      font-family: Avenir, Helvetica Neue, Arial, Helvetica, sans-serif;
      vertical-align: middle;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}
</style>
