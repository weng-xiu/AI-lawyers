<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="88px">
      <el-form-item label="智能体名称" prop="agentName">
        <el-input
          v-model="queryParams.agentName"
          placeholder="请输入智能体名称"
          clearable
          style="width: 220px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="平台" prop="provider">
        <el-select v-model="queryParams.provider" placeholder="请选择平台" clearable style="width: 160px">
          <el-option label="本地知识库" value="local" />
          <el-option label="MaxKB" value="maxkb" />
          <el-option label="Dify" value="dify" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
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
          v-hasPermi="['lawyers:agent:config:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['lawyers:agent:config:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:agent:config:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:agent:config:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="agentList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" prop="agentId" width="70" />
      <el-table-column label="智能体名称" prop="agentName" :show-overflow-tooltip="true" />
      <el-table-column label="平台" prop="provider" width="120">
        <template slot-scope="scope">
          <el-tag :type="providerTag(scope.row.provider)" size="small">{{ providerLabel(scope.row.provider) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="多轮上下文" prop="enableContext" width="100" align="center">
        <template slot-scope="scope">{{ scope.row.enableContext === '1' ? '开启' : '关闭' }}</template>
      </el-table-column>
      <el-table-column label="上下文轮数" prop="contextRounds" width="90" align="center" />
      <el-table-column label="欢迎语" prop="welcome" :show-overflow-tooltip="true" />
      <el-table-column label="状态" prop="status" width="80" align="center">
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.status"
            active-value="1"
            inactive-value="0"
            @change="handleStatusChange(scope.row)"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-connection"
            @click="handleTest(scope.row)" v-hasPermi="['lawyers:agent:config:test']">测试</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit"
            @click="handleUpdate(scope.row)" v-hasPermi="['lawyers:agent:config:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete"
            @click="handleDelete(scope.row)" v-hasPermi="['lawyers:agent:config:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="680px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="智能体名称" prop="agentName">
              <el-input v-model="form.agentName" placeholder="请输入智能体名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="平台" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择平台" style="width: 100%">
                <el-option label="本地知识库（推荐）" value="local" />
                <el-option label="MaxKB" value="maxkb" />
                <el-option label="Dify" value="dify" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="form.provider !== 'local'">
          <el-col :span="16">
            <el-form-item label="接口地址">
              <el-input v-model="form.apiUrl" placeholder="外部平台对话接口地址" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="应用ID">
              <el-input v-model="form.appId" placeholder="app_id" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="API Key" v-if="form.provider !== 'local'">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="外部平台认证密钥" />
        </el-form-item>
        <el-form-item label="角色提示词" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="4"
            placeholder="设定智能体角色与回答规则，例如：你是12348公共法律服务热线的智能法律助手..." />
        </el-form-item>
        <el-form-item label="欢迎语">
          <el-input v-model="form.welcome" placeholder="首轮无用户输入时播报的欢迎语" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="知识库ID">
              <el-input v-model="form.knowledgeIds" placeholder="限定知识库ID，逗号分隔，留空全库检索" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型ID">
              <el-input v-model="form.modelId" placeholder="关联大模型ID，留空用默认" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="多轮上下文">
              <el-switch v-model="form.enableContext" active-value="1" inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上下文轮数">
              <el-input-number v-model="form.contextRounds" :min="1" :max="20" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="转人工关键词">
          <el-input v-model="form.handoffKeywords" placeholder="命中即转人工，逗号分隔，如：人工,律师,投诉" />
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
import { listAgentConfig, getAgentConfig, addAgentConfig, updateAgentConfig, delAgentConfig, testAgentConfig } from "@/api/lawyers/agent";

export default {
  name: "AgentConfig",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      agentList: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        agentName: undefined,
        provider: undefined,
        status: undefined
      },
      form: {},
      rules: {
        agentName: [{ required: true, message: "智能体名称不能为空", trigger: "blur" }],
        provider: [{ required: true, message: "请选择平台", trigger: "change" }]
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      this.loading = true;
      listAgentConfig(this.queryParams).then(response => {
        this.agentList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    providerLabel(p) {
      return { local: "本地知识库", maxkb: "MaxKB", dify: "Dify" }[p] || p;
    },
    providerTag(p) {
      return { local: "success", maxkb: "warning", dify: "info" }[p] || "";
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {
        agentId: null, agentName: null, provider: "local", apiUrl: null, apiKey: null, appId: null,
        systemPrompt: null, modelId: null, knowledgeIds: null, enableContext: "1", contextRounds: 5,
        handoffKeywords: "人工,律师,转人工,投诉,信访", welcome: null, status: "1", remark: null
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
      this.ids = selection.map(item => item.agentId);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    handleAdd() {
      this.reset();
      this.title = "新增AI智能体";
      this.open = true;
    },
    handleUpdate(row) {
      this.reset();
      const agentId = row.agentId || this.ids[0];
      getAgentConfig(agentId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改AI智能体";
      });
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (!valid) return;
        if (this.form.agentId != null) {
          updateAgentConfig(this.form).then(() => {
            this.$modal.msgSuccess("修改成功");
            this.open = false;
            this.getList();
          });
        } else {
          addAgentConfig(this.form).then(() => {
            this.$modal.msgSuccess("新增成功");
            this.open = false;
            this.getList();
          });
        }
      });
    },
    handleDelete(row) {
      const agentIds = row.agentId ? [row.agentId] : this.ids;
      this.$modal.confirm('是否确认删除选中的智能体？').then(() => {
        return delAgentConfig(agentIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    handleExport() {
      this.download('lawyers/agent/config/export', { ...this.queryParams }, `agent_config_${new Date().getTime()}.xlsx`);
    },
    handleStatusChange(row) {
      const text = row.status === "1" ? "启用" : "停用";
      this.$modal.confirm(`确认${text}"${row.agentName}"吗？`).then(() => {
        return updateAgentConfig({ agentId: row.agentId, status: row.status });
      }).then(() => {
        this.$modal.msgSuccess(text + "成功");
      }).catch(() => {
        row.status = row.status === "1" ? "0" : "1";
      });
    },
    handleTest(row) {
      const agentId = row.agentId;
      this.$modal.loading("正在测试连接...");
      testAgentConfig(agentId).then(() => {
        this.$modal.msgSuccess("连接正常");
      }).catch(() => {}).finally(() => {
        this.$modal.closeLoading();
      });
    }
  }
};
</script>
