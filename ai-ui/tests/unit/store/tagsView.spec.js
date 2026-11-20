'use strict'

/**
 * 标签页 store 单测：锁定访问/缓存/iframe 三类标签的增删与"关闭左侧/右侧/其他"行为，
 * 防止多页签场景下缓存组件被误删或残留。
 */
import tagsView from '@/store/modules/tagsView'

const { mutations, actions } = tagsView

function createState() {
  return { visitedViews: [], cachedViews: [], iframeViews: [] }
}

function view(path, name, meta = {}) {
  return { path, name, meta }
}

describe('tagsView 访问标签', () => {
  test('新增访问标签按 path 去重，缺省标题为 no-name', () => {
    const state = createState()
    mutations.ADD_VISITED_VIEW(state, view('/a', 'A'))
    mutations.ADD_VISITED_VIEW(state, view('/a', 'A'))
    mutations.ADD_VISITED_VIEW(state, view('/b', 'B', { title: '页面B' }))

    expect(state.visitedViews.map(item => item.path)).toEqual(['/a', '/b'])
    expect(state.visitedViews[0].title).toBe('no-name')
    expect(state.visitedViews[1].title).toBe('页面B')
  })

  test('新增 iframe 标签按 path 去重', () => {
    const state = createState()
    mutations.ADD_IFRAME_VIEW(state, view('/ext', 'Ext', { title: '外链' }))
    mutations.ADD_IFRAME_VIEW(state, view('/ext', 'Ext', { title: '外链' }))

    expect(state.iframeViews).toHaveLength(1)
    expect(state.iframeViews[0]).toMatchObject({ path: '/ext', title: '外链' })
  })

  test('删除访问标签时同步清理 iframe', () => {
    const state = createState()
    mutations.ADD_VISITED_VIEW(state, view('/a', 'A'))
    mutations.ADD_VISITED_VIEW(state, view('/b', 'B'))
    mutations.ADD_IFRAME_VIEW(state, view('/a', 'A'))

    mutations.DEL_VISITED_VIEW(state, view('/a', 'A'))

    expect(state.visitedViews.map(item => item.path)).toEqual(['/b'])
    expect(state.iframeViews).toHaveLength(0)
  })

  test('更新访问标签合并同 path 的属性', () => {
    const state = createState()
    mutations.ADD_VISITED_VIEW(state, view('/a', 'A', { title: '旧标题' }))

    mutations.UPDATE_VISITED_VIEW(state, view('/a', 'A', { title: '新标题' }))

    expect(state.visitedViews[0].meta.title).toBe('新标题')
  })
})

describe('tagsView 缓存标签', () => {
  test('noCache 页面不进入缓存，重复 name 去重', () => {
    const state = createState()
    mutations.ADD_CACHED_VIEW(state, view('/a', 'A'))
    mutations.ADD_CACHED_VIEW(state, view('/a', 'A'))
    mutations.ADD_CACHED_VIEW(state, view('/b', 'B', { noCache: true }))

    expect(state.cachedViews).toEqual(['A'])
  })

  test('删除缓存标签仅移除指定 name', () => {
    const state = createState()
    state.cachedViews.push('A', 'B')

    mutations.DEL_CACHED_VIEW(state, view('/a', 'A'))

    expect(state.cachedViews).toEqual(['B'])
  })

  test('关闭其他时仅保留当前标签的缓存', () => {
    const state = { visitedViews: [view('/a', 'A'), view('/b', 'B')], cachedViews: ['A', 'B'], iframeViews: [] }

    mutations.DEL_OTHERS_CACHED_VIEWS(state, view('/b', 'B'))
    expect(state.cachedViews).toEqual(['B'])

    state.cachedViews = []
    mutations.DEL_OTHERS_CACHED_VIEWS(state, view('/x', 'X'))
    expect(state.cachedViews).toEqual([])
  })
})

