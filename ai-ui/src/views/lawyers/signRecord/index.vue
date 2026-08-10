<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="88px">
      <el-form-item label="业务流水号" prop="businessNo">
        <el-input v-model="queryParams.businessNo" placeholder="请输入业务流水号" clearable style="width: 200px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="签署方姓名" prop="signParty">
        <el-input v-model="queryParams.signParty" placeholder="请输入签署方姓名" clearable style="width: 200px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="签署方电话" prop="signPartyPhone">
        <el-input v-model="queryParams.signPartyPhone" placeholder="请输入签署方电话" clearable style="width: 200px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="签署状态" prop="signStatus">
        <el-select v-model="queryParams.signStatus" placeholder="请选择签署状态" clearable style="width: 160px">
          <el-option label="待签署" value="0" />
          <el-option label="签署中" value="1" />
          <el-option label="已完成" value="2" />
          <el-option label="已作废" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="签署时间">
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['lawyers:signRecord:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate" v-hasPermi="['lawyers:signRecord:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['lawyers:signRecord:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="['lawyers:signRecord:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="signRecordList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="业务流水号" align="center" prop="businessNo" width="160" show-overflow-tooltip />
      <el-table-column label="签署模板" align="center" prop="signTemplate" width="140" show-overflow-tooltip />
      <el-table-column label="签署方姓名" align="center" prop="signParty" width="110" />
      <el-table-column label="签署方电话" align="center" prop="signPartyPhone" width="130" />
      <el-table-column label="签署状态" align="center" prop="signStatus" width="100">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusTagType(scope.row.signStatus)">{{ statusText(scope.row.signStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签署时间" align="center" prop="signTime" width="160">
        <template slot-scope="scope">{{ scope.row.signTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="电子签章ID" align="center" prop="eSignId" width="160" show-overflow-tooltip />
      <el-table-column label="存证编号" align="center" prop="certificateNo" width="160" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)" v-hasPermi="['lawyers:signRecord:query']">详情</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['lawyers:signRecord:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['lawyers:signRecord:remove']">删除</el-button>
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

    <!-- 添加或修改签署记录对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="650px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="业务流水号" prop="businessNo">
              <el-input v-model="form.businessNo" placeholder="请输入业务流水号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签署模板" prop="signTemplate">
              <el-input v-model="form.signTemplate" placeholder="请输入签署模板" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="签署方姓名" prop="signParty">
              <el-input v-model="form.signParty" placeholder="请输入签署方姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签署方电话" prop="signPartyPhone">
              <el-input v-model="form.signPartyPhone" placeholder="请输入签署方电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="签署状态" prop="signStatus">
              <el-radio-group v-model="form.signStatus">
                <el-radio label="0">待签署</el-radio>
                <el-radio label="1">签署中</el-radio>
                <el-radio label="2">已完成</el-radio>
                <el-radio label="3">已作废</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签署时间" prop="signTime">
              <el-date-picker v-model="form.signTime" style="width: 100%" value-format="yyyy-MM-dd HH:mm:ss" type="datetime" placeholder="选择签署时间" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="电子签章ID" prop="eSignId">
              <el-input v-model="form.eSignId" placeholder="请输入电子签章ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存证编号" prop="certificateNo">
              <el-input v-model="form.certificateNo" placeholder="请输入存证编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="签署文件地址" prop="signFileUrl">
          <el-input v-model="form.signFileUrl" placeholder="请输入签署文件地址" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 签署记录详情对话框 -->
    <el-dialog title="签署记录详情" :visible.sync="detailOpen" width="650px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="业务流水号">{{ detailForm.businessNo }}</el-descriptions-item>
        <el-descriptions-item label="签署模板">{{ detailForm.signTemplate }}</el-descriptions-item>
        <el-descriptions-item label="签署方姓名">{{ detailForm.signParty }}</el-descriptions-item>
        <el-descriptions-item label="签署方电话">{{ detailForm.signPartyPhone }}</el-descriptions-item>
        <el-descriptions-item label="签署状态">
          <el-tag size="mini" :type="statusTagType(detailForm.signStatus)">{{ statusText(detailForm.signStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="签署时间">{{ detailForm.signTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电子签章ID">{{ detailForm.eSignId }}</el-descriptions-item>
        <el-descriptions-item label="存证编号">{{ detailForm.certificateNo }}</el-descriptions-item>
        <el-descriptions-item label="签署文件地址" :span="2">{{ detailForm.signFileUrl }}</el-descriptions-item>
        <el-descriptions-item label="创建者">{{ detailForm.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailForm.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新者">{{ detailForm.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailForm.updateTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSignRecord, getSignRecord, addSignRecord, updateSignRecord, delSignRecord } from "@/api/lawyers/signRecord";

export default {
  name: "SignRecord",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      signRecordList: [],
      title: "",
      open: false,
      detailOpen: false,
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        businessNo: undefined,
        signParty: undefined,
        signPartyPhone: undefined,
        signStatus: undefined
      },
      form: {},
      detailForm: {},
      rules: {
        businessNo: [
          { required: true, message: "业务流水号不能为空", trigger: "blur" }
        ],
        signTemplate: [
          { required: true, message: "签署模板不能为空", trigger: "blur" }
        ],
        signParty: [
          { required: true, message: "签署方姓名不能为空", trigger: "blur" }
        ],
        signPartyPhone: [
          { required: true, message: "签署方电话不能为空", trigger: "blur" },
          { pattern: /^1[3-9]\d{9}$/, message: "请输入正确的手机号码", trigger: "blur" }
        ],
        signStatus: [
          { required: true, message: "签署状态不能为空", trigger: "change" }
        ]
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      this.loading = true;
      listSignRecord(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.signRecordList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {
        signId: undefined,
        businessNo: undefined,
        signTemplate: undefined,
        signParty: undefined,
        signPartyPhone: undefined,
        signStatus: "0",
        signTime: undefined,
        eSignId: undefined,
        certificateNo: undefined,
        signFileUrl: undefined,
        remark: undefined
      };
      this.resetForm("form");
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.dateRange = [];
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.signId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加签署记录";
    },
    handleUpdate(row) {
      this.reset();
      const signId = row.signId || this.ids
      getSignRecord(signId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改签署记录";
      });
    },
    handleDetail(row) {
      getSignRecord(row.signId).then(response => {
        this.detailForm = response.data;
        this.detailOpen = true;
      });
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.signId != null) {
            updateSignRecord(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addSignRecord(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    handleDelete(row) {
      const signIds = row.signId || this.ids;
      this.$modal.confirm('是否确认删除签署记录编号为"' + signIds + '"的数据项？').then(function() {
        return delSignRecord(signIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    handleExport() {
      this.download('/lawyers/signRecord/export', {
        ...this.addDateRange(this.queryParams, this.dateRange)
      }, `签署记录_${new Date().getTime()}.xlsx`)
    },
    statusText(s) {
      const map = { '0': '待签署', '1': '签署中', '2': '已完成', '3': '已作废' }
      return map[s] || '-'
    },
    statusTagType(s) {
      const map = { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }
      return map[s] || 'info'
    }
  }
};
</script>
