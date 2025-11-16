<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="提示词名称" prop="promptName">
        <el-input
          v-model="queryParams.promptName"
          placeholder="请输入提示词名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="提示词类型" prop="promptType">
        <el-select v-model="queryParams.promptType" placeholder="请选择提示词类型" clearable>
          <el-option
            v-for="dict in dict.type.ai_prompt_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option
            v-for="dict in dict.type.sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:systemPrompt:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['lawyers:systemPrompt:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['lawyers:systemPrompt:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="systemPromptList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="提示词ID" align="center" prop="promptId" />
      <el-table-column label="提示词名称" align="center" prop="promptName" />
      <el-table-column label="提示词类型" align="center" prop="promptType">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.ai_prompt_type" :value="scope.row.promptType"/>
        </template>
      </el-table-column>
      <el-table-column label="适用场景" align="center" prop="sceneType" />
      <el-table-column label="内容" align="center" prop="content" show-overflow-tooltip>
        <template slot-scope="scope">
          <span>{{ scope.row.content.substring(0, 50) }}{{ scope.row.content.length > 50 ? '...' : '' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="默认配置" align="center" prop="isDefault">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_yes_no" :value="scope.row.isDefault"/>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_normal_disable" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:systemPrompt:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:systemPrompt:remove']"
          >删除</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handlePreview(scope.row)"
            v-hasPermi="['lawyers:systemPrompt:query']"
          >预览</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-star-on"
            @click="handleSetDefault(scope.row)"
            v-if="scope.row.isDefault === 'N'"
            v-hasPermi="['lawyers:systemPrompt:edit']"
          >设为默认</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改系统提示词配置对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="800px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="提示词名称" prop="promptName">
          <el-input v-model="form.promptName" placeholder="请输入提示词名称" />
        </el-form-item>
        <el-form-item label="提示词类型" prop="promptType">
          <el-select v-model="form.promptType" placeholder="请选择提示词类型">
            <el-option
              v-for="dict in dict.type.ai_prompt_type"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="适用场景" prop="sceneType">
          <el-input v-model="form.sceneType" placeholder="请输入适用场景" />
        </el-form-item>
        <el-form-item label="提示词内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="请输入提示词内容" />
        </el-form-item>
        <el-form-item label="参数配置" prop="params">
          <el-input v-model="form.params" type="textarea" :rows="3" placeholder="请输入JSON格式的参数配置，例如：{&quot;question&quot;: &quot;用户问题&quot;, &quot;category&quot;: &quot;分类&quot;}" />
        </el-form-item>
        <el-form-item label="默认配置" prop="isDefault">
          <el-radio-group v-model="form.isDefault">
            <el-radio
              v-for="dict in dict.type.sys_yes_no"
              :key="dict.value"
              :label="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in dict.type.sys_normal_disable"
              :key="dict.value"
              :label="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog title="提示词预览" :visible.sync="previewOpen" width="800px" append-to-body>
      <el-form ref="previewForm" :model="previewForm" label-width="100px">
        <el-form-item label="提示词内容">
          <el-input v-model="previewContent" type="textarea" :rows="8" readonly />
        </el-form-item>
        <el-divider content-position="left">参数测试</el-divider>
        <div v-if="previewParams">
          <el-form-item v-for="(value, key) in previewParams" :key="key" :label="value">
            <el-input v-model="testParams[key]" :placeholder="'请输入' + value" />
          </el-form-item>
        </div>
        <el-form-item>
          <el-button type="primary" @click="handlePreviewResult">生成预览</el-button>
        </el-form-item>
        <el-form-item label="预览结果" v-if="previewResult">
          <el-input v-model="previewResult" type="textarea" :rows="8" readonly />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="previewOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSystemPrompt, getSystemPrompt, delSystemPrompt, addSystemPrompt, updateSystemPrompt, setDefaultSystemPrompt, previewSystemPrompt } from "@/api/lawyers/systemPrompt";

export default {
  name: "SystemPrompt",
  dicts: ['ai_prompt_type', 'sys_normal_disable', 'sys_yes_no'],
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
      // 系统提示词配置表格数据
      systemPromptList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示预览弹出层
      previewOpen: false,
      // 预览内容
      previewContent: "",
      // 预览参数
      previewParams: null,
      // 测试参数
      testParams: {},
      // 预览结果
      previewResult: "",
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        promptName: null,
        promptType: null,
        status: null
      },
      // 表单参数
      form: {},
      // 预览表单参数
      previewForm: {},
      // 表单校验
      rules: {
        promptName: [
          { required: true, message: "提示词名称不能为空", trigger: "blur" }
        ],
        promptType: [
          { required: true, message: "提示词类型不能为空", trigger: "change" }
        ],
        sceneType: [
          { required: true, message: "适用场景不能为空", trigger: "blur" }
        ],
        content: [
          { required: true, message: "提示词内容不能为空", trigger: "blur" }
        ],
        status: [
          { required: true, message: "状态不能为空", trigger: "change" }
        ]
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询系统提示词配置列表 */
    getList() {
      this.loading = true;
      listSystemPrompt(this.queryParams).then(response => {
        this.systemPromptList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        promptId: null,
        promptName: null,
        promptType: null,
        sceneType: null,
        content: null,
        params: null,
        isDefault: "N",
        status: "0",
        remark: null
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.promptId)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加系统提示词配置";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const promptId = row.promptId || this.ids
      getSystemPrompt(promptId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改系统提示词配置";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.promptId != null) {
            updateSystemPrompt(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addSystemPrompt(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const promptIds = row.promptId || this.ids;
      this.$modal.confirm('是否确认删除系统提示词配置编号为"' + promptIds + '"的数据项？').then(function() {
        return delSystemPrompt(promptIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 设为默认按钮操作 */
    handleSetDefault(row) {
      this.$modal.confirm('确认将"' + row.promptName + '"设为默认提示词？').then(function() {
        return setDefaultSystemPrompt(row.promptId);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("设置成功");
      }).catch(() => {});
    },
    /** 预览按钮操作 */
    handlePreview(row) {
      this.previewContent = row.content;
      try {
        this.previewParams = JSON.parse(row.params || '{}');
        this.testParams = {};
        // 初始化测试参数为空
        Object.keys(this.previewParams).forEach(key => {
          this.testParams[key] = '';
        });
      } catch (e) {
        this.previewParams = null;
        this.$modal.msgError("参数配置格式不正确，应为JSON格式");
      }
      this.previewResult = "";
      this.previewOpen = true;
    },
    /** 生成预览结果 */
    handlePreviewResult() {
      if (!this.previewParams) {
        this.previewResult = this.previewContent;
        return;
      }
      
      // 检查是否所有参数都已填写
      let allParamsFilled = true;
      Object.keys(this.previewParams).forEach(key => {
        if (!this.testParams[key]) {
          allParamsFilled = false;
        }
      });
      
      if (!allParamsFilled) {
        this.$modal.msgError("请填写所有参数");
        return;
      }
      
      // 调用后端API生成预览
      previewSystemPrompt(this.currentPreviewPromptId, this.testParams).then(response => {
        this.previewResult = response.data;
      }).catch(() => {
        // 如果API调用失败，则在前端简单替换
        let result = this.previewContent;
        Object.keys(this.testParams).forEach(key => {
          const placeholder = `{${key}}`;
          result = result.replace(new RegExp(placeholder, 'g'), this.testParams[key]);
        });
        this.previewResult = result;
      });
    }
  }
};
</script>