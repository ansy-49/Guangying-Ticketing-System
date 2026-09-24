import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import api from '@/lib/api'

interface UserState {
  isLogged: boolean
  userNick: string
  userHeadImg: string | undefined
  defaultHeadImg: string
  token: string | null
  points: number

  loginAsync: (account: string, password: string) => Promise<{ success: boolean; msg?: string }>
  registerAsync: (account: string, password: string, userNick: string | undefined, inviteCode: string) => Promise<{ success: boolean; msg?: string }>
  logout: () => void
  setPoints: (points: number) => void
  fetchPoints: () => Promise<void>
}

export const useUserStore = create<UserState>()(
  persist(
    (set, get) => ({
      isLogged: false,
      userNick: '',
      userHeadImg: undefined,
      defaultHeadImg: 'https://picsum.photos/seed/default-avatar/100/100',
      token: null,
      points: 0,

      loginAsync: async (account, password) => {
        try {
          const res = await api.login({ account, password })
          if (res.code === 200 && res.data) {
            set({
              isLogged: true,
              userNick: res.data.userNick || account,
              userHeadImg: res.data.headImg || undefined,
              token: res.data.token,
              points: res.data.points ?? 0,
            })
            return { success: true }
          }
          return { success: false, msg: res.message || '登录失败' }
        } catch (e: any) {
          const msg = e?.response?.data?.message || '网络错误，请稍后重试'
          return { success: false, msg }
        }
      },

      registerAsync: async (account, password, userNick, inviteCode) => {
        try {
          const res = await api.register({ account, password, userNick, inviteCode })
          if (res.code === 200) {
            return { success: true }
          }
          return { success: false, msg: res.message || '注册失败' }
        } catch (e: any) {
          const msg = e?.response?.data?.message || '网络错误，请稍后重试'
          return { success: false, msg }
        }
      },

      logout: () =>
        set({
          isLogged: false,
          userNick: '',
          userHeadImg: undefined,
          token: null,
          points: 0,
        }),

      setPoints: (points: number) => set({ points }),

      fetchPoints: async () => {
        try {
          const res = await api.getUserInfo()
          if (res.code === 200 && res.data) {
            set({ points: res.data.points ?? 0 })
          }
        } catch {}
      },
    }),
    {
      name: 'guangying-user',
      storage: createJSONStorage(() => localStorage),
    }
  )
)
