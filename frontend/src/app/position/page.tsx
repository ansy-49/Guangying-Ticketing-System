'use client'

import { useState, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft } from 'lucide-react'
import Loading from '@/components/Loading'
import { useHomeStore } from '@/store/home'
import { formatCityList } from '@/lib/utils'
import api from '@/lib/api'
import type { CityGroup } from '@/types'

export default function PositionPage() {
  const router = useRouter()
  const { setPosId, setPosition } = useHomeStore()
  const [cityList, setCityList] = useState<CityGroup[]>([])
  const [loading, setLoading] = useState(true)
  const [activeTag, setActiveTag] = useState('')
  const tagRefs = useRef<Map<string, HTMLDivElement>>(new Map())

  useEffect(() => {
    const cached = localStorage.getItem('guangying-cities')
    if (cached) {
      try {
        const list = formatCityList(JSON.parse(cached))
        setCityList(list)
        setLoading(false)
        return
      } catch {}
    }

    api
      .getCities()
      .then((res) => {
        const cities = res?.cts || res?.cities || []
        if (cities.length) {
          localStorage.setItem('guangying-cities', JSON.stringify(cities))
          setCityList(formatCityList(cities))
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const chooseCity = (name: string, id: number) => {
    setPosition(name)
    setPosId(id)
    router.push('/')
  }

  const scrollToTag = (tag: string) => {
    setActiveTag(tag)
    const el = tagRefs.current.get(tag)
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const quickNavTags = cityList.map((c) => c.tag)

  if (loading) return <Loading />

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Top bar */}
      <div className="bg-white border-b border-gray-200 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto h-[60px] flex items-center gap-4">
          <ArrowLeft
            className="w-6 h-6 text-gray-500 cursor-pointer hover:text-primary"
            onClick={() => router.push('/')}
          />
          <h1 className="text-lg font-medium">选择城市</h1>
        </div>
      </div>

      <div className="max-w-[1200px] mx-auto mt-6 flex gap-4">
        {/* City list */}
        <div className="flex-1 bg-white rounded shadow-sm max-h-[70vh] overflow-y-auto">
          {cityList.map((group) => (
            <div
              key={group.tag}
              ref={(el) => {
                if (el) tagRefs.current.set(group.tag, el)
              }}
            >
              <div className="sticky top-0 bg-gray-100 px-6 py-2 text-sm text-gray-500 font-medium uppercase">
                {group.tag}
              </div>
              <div className="grid grid-cols-4 gap-2 px-6 py-2">
                {group.items.map((city) => (
                  <div
                    key={city.id}
                    className="px-4 py-2 text-sm cursor-pointer hover:text-primary hover:bg-gray-50 rounded"
                    onClick={() => chooseCity(city.nm, city.id)}
                  >
                    {city.nm}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

        {/* Right quick nav */}
        <div className="w-8 flex flex-col items-center justify-center py-2 bg-white rounded shadow-sm sticky top-[80px] self-start">
          {quickNavTags.map((tag) => (
            <div
              key={tag}
              className={`text-xs py-0.5 cursor-pointer uppercase ${
                tag === activeTag ? 'text-primary font-bold' : 'text-gray-400'
              }`}
              onClick={() => scrollToTag(tag)}
            >
              {tag}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
