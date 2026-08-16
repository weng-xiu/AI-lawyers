<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="通道名称" prop="configName">
        <el-input v-model="queryParams.configName" placeholder="请输入通道名称" clearable style="width: 200px"
          @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商" prop="provider">
        <el-select v-model="queryParams.provider" placeholder="请选择供应商" clearable style="width: 160px">
          <el-option label="本地模拟" value="mock" />
          <el-option label="阿里云" value="aliyun" />
          <el-option label="腾讯云" value="tencent" />
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
          v-hasPermi="['lawyers:smsConfig:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['lawyers:smsConfig:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:smsConfig:remove']">删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="configList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" prop="configId" width="70" />
      <el-table-column label="通道名称" prop="configName" :show-overflow-tooltip="true" />
      <el-table-column label="供应商" prop="provider" width="110" align="center">
        <template slot-scope="scope">
          <el-tag :type="providerTag(scope.row.provider)" size="small">{{ providerLabel(scope.row.provider) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="短信签名" prop="signName" width="140" :show-overflow-tooltip="true" />
      <el-table-column label="日发送上限" prop="dailyLimit" width="100" align="center" />
      <el-table-column label="状态" prop="status" width="80" align="center">
        <template slot-scope="scope">
          <el-switch v-model="scope.row.status" active-value="1" inactive-value="0"
            @change="handleStatusChange(scope.row)"></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" align="center" />
      <el-table-column label="操作" width="150" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:smsConfig:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:smsConfig:remove']">删除</el-button>
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
            <el-form-item label="通道名称" prop="configName">
              <el-input v-model="form.configName" placeholder="请输入通道名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择供应商" style="width: 100%">
                <el-option label="本地模拟（无凭证）" value="mock" />
                <el-option label="阿里云短信" value="aliyun" />
                <el-option label="腾讯云短信" value="tencent" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="短信签名">
          <el-input v-model="form.signName" placeholder="如：AI法律咨询" />
        </el-form-item>
        <template v-if="form.provider !== 'mock'">
          <el-form-item label="AccessKey ID">
            <el-input v-model="form.accessKeyId" placeholder="云厂商访问密钥ID" />
          </el-form-item>
          <el-form-item label="AccessKey Secret">
            <el-input v-model="form.accessKeySecret" type="password" show-password placeholder="云厂商访问密钥Secret" />
          </el-form-item>
          <el-form-item v-if="form.provider === 'aliyun'" label="地域">
            <el-input v-model="form.regionId" placeholder="如 cn-hangzhou" />
          </el-form-item>
          <el-form-item v-if="form.provider === 'tencent'" label="SDK AppId">
            <el-input v-model="form.sdkAppId" placeholder="腾讯云短信应用AppId" />
          </el-form-item>
        </template>
        <el-row>
          <el-col :span="12">
            <el-form-item label="日发送上限">
              <el-input-number v-model="form.dailyLimit" :min="1" :max="1000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio label="1">启用</el-radio>
                <el-radio label="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
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
      title: "",
      open: false,
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
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {
        configId: null, configName: null, provider: "mock", accessKeyId: null, accessKeySecret: null,
        signName: null, regionId: null, sdkAppId: null, dailyLimit: 10, status: "1", remark: null
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
        this.open = true;
        this.title = "修改短信通道";
      });
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (!valid) return;
        if (this.form.configId != null) {
          updateSmsConfig(this.form).then(() => {
            this.$modal.msgSuccess("修改成功");
            this.open = false;
            this.getList();
          });
        } else {
          addSmsConfig(this.form).then(() => {
            this.$modal.msgSuccess("新增成功");
            this.open = false;
            this.getList();
          });
        }
      });
    },
    handleDelete(row) {
      const configIds = row.configId ? [row.configId] : this.ids;
      this.$modal.confirm('是否确认删除选中的短信通道？').then(() => {
        return delSmsConfig(configIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    handleStatusChange(row) {
      const text = row.status === "1" ? "启用" : "停用";
      this.$modal.confirm(`确认${text}"${row.configName}"吗？`).then(() => {
        return updateSmsConfig({ configId: row.configId, status: row.status });
      }).then(() => {
        this.$modal.msgSuccess(text + "成功");
      }).catch(() => {
        row.status = row.status === "1" ? "0" : "1";
      });
    }
  }
};
</script>
