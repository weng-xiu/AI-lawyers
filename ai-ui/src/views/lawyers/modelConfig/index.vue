<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="modelConfigList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :stripe="false"
      has-selection
      label-width="68px"
      empty-text="暂无AI模型配置"
      empty-hint="新增模型配置后可将其中一条设为默认，并测试 API 连通性"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:modelConfig:add']">新增</el-button>
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['lawyers:modelConfig:edit']">修改</el-button>
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:modelConfig:remove']">删除</el-button>
      </template>
      <template #column-modelType="{ row }">
        <dict-tag :options="dict.type.ai_model_type" :value="row.modelType"/>
      </template>
      <template #column-inputPrice="{ row }">{{ row.inputPrice != null ? row.inputPrice : '—' }}</template>
      <template #column-outputPrice="{ row }">{{ row.outputPrice != null ? row.outputPrice : '—' }}</template>
      <template #column-isDefault="{ row }">
        <dict-tag :options="dict.type.sys_yes_no" :value="row.isDefault"/>
      </template>
      <template #column-status="{ row }">
        <dict-tag :options="dict.type.sys_normal_disable" :value="row.status"/>
      </template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)"
          v-hasPermi="['lawyers:modelConfig:edit']">修改</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)"
          v-hasPermi="['lawyers:modelConfig:remove']">删除</el-button>
        <el-button size="mini" type="text" icon="el-icon-connection" @click="handleTestConnection(row)"
          v-hasPermi="['lawyers:modelConfig:edit']">测试连接</el-button>
        <el-button size="mini" type="text" icon="el-icon-star-on" @click="handleSetDefault(row)"
          v-if="row.isDefault === 'N'" v-hasPermi="['lawyers:modelConfig:edit']">设为默认</el-button>
      </template>
    </ProTable>

    <!-- 添加或修改AI模型参数配置对话框（V3 ProDialog + ProForm 集成） -->
    <pro-dialog
      v-model="open"
      :title="title"
      width="500px"
      :confirm-loading="submitting"
      @confirm="handleSave"
      @closed="reset"
    >
      <!-- 字典 select/radio 走 item.dict（复用本页 dicts 已预热的 Vuex 缓存） -->
      <pro-form
        ref="form"
        :model="form"
        :fields="formFields"
        label-width="80px"
        size="medium"
      />
    </pro-dialog>
  </div>
</template>

<script>
import { listModelConfig, getModelConfig, delModelConfig, addModelConfig, updateModelConfig, setDefaultModelConfig, testModelConnection } from "@/api/lawyers/modelConfig";

