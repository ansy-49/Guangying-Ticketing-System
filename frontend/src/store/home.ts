import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

interface HomeState {
  position: string
  posId: number | undefined
  setPosition: (pos: string) => void
  setPosId: (id: number) => void
}

export const useHomeStore = create<HomeState>()(
  persist(
    (set) => ({
      position: '定位',
      posId: undefined,
      setPosition: (pos) => set({ position: pos }),
      setPosId: (id) => set({ posId: id }),
    }),
    {
      name: 'guangying-home',
      storage: createJSONStorage(() => localStorage),
    }
  )
)
