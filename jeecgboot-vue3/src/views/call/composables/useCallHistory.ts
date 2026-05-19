import { ref, computed, readonly } from 'vue'
import { getCallList, getCallDetail, getCallTurns, getAudioUrl, updateCallRemark } from '/@/api/call'
import type { CallRecord, CallDetail, CallListItem, CallTurnItem } from '../types'

const PAGE_SIZE   = 20
const PREVIEW_CNT = 2

export function useCallHistory() {
  const allRecords    = ref<CallRecord[]>([])
  const isLoading     = ref(false)
  const isLoadingMore = ref(false)
  const totalCount    = ref(0)
  const currentPage   = ref(1)
  const hasMore       = computed(() => allRecords.value.length < totalCount.value)
  const expanded      = ref(false)
  const selectedId    = ref('')
  const error         = ref<string | null>(null)

  const visibleRecords = computed<CallRecord[]>(() =>
    expanded.value ? allRecords.value : allRecords.value.slice(0, PREVIEW_CNT)
  )

  function _clearError(): void { error.value = null }

  function _mapItem(item: CallListItem, existingNotes: Map<string, string>): CallRecord {
    return {
      call_session_id: item.call_session_id,
      phone:           item.phone,
      customer_name:   item.customer_name,
      direction:       item.direction,
      status:          item.status,
      date:            item.started_at?.slice(0, 10) ?? '',
      time:            item.started_at?.slice(11, 16) ?? '',
      note:            existingNotes.get(item.call_session_id) ?? item.summary_short ?? '',
      duration_sec:    Math.floor((item.duration_ms ?? 0) / 1000),
      active:          selectedId.value === item.call_session_id,
    }
  }

  async function fetchList(): Promise<void> {
    if (isLoading.value || isLoadingMore.value) return
    isLoading.value = true
    _clearError()
    try {
      const res = await getCallList(1, PAGE_SIZE) as any
      if (res.code === 200 && Array.isArray(res.result?.items)) {
        const existingNotes = new Map(allRecords.value.map((r: CallRecord) => [r.call_session_id, r.note]))
        allRecords.value  = (res.result.items as CallListItem[]).map(item => _mapItem(item, existingNotes))
        totalCount.value  = res.result.total ?? allRecords.value.length
        currentPage.value = 1
      } else {
        error.value = '获取通话列表失败'
      }
    } catch (err: any) {
      console.error('[useCallHistory] 拉取列表失败:', err.message)
      error.value = err.message || '网络请求失败'
    } finally {
      isLoading.value = false
    }
  }

  async function fetchMore(): Promise<void> {
    if (isLoadingMore.value || isLoading.value || !hasMore.value) return
    isLoadingMore.value = true
    _clearError()
    try {
      const nextPage = currentPage.value + 1
      const res = await getCallList(nextPage, PAGE_SIZE) as any
      if (res.code === 200 && Array.isArray(res.result?.items)) {
        const existingIds   = new Set(allRecords.value.map((r: CallRecord) => r.call_session_id))
        const existingNotes = new Map(allRecords.value.map((r: CallRecord) => [r.call_session_id, r.note]))
        const newItems = (res.result.items as CallListItem[])
          .filter(item => !existingIds.has(item.call_session_id))
          .map(item => _mapItem(item, existingNotes))
        allRecords.value  = [...allRecords.value, ...newItems]
        totalCount.value  = res.result.total ?? totalCount.value
        currentPage.value = nextPage
      }
    } catch (err: any) {
      console.error('[useCallHistory] 追加列表失败:', err.message)
      error.value = err.message || '加载更多失败'
    } finally {
      isLoadingMore.value = false
    }
  }

  function selectOnly(id: string): void {
    selectedId.value = id
    allRecords.value = allRecords.value.map((r: CallRecord) => ({ ...r, active: r.call_session_id === id }))
  }

  async function fetchTurns(id: string): Promise<CallTurnItem[]> {
    try {
      const res = await getCallTurns(id) as any
      if (res.code === 200 && Array.isArray(res.result?.items)) return res.result.items as CallTurnItem[]
      return []
    } catch (err: any) {
      console.error('[useCallHistory] 拉取 turns 失败:', err.message)
      return []
    }
  }

  async function fetchTurnsWithAudio(id: string): Promise<CallTurnItem[]> {
    const [turnsRes, audioRes] = await Promise.allSettled([
      getCallTurns(id),
      getAudioUrl(id),
    ])

    let turns: CallTurnItem[] = []
    if (turnsRes.status === 'fulfilled') {
      const res = turnsRes.value as any
      if (res.code === 200 && Array.isArray(res.result?.items)) turns = res.result.items as CallTurnItem[]
    }

    if (audioRes.status === 'fulfilled') {
      const res = audioRes.value as any
      if (res.code === 200 && Array.isArray(res.result?.items)) {
        const urlMap = new Map<string, string>()
        for (const item of res.result.items) {
          if (item.turn_id && item.url) urlMap.set(item.turn_id, item.url)
        }
        if (urlMap.size > 0) {
          turns = turns.map(t => urlMap.has(t.turn_id) ? { ...t, audio_url: urlMap.get(t.turn_id)! } : t)
        }
      }
    }

    return turns
  }

  async function fetchDetail(id: string): Promise<CallDetail | null> {
    try {
      const res = await getCallDetail(id) as any
      if (res.code === 200 && res.result) return res.result as CallDetail
      return null
    } catch (err: any) {
      console.error('[useCallHistory] 拉取详情失败:', err.message)
      return null
    }
  }

  async function updateRecordNote(id: string, note: string): Promise<void> {
    const oldNote = allRecords.value.find((r: CallRecord) => r.call_session_id === id)?.note ?? ''
    allRecords.value = allRecords.value.map((r: CallRecord) => r.call_session_id === id ? { ...r, note } : r)
    try {
      await updateCallRemark(id, note)
    } catch (err: any) {
      allRecords.value = allRecords.value.map((r: CallRecord) => r.call_session_id === id ? { ...r, note: oldNote } : r)
      error.value = '备注保存失败'
      console.warn('[useCallHistory] 备注持久化失败:', err.message)
    }
  }

  function refresh(): void { fetchList() }

  return {
    allRecords:       readonly(allRecords),
    visibleRecords,
    totalCount:       readonly(totalCount),
    isLoading:        readonly(isLoading),
    isLoadingMore:    readonly(isLoadingMore),
    hasMore,
    expanded,
    selectedId:       readonly(selectedId),
    error:            readonly(error),
    fetchList,
    fetchMore,
    fetchTurns,
    fetchTurnsWithAudio,
    fetchDetail,
    selectOnly,
    updateRecordNote,
    refresh,
  }
}
