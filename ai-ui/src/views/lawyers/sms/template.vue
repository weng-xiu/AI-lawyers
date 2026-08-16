<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="模板名称" prop="templateName">
        <el-input v-model="queryParams.templateName" placeholder="请输入模板名称" clearable style="width: 200px"
          @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="场景" prop="sceneType">
        <el-select v-model="queryParams.sceneType" placeholder="请选择场景" clearable style="width: 150px">
          <el-option label="排队通知" value="queue" />
          <el-option label="欢迎语" value="welcome" />
          <el-option label="工单通知" value="ticket" />
          <el-option label="回访提醒" value="visit" />
          <el-option label="通用" value="generic" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
          <el-option label="启用" value="1" />
          <el-option label="停用" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:smsTemplate:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['lawyers:smsTemplate:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:smsTemplate:remove']">删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="templateList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" prop="templateId" width="70" />
      <el-table-column label="模板名称" prop="templateName" :show-overflow-tooltip="true" />
      <el-table-column label="模板内容" prop="content" min-width="260" :show-overflow-tooltip="true" />
      <el-table-column label="场景" prop="sceneType" width="110" align="center">
        <template slot-scope="scope">
          <el-tag :type="sceneTag(scope.row.sceneType)" size="small">{{ sceneLabel(scope.row.sceneType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="所属通道" prop="configName" width="130" :show-overflow-tooltip="true" />
      <el-table-column label="状态" prop="status" width="80" align="center">
        <template slot-scope="scope">
          <el-switch v-model="scope.row.status" active-value="1" inactive-value="0"
            @change="handleStatusChange(scope.row)"></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:smsTemplate:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:smsTemplate:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
      @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="模板名称" prop="templateName">
              <el-input v-model="form.templateName" placeholder="请输入模板名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="场景" prop="sceneType">
              <el-select v-model="form.sceneType" placeholder="请选择场景" style="width: 100%">
                <el-option label="排队通知" value="queue" />
                <el-option label="欢迎语" value="welcome" />
                <el-option label="工单通知" value="ticket" />
                <el-option label="回访提醒" value="visit" />
                <el-option label="通用" value="generic" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="所属通道" prop="configId">
          <el-select v-model="form.configId" placeholder="请选择短信通道" style="width: 100%">
            <el-option v-for="c in configOptions" :key="c.configId" :label="c.configName" :value="c.configId" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商CODE">
          <el-input v-model="form.providerTemplateCode" placeholder="供应商模板CODE，如 SMS_123456" />
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3"
            placeholder="短信内容，变量用 ${变量名} 占位，如：【AI法律咨询】您当前排队第${queuePos}位。" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio label="1">启用</el-radio>
            <el-radio label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSmsTemplate, getSmsTemplate, addSmsTemplate, updateSmsTemplate, delSmsTemplate, listEnabledConfigs } from "@/api/lawyers/sms";

export default {
  name: "SmsTemplate",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      templateList: [],
      configOptions: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        templateName: undefined,
        sceneType: undefined,
        status: undefined
      },
      form: {},
      rules: {
        templateName: [{ required: true, message: "模板名称不能为空", trigger: "blur" }],
        content: [{ required: true, message: "模板内容不能为空", trigger: "blur" }],
        configId: [{ required: true, message: "请选择所属通道", trigger: "change" }]
      }
    };
  },
  created() {
    this.getList();
    this.loadConfigs();
  },
  methods: {
    getList() {
      this.loading = true;
      listSmsTemplate(this.queryParams).then(response => {
        this.templateList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    loadConfigs() {
      listEnabledConfigs().then(res => {
        this.configOptions = res.data || [];
      }).catch(() => {
        this.configOptions = [];
      });
    },
    sceneLabel(s) {
      return { queue: "排队通知", welcome: "欢迎语", ticket: "工单通知", visit: "回访提醒", generic: "通用" }[s] || s;
    },
    sceneTag(s) {
      return { queue: "warning", welcome: "success", ticket: "danger", visit: "info", generic: "" }[s] || "";
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {
        templateId: null, templateName: null, providerTemplateCode: null, content: null,
        sceneType: "generic", configId: null, status: "1", remark: null
      };
      this.$nextTick(() => { this.$refs["form"] && this.$refs["form"].clearValidate(); });
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
      this.ids = selection.map(item => item.templateId);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    handleAdd() {
      this.reset();
      this.title = "新增短信模板";
      this.open = true;
    },
    handleUpdate(row) {
      this.reset();
      const templateId = row.templateId || this.ids[0];
      getSmsTemplate(templateId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改短信模板";
      });
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (!valid) return;
        if (this.form.templateId != null) {
          updateSmsTemplate(this.form).then(() => {
            this.$modal.msgSuccess("修改成功");
            this.open = false;
            this.getList();
          });
        } else {
          addSmsTemplate(this.form).then(() => {
            this.$modal.msgSuccess("新增成功");
            this.open = false;
            this.getList();
          });
        }
      });
    },
    handleDelete(row) {
      const templateIds = row.templateId ? [row.templateId] : this.ids;
      this.$modal.confirm('是否确认删除选中的短信模板？').then(() => {
        return delSmsTemplate(templateIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    handleStatusChange(row) {
      const text = row.status === "1" ? "启用" : "停用";
      this.$modal.confirm(`确认${text}"${row.templateName}"吗？`).then(() => {
        return updateSmsTemplate({ templateId: row.templateId, status: row.status });
      }).then(() => {
        this.$modal.msgSuccess(text + "成功");
      }).catch(() => {
        row.status = row.status === "1" ? "0" : "1";
      });
    }
  }
};
</script>
