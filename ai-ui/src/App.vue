<template>
  <div id="app">
    <router-view />
    <theme-picker />
    <!-- 浏览器 SIP 来电浮层 -->
    <sip-incoming-alert v-if="sipCall && sipCall.direction === 'incoming' && sipCall.state === 'ringing'" />
  </div>
</template>

<script>
import ThemePicker from "@/components/ThemePicker"
import SipIncomingAlert from "@/components/CallCenter/SipIncomingAlert"
import { mapGetters } from "vuex"

export default {
  name: "App",
  components: { ThemePicker, SipIncomingAlert },
  computed: {
    ...mapGetters(['sipCall'])
  },
  created() {
    // 绑定浏览器侧 SIP 软电话事件（仅注册监听器，真正注册发生在坐席签入后）
    if (this.$store && this.$store.dispatch) {
      this.$store.dispatch('agent/bindSipPhone')
    }
  }
}
</script>
<style scoped>
#app .theme-picker {
  display: none;
}
</style>
