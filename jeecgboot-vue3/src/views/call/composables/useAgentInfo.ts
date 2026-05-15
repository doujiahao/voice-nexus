import { ref, readonly } from 'vue'
import { getUserInfo } from '/@/api/sys/user'
import type { AgentInfo } from '../types'

const _info = ref<AgentInfo>({ name: '坐席', role: '客服坐席', id: '', avatarChar: '坐' })
let _initialized = false

export function useAgentInfo() {
  async function init(): Promise<void> {
    if (_initialized) return
    _initialized = true
    try {
      const userInfo = await getUserInfo()
      if (userInfo) {
        const name = (userInfo as any).realname || (userInfo as any).username || '坐席'
        _info.value = {
          id:         String((userInfo as any).id ?? ''),
          name,
          role:       (userInfo as any).postText || '客服坐席',
          avatarChar: name.charAt(0),
        }
      }
    } catch (err: any) {
      console.warn('[AgentInfo] 获取坐席信息失败，使用默认值:', err.message)
    }
  }

  return { agentInfo: readonly(_info), init }
}
