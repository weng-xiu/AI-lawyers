<template>
  <div class="portal-page my-channels">
    <section class="page-wrap" aria-label="渠道绑定">
      <div class="page-head">
        <h2 class="page-title">渠道绑定</h2>
        <p class="page-sub">绑定微信、H5 等渠道后，跨渠道咨询无需重复陈述身份；绑定与解绑均需本人确认</p>
      </div>

      <!-- 绑定须知 -->
      <el-alert
        class="bind-tip"
        type="info"
        :closable="false"
        show-icon
        title="绑定须二次确认，换号/过户后请及时解绑"
        description="手机号过户或多人共用号码时，原绑定身份可能错并他人服务记录，请及时解绑或重新绑定。微信渠道的授权自动回填能力随三期渠道入口开通。" />

      <!-- 已绑定渠道 -->
      <h3 class="block-title">已绑定渠道</h3>
      <div v-loading="loading">
        <div v-if="channelList.length === 0 && !loading" class="empty-box" role="status">
          <i class="el-icon-connection"></i>
          <p>暂未绑定任何渠道</p>
          <p class="empty-tip">绑定后可享受跨渠道统一服务</p>
        </div>

        <el-card v-for="item in channelList" :key="item.id" class="channel-card" shadow="hover">
          <div class="channel-row">
            <span class="channel-icon" aria-hidden="true">
              <i :class="typeIcon(item.channelType)"></i>
            </span>
            <div class="channel-info">
              <div class="channel-name">
                {{ typeText(item.channelType) }}
                <el-tag v-if="item.channelNickname" size="mini" type="info">{{ item.channelNickname }}</el-tag>
              </div>
              <div class="channel-uid">渠道标识：{{ maskUid(item.channelUid) }}</div>
              <div class="channel-time">绑定时间：{{ formatTime(item.bindConfirmTime) }}</div>
            </div>
            <el-button
              type="danger"
              plain
              class="unbind-btn"
              :aria-label="'解除绑定' + typeText(item.channelType)"
              @click="handleUnbind(item)">解绑</el-button>
          </div>
        </el-card>
      </div>

      <!-- 绑定新渠道 -->
      <h3 class="block-title">绑定新渠道</h3>
      <el-card class="bind-form-card" shadow="never">
        <el-form ref="bindForm" :model="bindForm" :rules="bindRules" label-width="96px" label-position="left">
          <el-form-item label="渠道类型" prop="channelType">
            <el-select v-model="bindForm.channelType" placeholder="请选择要绑定的渠道" style="width: 240px" aria-label="渠道类型">
              <el-option v-for="t in bindableTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="渠道标识" prop="channelUid">
            <el-input
              v-model.trim="bindForm.channelUid"
              maxlength="64"
              show-word-limit
              placeholder="微信 OpenID/UnionID 或平台账号（三期起由微信授权自动获取）"
              style="width: 360px"
              aria-label="渠道标识"
              @keyup.enter.native="handleBind" />
          </el-form-item>
          <el-form-item label="渠道昵称" prop="channelNickname">
            <el-input
              v-model.trim="bindForm.channelNickname"
              maxlength="32"
              placeholder="选填，便于识别（如微信昵称）"
              style="width: 360px"
              aria-label="渠道昵称" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="big-btn" :loading="binding" @click="handleBind">发起绑定</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </section>
  </div>
</template>

<script>
import { myChannels, requestChannelBind, confirmChannelBind, unbindChannel } from '@/api/portal'
import { announce } from '@/utils/a11y'

