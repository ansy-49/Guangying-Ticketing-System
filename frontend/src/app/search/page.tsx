'use client'

import { Suspense, useState, useCallback } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Search, X, ArrowLeft } from 'lucide-react'
import { useHomeStore } from '@/store/home'
import Loading from '@/components/Loading'
import api from '@/lib/api'

function SearchContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { posId } = useHomeStore()
  const [keywords, setKeywords] = useState(searchParams.get('kw') || '')
  const [results, setResults] = useState<any[]>([])
  const [timer, setTimer] = useState<ReturnType<typeof setTimeout> | null>(null)

  const handleInput = useCallback(
    (value: string) => {
      setKeywords(value)
      if (timer) clearTimeout(timer)

      if (!value.trim()) {
        setResults([])
        return
      }

      const t = setTimeout(() => {
        const cityId = posId ?? 1
        api
          .search({ kw: value, cityId, stype: 2 })
          .then((res) => {
            const cinemaList = res?.cinemas?.list || []
            const movieList = res?.movies?.list || []
            setResults([...cinemaList, ...movieList])
          })
          .catch(() => {})
      }, 300)
      setTimer(t)
    },
    [posId, timer]
  )

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Top bar */}
      <div className="bg-white border-b border-gray-200 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto h-[60px] flex items-center gap-4">
          <ArrowLeft
            className="w-6 h-6 text-gray-500 cursor-pointer hover:text-primary"
            onClick={() => router.push('/')}
          />
          <div className="flex-1 flex items-center bg-gray-100 rounded-full px-4 py-2 max-w-[600px]">
            <Search className="w-5 h-5 text-gray-400 mr-2 flex-shrink-0" />
            <input
              type="text"
              placeholder="搜索电影、影院"
              value={keywords}
              onChange={(e) => handleInput(e.target.value)}
              className="flex-1 bg-transparent outline-none text-sm"
              autoFocus
            />
            {keywords && (
              <X
                className="w-4 h-4 text-gray-400 cursor-pointer"
                onClick={() => {
                  setKeywords('')
                  setResults([])
                }}
              />
            )}
          </div>
        </div>
      </div>

      {/* Results */}
      <div className="max-w-[1200px] mx-auto mt-6">
        {results.length === 0 && keywords && (
          <div className="text-center py-20 text-gray-400">
            未找到“{keywords}”相关结果
          </div>
        )}
        <div className="bg-white rounded shadow-sm">
          {results.map((item: any, i: number) => (
            <div
              key={item.id || i}
              className="py-4 px-6 border-b border-gray-100 text-sm hover:bg-gray-50 cursor-pointer"
            >
              {item.nm || item.name}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default function SearchPage() {
  return (
    <Suspense fallback={<Loading />}>
      <SearchContent />
    </Suspense>
  )
}
