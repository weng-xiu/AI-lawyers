<template>
  <div class="sidebar-logo-container" :class="{'collapse':collapse}" :style="{ backgroundColor: sideTheme === 'theme-dark' ? variables.menuBackground : variables.menuLightBackground }">
    <transition name="sidebarLogoFade">
      <router-link v-if="collapse" key="collapse" class="sidebar-logo-link" to="/">
        <div class="justice-logo-mini">
          <svg viewBox="0 0 120 120" width="36" height="36">
            <defs>
              <linearGradient id="justiceGradMini" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#ffffff"/>
                <stop offset="100%" style="stop-color:#e6ebff"/>
              </linearGradient>
            </defs>
            <circle cx="60" cy="50" r="26" fill="none" stroke="url(#justiceGradMini)" stroke-width="5"/>
            <rect x="55" y="76" width="10" height="24" fill="#ffffff"/>
            <rect x="42" y="96" width="36" height="7" rx="3" fill="#ffffff"/>
          </svg>
        </div>
      </router-link>
      <router-link v-else key="expand" class="sidebar-logo-link" to="/">
        <div class="justice-logo">
          <svg viewBox="0 0 120 120" width="40" height="40">
            <defs>
              <linearGradient id="justiceGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#ffffff"/>
                <stop offset="100%" style="stop-color:#e6ebff"/>
              </linearGradient>
            </defs>
            <circle cx="60" cy="46" r="24" fill="none" stroke="url(#justiceGrad)" stroke-width="4"/>
            <rect x="56" y="70" width="8" height="20" fill="#ffffff"/>
            <rect x="46" y="86" width="28" height="6" rx="2" fill="#ffffff"/>
          </svg>
        </div>
        <h1 class="sidebar-title" :style="{ color: sideTheme === 'theme-dark' ? variables.logoTitleColor : variables.logoLightTitleColor }">{{ title }}</h1>
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
    },
    sideTheme() {
      return this.$store.state.settings.sideTheme
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
  transition: opacity 1.5s;
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
  background: rgba(255, 255, 255, 0.1);
  text-align: center;
  overflow: hidden;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);

  & .sidebar-logo-link {
    height: 100%;
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;

    & .justice-logo,
    & .justice-logo-mini {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      vertical-align: middle;
    }

    & .justice-logo {
      margin-right: 10px;
    }

    & .sidebar-title {
      display: inline-block;
      margin: 0;
      color: #fff;
      font-weight: 600;
      line-height: 60px;
      font-size: 15px;
      font-family: Avenir, Helvetica Neue, Arial, Helvetica, sans-serif;
      vertical-align: middle;
    }
  }

  &.collapse {
    .justice-logo-mini {
      margin-right: 0px;
    }
  }
}
</style>