describe('tagsView 批量关闭', () => {
  test('关闭其他保留 affix 与当前标签', () => {
    const state = createState()
    mutations.ADD_VISITED_VIEW(state, view('/home', 'Home', { affix: true }))
    mutations.ADD_VISITED_VIEW(state, view('/a', 'A'))
    mutations.ADD_VISITED_VIEW(state, view('/b', 'B'))
    mutations.ADD_IFRAME_VIEW(state, view('/a', 'A'))

    mutations.DEL_OTHERS_VISITED_VIEWS(state, view('/b', 'B'))

    expect(state.visitedViews.map(item => item.path)).toEqual(['/home', '/b'])
    expect(state.iframeViews).toHaveLength(0)
  })

  test('关闭全部仅保留 affix 标签并清空 iframe', () => {
    const state = createState()
    mutations.ADD_VISITED_VIEW(state, view('/home', 'Home', { affix: true }))
    mutations.ADD_VISITED_VIEW(state, view('/a', 'A'))
    mutations.ADD_IFRAME_VIEW(state, view('/a', 'A'))
    state.cachedViews.push('Home', 'A')

    mutations.DEL_ALL_VISITED_VIEWS(state)
    state.cachedViews.push('Home')
    mutations.DEL_ALL_CACHED_VIEWS(state)

    expect(state.visitedViews.map(item => item.path)).toEqual(['/home'])
    expect(state.iframeViews).toHaveLength(0)
    expect(state.cachedViews).toEqual([])
  })

  test('关闭右侧移除右侧标签及其缓存和外链', () => {
    const state = createState()
    state.visitedViews = [view('/a', 'A'), view('/b', 'B'), view('/c', 'C', { link: '/ext' })]
    state.cachedViews = ['A', 'B', 'C']
    state.iframeViews = [view('/c', 'C')]

    mutations.DEL_RIGHT_VIEWS(state, view('/b', 'B'))

    expect(state.visitedViews.map(item => item.path)).toEqual(['/a', '/b'])
    expect(state.cachedViews).toEqual(['A', 'B'])
    expect(state.iframeViews).toHaveLength(0)
  })

  test('关闭左侧移除左侧标签，affix 除外', () => {
    const state = createState()
    state.visitedViews = [view('/home', 'Home', { affix: true }), view('/a', 'A'), view('/b', 'B')]
    state.cachedViews = ['A', 'B']
    state.iframeViews = []

    mutations.DEL_LEFT_VIEWS(state, view('/b', 'B'))

    expect(state.visitedViews.map(item => item.path)).toEqual(['/home', '/b'])
    expect(state.cachedViews).toEqual(['B'])
  })

  test('目标标签不存在时左右关闭不产生变化', () => {
    const state = createState()
    state.visitedViews = [view('/a', 'A')]

    mutations.DEL_RIGHT_VIEWS(state, view('/x', 'X'))
    mutations.DEL_LEFT_VIEWS(state, view('/x', 'X'))

    expect(state.visitedViews).toHaveLength(1)
  })
})

describe('tagsView actions', () => {
  test('addView 同时派发访问与缓存', () => {
    const dispatch = jest.fn()
    actions.addView({ dispatch }, view('/a', 'A'))

    expect(dispatch).toHaveBeenNthCalledWith(1, 'addVisitedView', view('/a', 'A'))
    expect(dispatch).toHaveBeenNthCalledWith(2, 'addCachedView', view('/a', 'A'))
  })

  test('删除类 action 返回最新数组快照', async () => {
    const state = { visitedViews: [view('/a', 'A')], cachedViews: ['A'], iframeViews: [] }
    const commit = jest.fn()

    await expect(actions.delVisitedView({ commit, state }, view('/a', 'A')))
      .resolves.toEqual([view('/a', 'A')])
    await expect(actions.delCachedView({ commit, state }, view('/a', 'A'))).resolves.toEqual(['A'])
    await expect(actions.delIframeView({ commit, state }, view('/a', 'A'))).resolves.toEqual([])
    expect(commit).toHaveBeenCalledWith('DEL_VISITED_VIEW', view('/a', 'A'))
    expect(commit).toHaveBeenCalledWith('DEL_CACHED_VIEW', view('/a', 'A'))
    expect(commit).toHaveBeenCalledWith('DEL_IFRAME_VIEW', view('/a', 'A'))
  })

  test('批量删除 action 派发子动作并返回快照', async () => {
    const dispatch = jest.fn()
    const state = { visitedViews: [view('/a', 'A')], cachedViews: ['A'], iframeViews: [] }

    await expect(actions.delView({ dispatch, state }, view('/a', 'A'))).resolves.toEqual({
      visitedViews: [view('/a', 'A')],
      cachedViews: ['A']
    })
    await expect(actions.delOthersViews({ dispatch, state }, view('/a', 'A'))).resolves.toEqual({
      visitedViews: [view('/a', 'A')],
      cachedViews: ['A']
    })
    await expect(actions.delAllViews({ dispatch, state }, view('/a', 'A'))).resolves.toEqual({
      visitedViews: [view('/a', 'A')],
      cachedViews: ['A']
    })
    expect(dispatch).toHaveBeenCalledWith('delVisitedView', view('/a', 'A'))
    expect(dispatch).toHaveBeenCalledWith('delOthersCachedViews', view('/a', 'A'))
    expect(dispatch).toHaveBeenCalledWith('delAllCachedViews', view('/a', 'A'))
  })

  test('updateVisitedView/delRightTags/delLeftTags 转发提交或返回快照', async () => {
    const commit = jest.fn()
    // 左右关闭 action 的返回快照直接读取模块级 state
    tagsView.state.visitedViews = [view('/a', 'A')]

    actions.updateVisitedView({ commit }, view('/a', 'A'))
    expect(commit).toHaveBeenCalledWith('UPDATE_VISITED_VIEW', view('/a', 'A'))

    await expect(actions.delRightTags({ commit }, view('/a', 'A'))).resolves.toEqual([view('/a', 'A')])
    await expect(actions.delLeftTags({ commit }, view('/a', 'A'))).resolves.toEqual([view('/a', 'A')])
    expect(commit).toHaveBeenCalledWith('DEL_RIGHT_VIEWS', view('/a', 'A'))
    expect(commit).toHaveBeenCalledWith('DEL_LEFT_VIEWS', view('/a', 'A'))
  })
})
