<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="接收号码" prop="phone">
        <el-input v-model="queryParams.phone" placeholder="请输入接收号码" clearable style="width: 180px"
          @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="短信模板" prop="templateId">
        <el-select v-model="queryParams.templateId" placeholder="请选择模板" clearable filterable style="width: 180px">
          <el-option v-for="t in templateOptions" :key="t.templateId" :label="t.templateName" :value="t.templateId" />
        </el-select>
      </el-form-item>
      <el-form-item label="发送状态" prop="sendStatus">
        <el-select v-model="queryParams.sendStatus" placeholder="请选择状态" clearable style="width: 130px">
          <el-option label="成功" value="1" />
          <el-option label="失败" value="2" />
          <el-option label="待发" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-s-promotion" size="mini" @click="handleSend">调试发送</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:smsLog:remove']">删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="logList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" prop="logId" width="70" />
      <el-table-column label="接收号码" prop="phone" width="130" />
      <el-table-column label="模板" prop="templateName" width="120" :show-overflow-tooltip="true" />
      <el-table-column label="发送内容" prop="content" min-width="260" :show-overflow-tooltip="true" />
      <el-table-column label="状态" prop="sendStatus" width="80" align="center">
        <template slot-scope="scope">
          <el-tag :type="statusTag(scope.row.sendStatus)" size="small">{{ statusLabel(scope.row.sendStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="回执ID" prop="providerMsgId" width="170" :show-overflow-tooltip="true" />
      <el-table-column label="失败原因" prop="failReason" width="150" :show-overflow-tooltip="true" />
      <el-table-column label="发送时间" prop="createTime" width="160" align="center" />
      <el-table-column label="操作" width="80" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:smsLog:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
      @pagination="getList" />

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
      this.$refs["queryForm"].resetFields();
      this.handleQuery();
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
      this.$modal.confirm('是否确认删除选中的短信记录？').then(() => {
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
