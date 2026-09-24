'use client'

import { useState, useEffect, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import { useHomeStore } from '@/store/home'
import Loading from '@/components/Loading'
import api from '@/lib/api'
import { formatDate } from '@/lib/utils'
import type { CinemaItem } from '@/types'

const BRAND_OPTIONS = ['全部', '万达影城', 'CGV影城', '星美国际', '金逸影城', '大地影院', '百老汇影城', '博纳国际', '中影国际', '横店影城', '耀莱成龙']
const DISTRICT_OPTIONS = ['全部', '朝阳区', '海淀区', '东城区', '西城区', '丰台区', '通州区']
const HALL_TYPE_OPTIONS = ['全部', 'IMAX厅', '杜比全景声厅', '4DX厅', '中国巨幕厅', '激光厅', '杜比影院']
const SERVICE_OPTIONS = ['全部', '可退票', '可改签']

function FilterRow({
  label,
  options,
  active = '全部',
  onSelect,
}: {
  label: string
  options: string[]
  active?: string
  onSelect?: (v: string) => void
}) {
  return (
    <div className="flex items-start py-3 border-b border-dashed border-gray-200 last:border-0">
      <span className="text-gray-400 w-20 text-sm shrink-0 mt-0.5">
        {label}
      </span>
      <div className="flex flex-wrap gap-3">
        {options.map((opt) => (
          <span
            key={opt}
            onClick={() => onSelect?.(opt)}
            className={`px-3 py-0.5 text-sm rounded-full cursor-pointer transition-colors ${
              opt === active
                ? 'bg-primary text-white'
                : 'text-gray-700 hover:text-primary'
            }`}
          >
            {opt}
          </span>
        ))}
      </div>
    </div>
  )
}

export default function CinemasTab() {
  const router = useRouter()
  const { posId } = useHomeStore()
  const [cinemas, setCinemas] = useState<CinemaItem[]>([])
  const [loading, setLoading] = useState(true)
  const [activeBrand, setActiveBrand] = useState('全部')
  const [activeDistrict, setActiveDistrict] = useState('全部')
  const [activeHallType, setActiveHallType] = useState('全部')
  const [activeService, setActiveService] = useState('全部')

  useEffect(() => {
    const cityId = posId ?? 1
    api
      .getCinemaList({
        cityId,
        day: formatDate(),
      })
      .then((res) => {
        const list = res?.cinemas || res?.data?.cinemas || []
        setCinemas(list)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [posId])

  /* ---- 前端过滤逻辑 ---- */
  const filteredCinemas = useMemo(() => {
    return cinemas.filter((c) => {
      // 品牌：影院名包含品牌关键词
      if (activeBrand !== '全部' && !c.nm?.includes(activeBrand.replace('影城', '').replace('国际', ''))) return false
      // 行政区：地址包含区名
      if (activeDistrict !== '全部' && !c.addr?.includes(activeDistrict)) return false
      // 影厅类型：hallType 数组是否包含
      if (activeHallType !== '全部') {
        const halls = c.tag?.hallType || []
        if (!halls.some((h) => h.includes(activeHallType.replace('厅', '').replace('影院', '')))) return false
      }
      // 影院服务
      if (activeService === '可退票' && !c.tag?.allowRefund) return false
      if (activeService === '可改签' && !c.tag?.endorse) return false
      return true
    })
  }, [cinemas, activeBrand, activeDistrict, activeHallType, activeService])

  return (
    <div>
      {/* Filters */}
      <div className="border border-gray-200 bg-white p-5 mb-8 text-sm">
        <FilterRow
          label="品牌："
          options={BRAND_OPTIONS}
          active={activeBrand}
          onSelect={setActiveBrand}
        />
        <FilterRow
          label="行政区："
          options={DISTRICT_OPTIONS}
          active={activeDistrict}
          onSelect={setActiveDistrict}
        />
        <FilterRow
          label="影厅类型："
          options={HALL_TYPE_OPTIONS}
          active={activeHallType}
          onSelect={setActiveHallType}
        />
        <FilterRow
          label="影院服务："
          options={SERVICE_OPTIONS}
          active={activeService}
          onSelect={setActiveService}
        />
      </div>

      {/* Header */}
      <div className="flex items-center mb-6 pl-4 border-l-4 border-primary">
        <h2 className="text-xl text-gray-800">影院列表</h2>
      </div>

      {/* Cinema List */}
      {loading ? (
        <Loading />
      ) : filteredCinemas.length === 0 ? (
        <div className="text-center py-20 text-gray-400">暂无影院数据</div>
      ) : (
        <div className="space-y-0">
          {filteredCinemas.map((cinema) => {
            const tags: { text: string; type: 'blue' | 'orange' }[] = []
            if (cinema.tag?.allowRefund) tags.push({ text: '退票', type: 'blue' })
            if (cinema.tag?.endorse) tags.push({ text: '改签', type: 'blue' })
            if (cinema.tag?.snack) tags.push({ text: '小吃', type: 'orange' })
            if (cinema.tag?.vipTag) tags.push({ text: cinema.tag.vipTag, type: 'orange' })
            cinema.tag?.hallType?.forEach((h) => tags.push({ text: h, type: 'blue' }))

            return (
              <div
                key={cinema.id}
                className="py-6 border-b border-gray-100 flex justify-between items-center group hover:bg-gray-50 px-4 transition-colors"
              >
                {/* Left: Info */}
                <div className="flex-1">
                  <h3
                    onClick={() => router.push(`/cinema-detail?cinemaId=${cinema.id}`)}
                    className="text-base font-medium text-gray-800 mb-1 cursor-pointer hover:text-primary transition-colors"
                  >
                    {cinema.nm}
                  </h3>
                  <div className="text-sm text-gray-500 mb-2 truncate max-w-[600px]">
                    {cinema.addr}
                  </div>
                  <div className="flex space-x-2 flex-wrap gap-y-1">
                    {tags.slice(0, 5).map((tag, idx) => (
                      <span
                        key={idx}
                        className={`text-xs px-1 border rounded ${
                          tag.type === 'blue'
                            ? 'border-blue-300 text-blue-400'
                            : 'border-orange-300 text-orange-400'
                        }`}
                      >
                        {tag.text}
                      </span>
                    ))}
                  </div>
                </div>

                {/* Right: Price & Buy */}
                <div className="flex items-center space-x-6 text-right">
                  <div>
                    {cinema.distance && (
                      <div className="text-xs text-gray-400">
                        {cinema.distance}
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => router.push(`/cinema-detail?cinemaId=${cinema.id}`)}
                    className="px-5 py-1.5 bg-primary text-white rounded-full text-sm shadow-sm hover:bg-red-600 transition-colors"
                  >
                    选座购票
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
