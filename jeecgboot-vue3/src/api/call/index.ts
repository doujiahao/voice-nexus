import { defHttp } from '/@/utils/http/axios'
import type { AgentStatusEnum, AgentStatusResult, CallListResult, CallDetailResult, CallTurnResult } from './model/callModel'

const _base = (import.meta.env.VITE_CALL_API_BASE ?? '/call') as string

const Api = {
  AgentStatus : `${_base}/api/v1/agent/status`,
  CallList    : `${_base}/api/v1/calls`,
  CallDetail  : `${_base}/api/v1/calls`,
  CallTurns   : `${_base}/api/v1/calls`,
  CallRemark  : `${_base}/api/v1/calls`,
  AudioUrl    : `${_base}/api/v1/calls`,
} as const

const httpOpts = { isTransformResponse: false, joinPrefix: false, apiUrl: '' }

/** 获取坐席当前状态 */
export function getAgentStatus() {
  return defHttp.get<AgentStatusResult>({ url: Api.AgentStatus }, httpOpts)
}

/** 更新坐席状态 */
export function updateAgentStatus(status: AgentStatusEnum, reason?: string) {
  return defHttp.post({ url: Api.AgentStatus, data: { status, reason } }, httpOpts)
}

/** 获取通话列表 */
export function getCallList(page: number, pageSize: number) {
  return defHttp.get<CallListResult>({ url: Api.CallList, params: { page, page_size: pageSize } }, httpOpts)
}

/** 获取通话详情 */
export function getCallDetail(callSessionId: string) {
  return defHttp.get<CallDetailResult>({ url: `${Api.CallDetail}/${encodeURIComponent(callSessionId)}` }, httpOpts)
}

/** 获取通话转写轮次 */
export function getCallTurns(callSessionId: string) {
  return defHttp.get<CallTurnResult>({ url: `${Api.CallTurns}/${encodeURIComponent(callSessionId)}/turns` }, httpOpts)
}

/** 更新通话备注 */
export function updateCallRemark(callSessionId: string, remark: string) {
  return defHttp.put({ url: `${Api.CallRemark}/${encodeURIComponent(callSessionId)}/remark`, data: { remark } }, httpOpts)
}

/** 获取通话录音预签名URL */
export function getAudioUrl(callSessionId: string) {
  return defHttp.get({ url: `${Api.AudioUrl}/${encodeURIComponent(callSessionId)}/audio-url` }, httpOpts)
}
