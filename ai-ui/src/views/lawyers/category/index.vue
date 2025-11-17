<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="分类名称" prop="categoryName">
        <el-input
          v-model="queryParams.categoryName"
          placeholder="请输入分类名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
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
          v-hasPermi="['lawyers:category:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-sort"
          size="mini"
          @click="toggleExpandAll"
        >展开/折叠</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:category:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table
      v-if="refreshTable"
      v-loading="loading"
      :data="categoryList"
      row-key="categoryId"
      :default-expand-all="isExpandAll"
      :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
      ref="categoryTable"
    >
      <el-table-column label="分类名称" prop="categoryName" :show-overflow-tooltip="true"></el-table-column>
      <el-table-column label="排序" align="center" prop="orderNum" width="100"></el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_normal_disable" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:category:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-plus"
            @click="handleAdd(scope.row)"
            v-hasPermi="['lawyers:category:add']"
          >新增</el-button>
          <el-button
            v-if="scope.row.parentId != 0"
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:category:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改咨询分类对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="上级分类" prop="parentId">
          <el-select
            v-model="form.parentId"
            placeholder="选择上级分类"
            clearable
          >
            <el-option
              v-for="item in categoryOptions"
              :key="item.categoryId"
              :label="item.categoryName"
              :value="item.categoryId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="显示排序" prop="orderNum">
          <el-input-number v-model="form.orderNum" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="负责人" prop="leader">
          <el-input v-model="form.leader" placeholder="请输入负责人" maxlength="20" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="11" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="50" />
        </el-form-item>
        <el-form-item label="分类状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in dict.type.sys_normal_disable"
              :key="dict.value"
              :label="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
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
import { listConsultationCategory, getConsultationCategory, delConsultationCategory, addConsultationCategory, updateConsultationCategory, listConsultationCategoryTree, exportConsultationCategory } from "@/api/lawyers/consultationCategory"

