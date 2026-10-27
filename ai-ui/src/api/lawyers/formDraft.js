import request from '@/utils/request'

/**
 * U4 表单防丢失：服务端草稿（按登录用户隔离）。
 * 自动保存节流由前端 mixin 控制，此接口只负责写入/读取/删除。
 */
export function getFormDraft(formKey) {
  return request({
    url: '/lawyers/formDraft',
    method: 'get',
    params: { formKey }
  })
}

export function saveFormDraft(formKey, content) {
  return request({
    url: '/lawyers/formDraft',
    method: 'post',
    data: { formKey, content }
  })
}

export function deleteFormDraft(formKey) {
  return request({
    url: '/lawyers/formDraft',
    method: 'delete',
    params: { formKey }
  })
}
