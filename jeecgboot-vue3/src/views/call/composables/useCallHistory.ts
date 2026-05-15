import { ref, computed, readonly } from 'vue'
import { getCallList, getCallDetail, getCallTurns, updateCallRemark } from '/@/api/call'
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

  const visibleRecords = computed<CallRecord[]>(() =>
    expanded.value ? allRecords.value : allRecords.value.slice(0, PREVIEW_CNT)
  )

  function _mapItem(item: CallListItem, existingNotes: Map<string, string>): CallRecord {
    return {
      call_session_id: item.call_session_id,
      phone:           item.phone,
      customer_name:   item.customer_name,
      date:            item.started_at.slice(0, 10),
      note:            existingNotes.get(item.call_session_id) ?? item.summary_short ?? '',
      duration_sec:    Math.floor((item.duration_ms ?? 0) / 1000),
      active:          selectedId.value === item.call_session_id,
    }
  }

  async function fetchList(): Promise<void> {
    if (isLoading.value) return
    isLoading.value = true
    try {
      const res = await getCallList(1, PAGE_SIZE) as any
      if (res.code === 200 && Array.isArray(res.result?.items)) {
        const existingNotes = new Map(allRecords.value.map((r: CallRecord) => [r.call_session_id, r.note]))
        allRecords.value  = (res.result.items as CallListItem[]).map(item => _mapItem(item, existingNotes))
        totalCount.value  = res.result.total ?? allRecords.value.length
        currentPage.value = 1
      }
    } catch (err: any) {
      console.error('[useCallHistory] 拉取列表失败:', err.message)
    } finally {
      isLoading.value = false
    }
  }

  async function fetchMore(): Promise<void> {
    if (isLoadingMore.value || isLoading.value || !hasMore.value) return
    isLoadingMore.value = true
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
    allRecords.value = allRecords.value.map((r: CallRecord) => r.call_session_id === id ? { ...r, note } : r)
    try {
      await updateCallRemark(id, note)
    } catch (err: any) {
      console.warn('[useCallHistory] 备注持久化失败（后端接口可能尚未就绪）:', err.message)
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
    fetchList,
    fetchMore,
    fetchTurns,
    fetchDetail,
    selectOnly,
    updateRecordNote,
    refresh,
  }
}
