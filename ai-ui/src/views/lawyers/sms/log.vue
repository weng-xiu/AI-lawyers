<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="logList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      has-selection
      label-width="80px"
      empty-text="暂无短信发送记录"
      empty-hint="调试发送或业务触发后将在此留痕"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-s-promotion" size="mini" @click="handleSend">调试发送</el-button>
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:smsLog:remove']">删除</el-button>
      </template>
      <template #column-sendStatus="{ row }">
        <el-tag :type="statusTag(row.sendStatus)" size="small">{{ statusLabel(row.sendStatus) }}</el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)"
          v-hasPermi="['lawyers:smsLog:remove']">删除</el-button>
      </template>
    </ProTable>

    <!-- 调试发送对话框 -->
    <el-dialog title="调试发送短信" :visible.sync="sendOpen" width="520px" append-to-body>
      <el-form ref="sendForm" :model="sendForm" :rules="sendRules" label-width="90px">
        <el-form-item label="接收号码" prop="phone">
          <el-input v-model="sendForm.phone" placeholder="请输入接收手机号" />
        </el-form-item>
        <el-form-item label="短信模板" prop="templateId">
          <el-select v-model="sendForm.templateId" placeholder="请选择短信模板" filterable style="width: 100%">
            <el-option v-for="t in templateOptions" :key="t.templateId" :label="t.templateName" :value="t.templateId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentTemplate" label="模板内容">
          <div class="sms-template-content">{{ currentTemplate.content }}</div>
        </el-form-item>
        <el-form-item label="模板参数">
          <el-input v-model="sendForm.params" type="textarea" :rows="3"
            placeholder='JSON，如 {"queuePos":"3"}' />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitSend">发 送</el-button>
        <el-button @click="sendOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSmsLog, delSmsLog, sendSms, listEnabledTemplates } from "@/api/lawyers/sms";

export default {
  name: "SmsLog",
  data() {
    return {
      loading: true,
      ids: [],
      multiple: true,
      showSearch: true,
      total: 0,
      logList: [],
      // V3：ProTable 列定义（发送状态/操作走插槽）
      columns: [
        { prop: 'logId', label: 'ID', width: 70, align: 'center' },
        { prop: 'phone', label: '接收号码', width: 130, align: 'center' },
        { prop: 'templateName', label: '模板', width: 120, align: 'left', showOverflowTooltip: true },
        { prop: 'content', label: '发送内容', minWidth: 260, align: 'left', showOverflowTooltip: true },
        { prop: 'sendStatus', label: '状态', width: 80, align: 'center', slot: true },
        { prop: 'providerMsgId', label: '回执ID', width: 170, align: 'left', showOverflowTooltip: true },
        { prop: 'failReason', label: '失败原因', width: 150, align: 'left', showOverflowTooltip: true },
        { prop: 'createTime', label: '发送时间', width: 160, align: 'center' },
        { prop: 'action', label: '操作', width: 80, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      templateOptions: [],
      sendOpen: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        phone: undefined,
        templateId: undefined,
        sendStatus: undefined
      },
      sendForm: {
        phone: undefined,
        templateId: undefined,
        params: ''
      },
      sendRules: {
        phone: [{ required: true, message: "接收号码不能为空", trigger: "blur" }],
        templateId: [{ required: true, message: "请选择短信模板", trigger: "change" }]
      }
    };
  },
  computed: {
    queryFormItems() {
      return [
        { prop: 'phone', label: '接收号码', placeholder: '请输入接收号码', width: 180 },
        {
          prop: 'templateId',
          label: '短信模板',
          type: 'select',
          placeholder: '请选择模板',
          width: 180,
          filterable: true,
          options: this.templateOptions.map(t => ({ label: t.templateName, value: t.templateId }))
        },
        {
          prop: 'sendStatus',
          label: '发送状态',
          type: 'select',
          placeholder: '请选择状态',
          width: 130,
          options: [
            { label: '成功', value: '1' },
            { label: '失败', value: '2' },
            { label: '待发', value: '0' }
          ]
        }
      ]
    },
    currentTemplate() {
      if (!this.sendForm.templateId) return null;
      return this.templateOptions.find(t => t.templateId === this.sendForm.templateId) || null;
    }
  },
  created() {
    this.getList();
    this.loadTemplates();
  },
  methods: {
    getList() {
      this.loading = true;
      listSmsLog(this.queryParams).then(response => {
        this.logList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    loadTemplates() {
      listEnabledTemplates().then(res => {
        this.templateOptions = res.data || [];
      }).catch(() => {
        this.templateOptions = [];
      });
    },
    statusLabel(s) {
      return { '0': "待发", '1': "成功", '2': "失败" }[s] || s;
    },
    statusTag(s) {
      return { '0': "info", '1': "success", '2': "danger" }[s] || "";
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
        phone: undefined,
        templateId: undefined,
        sendStatus: undefined
      }
      this.getList()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.logId);
      this.multiple = !selection.length;
    },
    handleSend() {
      this.sendForm = { phone: undefined, templateId: undefined, params: '' };
      this.sendOpen = true;
    },
    submitSend() {
      this.$refs["sendForm"].validate(valid => {
        if (!valid) return;
        let params = {};
        if (this.sendForm.params && this.sendForm.params.trim()) {
          try {
            params = JSON.parse(this.sendForm.params);
          } catch (e) {
            this.$modal.msgError("模板参数不是合法 JSON");
            return;
          }
        }
        sendSms({
          phone: this.sendForm.phone,
          templateId: this.sendForm.templateId,
          params: params
        }).then(res => {
          const data = res.data || {};
          this.$modal.msgSuccess("发送成功，回执：" + (data.msgId || "-"));
          this.sendOpen = false;
          this.getList();
        }).catch(() => {});
      });
    },
    handleDelete(row) {
      const logIds = row.logId ? [row.logId] : this.ids;
      this.$modal.dangerConfirm({
        content: '将删除选中的短信发送记录',
        count: logIds.length,
        countNoun: '条记录'
      }).then(() => {
        return delSmsLog(logIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    }
  }
};
</script>

<style scoped>
.sms-template-content {
  font-size: 13px;
  color: #5A6A7E;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 8px 10px;
  line-height: 1.6;
  word-break: break-all;
}
</style>