export default {
  name: 'MyChannels',
  data() {
    return {
      loading: false,
      binding: false,
      channelList: [],
      bindableTypes: [
        { value: 'WECHAT_MP', label: '微信公众号' },
        { value: 'WECHAT_MINI', label: '微信小程序' },
        { value: 'H5', label: 'H5 页面' },
        { value: 'WEB', label: '网站账号' }
      ],
      bindForm: { channelType: '', channelUid: '', channelNickname: '' },
      bindRules: {
        channelType: [{ required: true, message: '请选择渠道类型', trigger: 'change' }],
        channelUid: [
          { required: true, message: '请输入渠道标识', trigger: 'blur' },
          { min: 4, max: 64, message: '长度 4 到 64 个字符', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    this.loadChannels()
  },
  methods: {
    loadChannels() {
      this.loading = true
      myChannels().then(res => {
        this.channelList = res.data || []
        const n = this.channelList.length
        announce(n > 0 ? `已绑定 ${n} 个渠道` : '暂未绑定任何渠道，可在下方发起绑定', 'polite')
      }).catch(() => {
        announce('渠道绑定信息加载失败，请稍后重试', 'assertive')
      }).finally(() => { this.loading = false })
    },
    handleBind() {
      this.$refs.bindForm.validate(valid => {
        if (!valid) return
        const payload = {
          channelType: this.bindForm.channelType,
          channelUid: this.bindForm.channelUid,
          channelNickname: this.bindForm.channelNickname || null
        }
        this.binding = true
        requestChannelBind(payload).then(res => {
          const bindId = res.data
          // 二次确认（短信验证码/微信授权通道随三期开通，先做本人确认闭环）
          this.$confirm(
            `请确认是您本人发起的绑定操作：将「${this.typeText(payload.channelType)}」与当前账号关联。确认后立即生效，您可随时解绑。`,
            '绑定二次确认',
            {
              confirmButtonText: '确认绑定',
              cancelButtonText: '取消',
              type: 'warning',
              confirmButtonClass: 'big-btn'
            }
          ).then(() => {
            // 用户已点确认：确认请求失败与用户取消分开处理
            return confirmChannelBind({ id: bindId }).then(() => {
              this.$message.success('绑定成功')
              announce('渠道绑定成功', 'polite')
              this.resetForm()
              this.loadChannels()
            }).catch(() => {
              // 错误提示已由 http 拦截器弹出，此处仅补读屏播报
              announce('渠道绑定确认失败，请稍后重试', 'assertive')
            })
          }).catch(() => {
            // 用户取消二次确认：绑定记录保持待确认状态，不生效
            this.$message.info('已取消，绑定未生效')
            announce('已取消绑定，绑定未生效', 'polite')
          })
        }).finally(() => { this.binding = false })
      })
    },
    handleUnbind(item) {
      this.$confirm(
        `确定解除「${this.typeText(item.channelType)}」的绑定吗？解绑后该渠道将不再与您关联，历史服务记录不受影响。`,
        '解绑确认',
        {
          confirmButtonText: '确定解绑',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        return unbindChannel({ id: item.id })
      }).then(() => {
        this.$message.success('已解绑')
        announce(`${this.typeText(item.channelType)}已解绑`, 'polite')
        this.loadChannels()
      }).catch(() => {})
    },
    resetForm() {
      this.bindForm = { channelType: '', channelUid: '', channelNickname: '' }
      this.$refs.bindForm && this.$refs.bindForm.clearValidate()
    },
    typeText(t) {
      const map = {
        PHONE: '电话',
        WECHAT_MP: '微信公众号',
        WECHAT_MINI: '微信小程序',
        H5: 'H5 页面',
        WEB: '网站账号'
      }
      return map[t] || t
    },
    typeIcon(t) {
      const map = {
        PHONE: 'el-icon-phone-outline',
        WECHAT_MP: 'el-icon-chat-dot-round',
        WECHAT_MINI: 'el-icon-mobile-phone',
        H5: 'el-icon-cellphone',
        WEB: 'el-icon-monitor'
      }
      return map[t] || 'el-icon-connection'
    },
    maskUid(uid) {
      if (!uid) return '-'
      const s = String(uid)
      if (s.length <= 8) return s.substring(0, 2) + '****'
      return s.substring(0, 4) + '****' + s.substring(s.length - 4)
    },
    formatTime(t) {
      if (!t) return '-'
      return String(t).replace('T', ' ').substring(0, 16)
    }
  }
}
</script>

<style scoped>
.portal-page {
  min-height: calc(100vh - 120px);
  background: linear-gradient(180deg, #f0f4fa 0%, #f7f9fc 100%);
  padding: 24px 0 40px;
}
.page-wrap { max-width: 960px; margin: 0 auto; padding: 0 20px; }
.page-head { margin-bottom: 18px; }
.page-title { font-size: 24px; color: #0b1f4a; margin: 0 0 6px; }
.page-sub { color: #5b6b84; font-size: 14px; margin: 0; }

.bind-tip { margin-bottom: 22px; border-radius: 6px; }

.block-title {
  font-size: 17px;
  color: #0b1f4a;
  margin: 0 0 12px;
  padding-left: 10px;
  border-left: 4px solid #255A99;
}

.channel-card { margin-bottom: 14px; border-radius: 8px; border-left: 4px solid #255A99; }
.channel-row { display: flex; align-items: center; gap: 16px; }
.channel-icon {
  flex: none;
  width: 52px; height: 52px;
  display: flex; align-items: center; justify-content: center;
  background: #eef4fc; border-radius: 10px;
  color: #255A99; font-size: 26px;
}
.channel-info { flex: 1; min-width: 0; }
.channel-name { font-size: 16px; font-weight: 600; color: #0b1f4a; margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
.channel-uid, .channel-time { font-size: 13px; color: #8a94a6; line-height: 1.8; }
.unbind-btn { flex: none; min-height: 44px; }

.bind-form-card { border-radius: 8px; }
.big-btn { min-height: 44px; padding: 10px 28px; font-size: 16px; }

.empty-box { text-align: center; padding: 50px 0; color: #8a94a6; }
.empty-box i { font-size: 56px; color: #c4cedd; }
.empty-box p { margin: 12px 0 4px; font-size: 15px; }
.empty-tip { font-size: 13px; color: #a8b3c5; }

/* ===== F2 适老化：大字 / 超大 / 高对比 均由 html 根类驱动 ===== */
html.care-large .page-title { font-size: 28px; }
html.care-large .page-sub,
html.care-large .channel-uid,
html.care-large .channel-time,
html.care-large .channel-name { font-size: 17px; }
html.care-large .block-title { font-size: 20px; }
html.care-large .unbind-btn,
html.care-large .big-btn { min-height: 48px; font-size: 17px; }
html.care-large .channel-icon { width: 56px; height: 56px; }

html.care-xlarge .page-title { font-size: 32px; }
html.care-xlarge .page-sub,
html.care-xlarge .channel-uid,
html.care-xlarge .channel-time { font-size: 20px; }
html.care-xlarge .channel-name { font-size: 22px; }
html.care-xlarge .block-title { font-size: 24px; }
html.care-xlarge .unbind-btn,
html.care-xlarge .big-btn { min-height: 52px; font-size: 19px; padding: 12px 24px; }
html.care-xlarge .channel-icon { width: 64px; height: 64px; font-size: 30px; }
html.care-xlarge .channel-row { gap: 20px; }

html.care-high-contrast .portal-page { background: #fff; }
html.care-high-contrast .page-title,
html.care-high-contrast .block-title,
html.care-high-contrast .channel-name { color: #000; }
html.care-high-contrast .page-sub,
html.care-high-contrast .channel-uid,
html.care-high-contrast .channel-time { color: #000; }
html.care-high-contrast .block-title { border-left-color: #000; }
html.care-high-contrast .channel-card { border: 2px solid #000; border-left: 6px solid #000; }
html.care-high-contrast .bind-form-card { border: 2px solid #000; }
html.care-high-contrast .channel-icon { background: #fff; color: #000; border: 2px solid #000; }
html.care-high-contrast .empty-tip { color: #333; }
</style>