export default {
  name: "ModelConfig",
  dicts: ['ai_model_type', 'sys_normal_disable', 'sys_yes_no'],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // AI模型参数配置表格数据
      modelConfigList: [],
      // V3：ProTable 列定义（类型/单价/默认/状态/操作走插槽）
      columns: [
        { prop: 'configId', label: '配置ID', align: 'center' },
        { prop: 'modelType', label: '模型类型', align: 'center', slot: true },
        { prop: 'modelName', label: '模型名称', align: 'center' },
        { prop: 'apiUrl', label: 'API地址', align: 'center', showOverflowTooltip: true },
        { prop: 'apiKey', label: 'API密钥', align: 'center', showOverflowTooltip: true },
        { prop: 'maxTokens', label: '最大Token数', align: 'center' },
        { prop: 'inputPrice', label: '输入单价(元/千Token)', width: 150, align: 'center', slot: true },
        { prop: 'outputPrice', label: '输出单价(元/千Token)', width: 150, align: 'center', slot: true },
        { prop: 'temperature', label: '温度', align: 'center' },
        { prop: 'isDefault', label: '默认配置', align: 'center', slot: true },
        { prop: 'status', label: '状态', align: 'center', slot: true },
        { prop: 'action', label: '操作', align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 提交 loading（ProDialog confirm 按钮）
      submitting: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        modelType: null,
        status: null
      },
      // 表单参数
      form: {},
      // V3 ProForm：新增/修改弹窗 schema（校验规则、字典、数字精度均随字段声明）
      formFields: [
        { prop: '__g1', label: '基本信息', type: 'divider' },
        { prop: 'modelType', label: '模型类型', type: 'select', dict: 'ai_model_type', required: true, message: '模型类型不能为空' },
        { prop: 'modelName', label: '模型名称', type: 'input', required: true, message: '模型名称不能为空' },
        { prop: 'apiUrl', label: 'API地址', type: 'input', required: true, message: 'API地址不能为空' },
        { prop: 'apiKey', label: 'API密钥', type: 'password', required: true, message: 'API密钥不能为空' },
        { prop: '__g2', label: '计费与参数', type: 'divider' },
        { prop: 'maxTokens', label: '最大Token数', type: 'number', min: 100, max: 8192, required: true, message: '最大Token数不能为空' },
        {
          prop: 'inputPrice', label: '输入单价', type: 'number', min: 0, step: 0.001, precision: 6,
          width: 200, tip: '元/千Token，留空不计费', labelTip: '按输入 Token 数计费，留空则不计收输入费用',
          attrs: { controls: false, valueOnClear: null, placeholder: '元/千Token，可空' }
        },
        {
          prop: 'outputPrice', label: '输出单价', type: 'number', min: 0, step: 0.001, precision: 6,
          width: 200, tip: '元/千Token，留空不计费', labelTip: '按输出 Token 数计费，留空则不计收输出费用',
          attrs: { controls: false, valueOnClear: null, placeholder: '元/千Token，可空' }
        },
        { prop: 'temperature', label: '温度', type: 'number', min: 0, max: 2, step: 0.1, precision: 1, required: true, message: '温度不能为空' },
        { prop: 'isDefault', label: '默认配置', type: 'radio', dict: 'sys_yes_no' },
        { prop: 'status', label: '状态', type: 'radio', dict: 'sys_normal_disable', required: true, message: '状态不能为空' },
        { prop: 'remark', label: '备注', type: 'textarea', rows: 2 }
      ]
    };
  },
  created() {
    this.getList();
  },
  computed: {
    queryFormItems() {
      const modelTypes = (this.dict.type.ai_model_type || []).map(d => ({ label: d.label, value: d.value }))
      const statusOptions = (this.dict.type.sys_normal_disable || []).map(d => ({ label: d.label, value: d.value }))
      return [
        { prop: 'modelType', label: '模型类型', type: 'select', placeholder: '请选择模型类型', width: 160, options: modelTypes },
        { prop: 'status', label: '状态', type: 'select', placeholder: '请选择状态', width: 120, options: statusOptions }
      ]
    }
  },
  methods: {
    /** 查询AI模型参数配置列表 */
    getList() {
      this.loading = true;
      listModelConfig(this.queryParams).then(response => {
        this.modelConfigList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    // 表单重置
    reset() {
      this.form = {
        configId: null,
        modelType: null,
        modelName: null,
        apiUrl: null,
        apiKey: null,
        maxTokens: 2048,
        inputPrice: null,
        outputPrice: null,
        temperature: 0.7,
        isDefault: "N",
        status: "0",
        remark: null
      };
      // 校验状态由 ProDialog closed 时自动 clearValidate
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, modelType: undefined, status: undefined }
      this.getList()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.configId)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加AI模型参数配置";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const configId = row.configId || this.ids
      getModelConfig(configId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改AI模型参数配置";
      });
    },
    /** 提交按钮（ProDialog 已自动校验通过） */
    handleSave() {
      this.submitting = true;
      const promise = this.form.configId != null
        ? updateModelConfig(this.form)
        : addModelConfig(this.form);
      promise.then(response => {
        this.$modal.msgSuccess(this.form.configId != null ? "修改成功" : "新增成功");
        this.open = false;
        this.getList();
      }).finally(() => {
        this.submitting = false;
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const configIds = row.configId || this.ids;
      const count = row.configId ? 1 : (this.ids || []).length;
      this.$modal.dangerConfirm({
        content: '将删除选中的 AI 模型参数配置',
        count: count,
        countNoun: '条配置'
      }).then(function() {
        return delModelConfig(configIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 设为默认按钮操作 */
    handleSetDefault(row) {
      this.$modal.dangerConfirm({
        actionType: 'publish',
        content: '将「' + row.modelName + '」设为默认模型配置',
        tip: '新建会话将立即使用该默认模型，进行中的会话不受影响'
      }).then(function() {
        return setDefaultModelConfig(row.configId);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("设置成功");
      }).catch(() => {});
    },
    /** 测试连接按钮操作 */
    handleTestConnection(row) {
      this.$modal.msgLoading("正在测试连接...");
      testModelConnection(row.configId).then(response => {
        this.$modal.msgSuccess("连接测试成功");
      }).catch(() => {
        this.$modal.msgError("连接测试失败");
      });
    }
  }
};
</script>