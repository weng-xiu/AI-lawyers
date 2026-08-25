<template>
  <el-select
    :value="value"
    :placeholder="placeholder"
    :multiple="multiple"
    :clearable="clearable"
    :filterable="filterable"
    :disabled="disabled"
    :size="size"
    :style="{ width: width }"
    @input="onInput"
    @change="onChange"
    @clear="onClear"
  >
    <el-option
      v-for="u in users"
      :key="u.userId"
      :label="u.nickName ? (u.nickName + '（' + u.userName + '）') : u.userName"
      :value="u.userId"
    />
  </el-select>
</template>

<script>
import { listUser } from '@/api/system/user'

/**
 * 系统用户选择组件（统一复用，避免各页面重复加载 sys_user）
 *
 * 用法：
 *   <user-select v-model="form.lawyerId" lawyer @change="onLawyerChange" />
 *   <user-select v-model="form.assignUserId" @change="v => form.assignUserName = $event.nickName" />
 *   <user-select v-model="queryParams.userIds" multiple :max-tag-count="3" />
 *
 * Props：
 *   lawyer   - 仅显示 lawyer_flag=1 的律师用户
 *   status   - 用户状态筛选，默认 '0'（正常）；传 null 则不过滤
 *   multiple - 多选
 */
export default {
  name: 'UserSelect',
  props: {
    value: { type: [Number, String, Array], default: null },
    placeholder: { type: String, default: '请选择用户' },
    multiple: { type: Boolean, default: false },
    clearable: { type: Boolean, default: true },
    filterable: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false },
    size: { type: String, default: 'mini' },
    width: { type: String, default: '100%' },
    // true 仅律师；false 全部正常用户
    lawyer: { type: Boolean, default: false },
    // 用户状态，null 表示不过滤
    status: { type: String, default: '0' }
  },
  data() {
    return { users: [] }
  },
  created() {
    this.loadUsers()
  },
  methods: {
    loadUsers() {
      const params = { pageNum: 1, pageSize: 1000 }
      if (this.lawyer) params.lawyerFlag = '1'
      if (this.status) params.status = this.status
      listUser(params).then(res => {
        this.users = res.rows || []
      })
    },
    onInput(val) {
      this.$emit('input', val)
    },
    onChange(val) {
      if (this.multiple) {
        const selected = this.users.filter(u => val.includes(u.userId))
        this.$emit('change', val, selected)
      } else {
        const user = this.users.find(u => u.userId === val)
        this.$emit('change', val, user)
      }
    },
    onClear() {
      this.$emit('change', this.multiple ? [] : null, this.multiple ? [] : null)
    }
  }
}
</script>