export default {
  name: "ConsultationCategory",
  dicts: ['sys_normal_disable'],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 咨询分类表格数据
      categoryList: [],
      // 咨询分类树选项
      categoryOptions: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否展开，默认全部展开
      isExpandAll: true,
      // 重新渲染表格状态
      refreshTable: true,
      // 查询参数
      queryParams: {
        categoryName: undefined,
        status: undefined
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        parentId: [
          { required: true, message: "上级分类不能为空", trigger: "blur" }
        ],
        categoryName: [
          { required: true, message: "分类名称不能为空", trigger: "blur" }
        ],
        orderNum: [
          { required: true, message: "显示排序不能为空", trigger: "blur" }
        ],
        email: [
          {
            type: "email",
            message: "请输入正确的邮箱地址",
            trigger: ["blur", "change"]
          }
        ],
        phone: [
          {
            pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/,
            message: "请输入正确的手机号码",
            trigger: "blur"
          }
        ]
      }
    }
  },
  created() {
    this.getList()
    this.getTreeselect()
  },
  methods: {
    /** 查询咨询分类列表 */
    getList() {
      this.loading = true
      console.log('开始获取咨询分类列表，查询参数:', this.queryParams)
      
      // 优先使用树形结构API获取数据
      listConsultationCategoryTree(this.queryParams).then(response => {
        console.log('获取咨询分类树形结构响应:', response)
        
        if (response.data && response.data.length > 0) {
          console.log('使用树形API返回的数据，数据长度:', response.data.length)
          this.categoryList = response.data
        } else {
          console.log('树形API返回空数据，尝试使用列表API')
          // 如果树形API返回空数据，回退到列表API
          listConsultationCategory(this.queryParams).then(response => {
            console.log('获取咨询分类列表响应:', response)
            // 处理分页数据格式
            let dataList = []
            if (response.rows) {
              // 如果返回的是分页数据格式 {rows: [], total: number}
              dataList = response.rows
              console.log('使用分页数据格式，数据长度:', dataList.length)
            } else if (response.data) {
              // 如果返回的是直接数据格式 {data: []}
              dataList = response.data
              console.log('使用直接数据格式，数据长度:', dataList.length)
            }
            
            // 转换为树形结构
            if (dataList && dataList.length > 0) {
              console.log('将列表数据转换为树形结构')
              this.categoryList = this.handleTree(dataList, "categoryId", "parentId")
            } else {
              console.log('列表数据为空')
              this.categoryList = []
            }
          })
        }
        
        this.loading = false
      }).catch(error => {
        console.error('获取咨询分类树形结构失败，尝试使用列表API:', error)
        // 如果树形API失败，回退到列表API
        listConsultationCategory(this.queryParams).then(response => {
          console.log('获取咨询分类列表响应:', response)
          // 处理分页数据格式
          let dataList = []
          if (response.rows) {
            // 如果返回的是分页数据格式 {rows: [], total: number}
            dataList = response.rows
            console.log('使用分页数据格式，数据长度:', dataList.length)
          } else if (response.data) {
            // 如果返回的是直接数据格式 {data: []}
            dataList = response.data
            console.log('使用直接数据格式，数据长度:', dataList.length)
          }
          
          // 转换为树形结构
          if (dataList && dataList.length > 0) {
            console.log('将列表数据转换为树形结构')
            this.categoryList = this.handleTree(dataList, "categoryId", "parentId")
          } else {
            console.log('列表数据为空')
            this.categoryList = []
          }
          this.loading = false
        }).catch(error => {
          console.error('获取咨询分类列表失败:', error)
          this.categoryList = []
          this.loading = false
        })
      })
    },
    /** 转换咨询分类数据结构 */
    normalizer(node) {
      if (node.children && !node.children.length) {
        delete node.children
      }
      return {
        id: node.categoryId,
        label: node.categoryName,
        children: node.children
      }
    },
    /** 查询咨询分类下拉树结构 */
    getTreeselect() {
      console.log('开始获取咨询分类树形结构')
      listConsultationCategoryTree().then(response => {
        console.log('获取咨询分类树形结构响应:', response)
        this.categoryOptions = []
        const category = { categoryId: 0, categoryName: '主类目' }
        this.categoryOptions.push(category)
        
        // 使用树形结构API返回的数据，不需要再进行handleTree处理
        if (response.data && response.data.length > 0) {
          console.log('树形数据长度:', response.data.length)
          // 递归处理树形数据，转换为下拉选项需要的格式
          const processTreeData = (treeData, level = 0) => {
            treeData.forEach(node => {
              // 添加缩进以显示层级关系
              const indent = level > 0 ? '　'.repeat(level) + '├─ ' : ''
              this.categoryOptions.push({
                categoryId: node.categoryId,
                categoryName: indent + node.categoryName
              })
              
              // 递归处理子节点
              if (node.children && node.children.length > 0) {
                processTreeData(node.children, level + 1)
              }
            })
          }
          
          processTreeData(response.data)
          console.log('处理后的分类选项:', this.categoryOptions)
        } else {
          console.log('树形数据为空')
        }
      }).catch(error => {
        console.error('获取咨询分类树形结构失败:', error)
        // 如果树形API失败，回退到使用列表API
        console.log('回退到使用列表API获取树形结构')
        listConsultationCategory().then(response => {
          console.log('使用列表API获取的响应:', response)
          this.categoryOptions = []
          const category = { categoryId: 0, categoryName: '主类目' }
          this.categoryOptions.push(category)
          
          if (response.data && response.data.length > 0) {
            const treeData = this.handleTree(response.data, "categoryId", "parentId")
            console.log('转换后的树形数据:', treeData)
            const processTreeData = (treeData, level = 0) => {
              treeData.forEach(node => {
                const indent = level > 0 ? '　'.repeat(level) + '├─ ' : ''
                this.categoryOptions.push({
                  categoryId: node.categoryId,
                  categoryName: indent + node.categoryName
                })
                
                if (node.children && node.children.length > 0) {
                  processTreeData(node.children, level + 1)
                }
              })
            }
            
            processTreeData(treeData)
            console.log('处理后的分类选项:', this.categoryOptions)
          }
        })
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        categoryId: null,
        parentId: null,
        categoryName: null,
        orderNum: null,
        leader: null,
        phone: null,
        email: null,
        status: "0"
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 新增按钮操作 */
    handleAdd(row) {
      this.reset()
      this.getTreeselect()
      if (row != null && row.categoryId) {
        this.form.parentId = row.categoryId
      } else {
        this.form.parentId = 0
      }
      this.open = true
      this.title = "添加咨询分类"
    },
    /** 展开/折叠操作 */
    toggleExpandAll() {
      this.refreshTable = false
      this.isExpandAll = !this.isExpandAll
      this.$nextTick(() => {
        this.refreshTable = true
        // 确保表格重新渲染后应用展开/折叠状态
        this.$nextTick(() => {
          if (this.$refs.categoryTable) {
            const rows = this.$refs.categoryTable.store.states.flattenTree
            if (rows && rows.length > 0) {
              rows.forEach(row => {
                if (row.row && row.row.children && row.row.children.length > 0) {
                  this.$refs.categoryTable.toggleRowExpansion(row.row, this.isExpandAll)
                }
              })
            }
          }
        })
      })
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      this.getTreeselect()
      if (row != null) {
        this.form.parentId = row.parentId
        getConsultationCategory(row.categoryId).then(response => {
          this.form = response.data
          this.open = true
          this.title = "修改咨询分类"
        })
      }
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.categoryId != null) {
            updateConsultationCategory(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addConsultationCategory(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      this.$modal.confirm('是否确认删除名称为"' + row.categoryName + '"的数据项？').then(function() {
        return delConsultationCategory(row.categoryId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('lawyers/category/export', {
        ...this.queryParams
      }, `category_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>