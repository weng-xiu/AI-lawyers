<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="configList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      has-selection
      label-width="80px"
      empty-text="暂无短信通道"
      empty-hint="新增通道后可用于排队通知、工单通知等短信发送"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:smsConfig:add']">新增</el-button>
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['lawyers:smsConfig:edit']">修改</el-button>
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:smsConfig:remove']">删除</el-button>
      </template>
      <template #column-provider="{ row }">
        <el-tag :type="providerTag(row.provider)" size="small">{{ providerLabel(row.provider) }}</el-tag>
      </template>
      <template #column-status="{ row }">
        <el-switch v-model="row.status" active-value="1" inactive-value="0"
          @change="handleStatusChange(row)"></el-switch>
      </template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)"
          v-hasPermi="['lawyers:smsConfig:edit']">修改</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)"
          v-hasPermi="['lawyers:smsConfig:remove']">删除</el-button>
      </template>
    </ProTable>

    <!-- 新增/编辑对话框（V3 ProDialog） -->
    <pro-dialog
      v-model="open"
      :title="title"
      width="620px"
      footer-size="medium"
      :confirm-loading="submitting"
      :dirty="formDirty"
      @confirm="submitForm"
      @closed="reset"
    >
      <!-- V3 ProForm 自由模式：无 schema，pro-form-item（el-col+el-form-item）手工栅格，双列 span=12 -->
      <pro-form
        ref="form"
        :model="form"
        :rules="rules"
        label-width="110px"
        size="medium"
        :gutter="0"
      >
        <pro-form-item label="通道名称" prop="configName" :span="12">
          <el-input v-model="form.configName" placeholder="请输入通道名称" />
        </pro-form-item>
        <pro-form-item label="供应商" prop="provider" :span="12">
          <el-select v-model="form.provider" placeholder="请选择供应商" style="width: 100%">
            <el-option label="本地模拟（无凭证）" value="mock" />
            <el-option label="阿里云短信" value="aliyun" />
            <el-option label="腾讯云短信" value="tencent" />
          </el-select>
        </pro-form-item>
        <pro-form-item label="短信签名" prop="signName">
          <el-input v-model="form.signName" placeholder="如：AI法律咨询" />
        </pro-form-item>
        <template v-if="form.provider !== 'mock'">
          <pro-form-item label="AccessKey ID" prop="accessKeyId">
            <el-input v-model="form.accessKeyId" placeholder="云厂商访问密钥ID" />
          </pro-form-item>
          <pro-form-item label="AccessKey Secret" prop="accessKeySecret">
            <el-input v-model="form.accessKeySecret" type="password" show-password placeholder="云厂商访问密钥Secret" />
          </pro-form-item>
          <pro-form-item v-if="form.provider === 'aliyun'" label="地域" prop="regionId">
            <el-input v-model="form.regionId" placeholder="如 cn-hangzhou" />
          </pro-form-item>
          <pro-form-item v-if="form.provider === 'tencent'" label="SDK AppId" prop="sdkAppId">
            <el-input v-model="form.sdkAppId" placeholder="腾讯云短信应用AppId" />
          </pro-form-item>
        </template>
        <pro-form-item label="日发送上限" prop="dailyLimit" :span="12">
          <el-input-number v-model="form.dailyLimit" :min="1" :max="1000" />
        </pro-form-item>
        <pro-form-item label="状态" prop="status" :span="12">
          <el-radio-group v-model="form.status">
            <el-radio label="1">启用</el-radio>
            <el-radio label="0">停用</el-radio>
          </el-radio-group>
        </pro-form-item>
        <pro-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </pro-form-item>
      </pro-form>
    </pro-dialog>
  </div>
</template>

<script>
import { listSmsConfig, getSmsConfig, addSmsConfig, updateSmsConfig, delSmsConfig } from "@/api/lawyers/sms";

export default {
  name: "SmsConfig",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      configList: [],
      // V3：ProTable 列定义（供应商/状态/操作走插槽）
      columns: [
        { prop: 'configId', label: 'ID', width: 70, align: 'center' },
        { prop: 'configName', label: '通道名称', minWidth: 160, align: 'left', showOverflowTooltip: true },
        { prop: 'provider', label: '供应商', width: 110, align: 'center', slot: true },
        { prop: 'signName', label: '短信签名', width: 140, align: 'left', showOverflowTooltip: true },
        { prop: 'dailyLimit', label: '日发送上限', width: 100, align: 'center' },
        { prop: 'status', label: '状态', width: 80, align: 'center', slot: true },
        { prop: 'createTime', label: '创建时间', width: 160, align: 'center' },
        { prop: 'action', label: '操作', width: 150, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      title: "",
      open: false,
      // V3 ProDialog：提交防重锁 + 脏改动快照
      submitting: false,
      formSnapshot: "",
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        configName: undefined,
        provider: undefined,
        status: undefined
      },
      form: {},
      rules: {
        configName: [{ required: true, message: "通道名称不能为空", trigger: "blur" }],
        provider: [{ required: true, message: "请选择供应商", trigger: "change" }]
      }
    };
  },
  computed: {
    queryFormItems() {
      return [
        { prop: 'configName', label: '通道名称', placeholder: '请输入通道名称', width: 200 },
        {
          prop: 'provider',
          label: '供应商',
          type: 'select',
          placeholder: '请选择供应商',
          width: 160,
          options: [
            { label: '本地模拟', value: 'mock' },
            { label: '阿里云', value: 'aliyun' },
            { label: '腾讯云', value: 'tencent' }
          ]
        },
        {
          prop: 'status',
          label: '状态',
          type: 'select',
          placeholder: '请选择状态',
          width: 120,
          options: [
            { label: '启用', value: '1' },
            { label: '停用', value: '0' }
          ]
        }
      ]
    },
    /** 表单相对打开时是否有改动（供 ProDialog 关闭拦截） */
    formDirty() {
      return this.open && this.formSnapshot !== JSON.stringify(this.form);
    }
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      this.loading = true;
      listSmsConfig(this.queryParams).then(response => {
        this.configList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    providerLabel(p) {
      return { mock: "本地模拟", aliyun: "阿里云", tencent: "腾讯云" }[p] || p;
    },
    providerTag(p) {
      return { mock: "info", aliyun: "warning", tencent: "success" }[p] || "";
    },
    reset() {
      this.form = {
        configId: null, configName: null, provider: "mock", accessKeyId: null, accessKeySecret: null,
        signName: null, regionId: null, sdkAppId: null, dailyLimit: 10, status: "1", remark: null
      };
      this.formSnapshot = JSON.stringify(this.form);
      this.$nextTick(() => { this.$refs["form"] && this.$refs["form"].clearValidate(); });
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        configName: undefined,
        provider: undefined,
        status: undefined
      }
      this.getList()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.configId);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    handleAdd() {
      this.reset();
      this.title = "新增短信通道";
      this.open = true;
    },
    handleUpdate(row) {
      this.reset();
      const configId = row.configId || this.ids[0];
      getSmsConfig(configId).then(response => {
        this.form = response.data;
        this.formSnapshot = JSON.stringify(this.form);
        this.open = true;
        this.title = "修改短信通道";
      });
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (!valid || this.submitting) return;
        this.submitting = true;
        const isEdit = this.form.configId != null;
        const request = isEdit ? updateSmsConfig(this.form) : addSmsConfig(this.form);
        request.then(() => {
          this.$modal.msgSuccess(isEdit ? "修改成功" : "新增成功");
          this.open = false;
          this.getList();
        }).finally(() => {
          this.submitting = false;
        });
      });
    },
    handleDelete(row) {
      const configIds = row.configId ? [row.configId] : this.ids;
      this.$modal.dangerConfirm({
        content: '将删除选中的短信通道配置',
        count: configIds.length,
        countNoun: '个通道'
      }).then(() => {
        return delSmsConfig(configIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    handleStatusChange(row) {
      const text = row.status === "1" ? "启用" : "停用";
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: `确认${text}短信通道「${row.configName}」吗？`,
        tip: row.status === "1" ? '启用后该通道将参与短信发送与故障切换' : '停用后该通道不再发送短信，可在 30 秒内撤销'
      }).then(() => {
        return updateSmsConfig({ configId: row.configId, status: row.status });
      }).then(() => {
        // U2：通道启停 30 秒内可撤销
        const oldStatus = row.status === "1" ? "0" : "1";
        this.$modal.undo({
          message: '短信通道已' + text,
          undoSuccessText: '已撤销，通道已' + (oldStatus === "1" ? "启用" : "停用"),
          onUndo: () => updateSmsConfig({ configId: row.configId, status: oldStatus }).then(() => {
            row.status = oldStatus;
          })
        });
      }).catch(() => {
        row.status = row.status === "1" ? "0" : "1";
      });
    }
  }
};
</script>
