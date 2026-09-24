'use client'

import { Suspense, useState, useEffect, useCallback } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { ArrowLeft, ChevronLeft, ChevronRight } from 'lucide-react'
import Loading from '@/components/Loading'
import api from '@/lib/api'
import { imgUrlReplace } from '@/lib/utils'
import type { MovieItem, ScheduleItem } from '@/types'

interface CinemaInfo {
  id: number
  nm: string
  addr: string
  allowRefund: boolean
  endorse: boolean
  snack: boolean
  vipTag: string
  hallTypes: string[]
}

function CinemaDetailContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const cinemaId = searchParams.get('cinemaId')

  const [cinemaInfo, setCinemaInfo] = useState<CinemaInfo | null>(null)
  const [movies, setMovies] = useState<MovieItem[]>([])
  const [selectedMovieId, setSelectedMovieId] = useState<number | null>(null)
  const [dates, setDates] = useState<string[]>([])
  const [activeDate, setActiveDate] = useState('')
  const [schedules, setSchedules] = useState<ScheduleItem[]>([])
  const [loading, setLoading] = useState(true)
  const [scheduleLoading, setScheduleLoading] = useState(false)

  const loadSchedules = useCallback((date: string) => {
    if (!cinemaId || !selectedMovieId) return
    setScheduleLoading(true)
    api.getCinemaSchedules({ cinemaId: Number(cinemaId), movieId: selectedMovieId, showDate: date })
      .then((res) => setSchedules(res.data || []))
      .catch(() => setSchedules([]))
      .finally(() => setScheduleLoading(false))
  }, [cinemaId, selectedMovieId])

  // 加载影院信息 + 电影列表
  useEffect(() => {
    if (!cinemaId) return
    const cid = Number(cinemaId)
    Promise.all([
      api.getCinemaDetail({ cinemaId: cid }),
      api.getCinemaMovies({ cinemaId: cid }),
    ])
      .then(([cinemaRes, moviesRes]) => {
        setCinemaInfo(cinemaRes.data || cinemaRes)
        const movieList = moviesRes.data || moviesRes || []
        setMovies(movieList)
        if (movieList.length > 0) {
          setSelectedMovieId(movieList[0].id)
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [cinemaId])

  // 选中电影变化 → 加载日期
  useEffect(() => {
    if (!cinemaId || !selectedMovieId) return
    api.getCinemaAvailableDates({ cinemaId: Number(cinemaId), movieId: selectedMovieId })
      .then((res) => {
        const dateList = res.data || res || []
        setDates(dateList)
        if (dateList.length > 0) {
          setActiveDate(dateList[0])
          loadSchedules(dateList[0])
        } else {
          setDates([])
          setSchedules([])
        }
      })
      .catch(() => {
        setDates([])
        setSchedules([])
      })
  }, [cinemaId, selectedMovieId, loadSchedules])

  const handleDateSelect = (date: string) => {
    setActiveDate(date)
    loadSchedules(date)
  }

  const handleSelectSchedule = (schedule: ScheduleItem) => {
    router.push(`/seat-selection?scheduleId=${schedule.id}&movieId=${selectedMovieId}`)
  }

  const formatDateLabel = (dateStr: string) => {
    const d = new Date(dateStr)
    const today = new Date()
    const tomorrow = new Date()
    tomorrow.setDate(today.getDate() + 1)
    const month = d.getMonth() + 1
    const day = d.getDate()
    const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    if (dateStr === today.toISOString().slice(0, 10)) return `今天 ${month}月${day}日`
    if (dateStr === tomorrow.toISOString().slice(0, 10)) return `明天 ${month}月${day}日`
    return `${weekdays[d.getDay()]} ${month}月${day}日`
  }

  const selectedMovie = movies.find((m) => m.id === selectedMovieId) || null

  if (loading) return <Loading />
  if (!cinemaInfo) return <div className="text-center py-20 text-gray-400">影院不存在</div>

  // 服务标签
  const services: { tag: string; desc: string; color: string }[] = []
  if (cinemaInfo.allowRefund) services.push({ tag: '退', desc: '未取票用户放映前可退票', color: 'orange' })
  if (cinemaInfo.endorse) services.push({ tag: '改签', desc: '未取票用户放映前可改签', color: 'orange' })
  if (cinemaInfo.snack) services.push({ tag: '小吃', desc: '提供小吃饮品服务', color: 'blue' })
  cinemaInfo.hallTypes?.forEach((h) => services.push({ tag: h, desc: `${h}影厅`, color: 'blue' }))

  return (
    <div className="min-h-screen bg-white pb-20">
      {/* 影院信息头部 */}
      <div className="w-full bg-[#392f59] text-white py-8 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto px-4 flex gap-8">
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-3">
              <ArrowLeft
                className="w-6 h-6 cursor-pointer hover:text-gray-300 shrink-0"
                onClick={() => router.back()}
              />
              <h1 className="text-2xl font-bold">{cinemaInfo.nm}</h1>
            </div>
            <p className="text-gray-300 text-sm mb-2">{cinemaInfo.addr}</p>

            {services.length > 0 && (
              <div className="mt-4">
                <h3 className="font-bold text-sm mb-2">影院服务</h3>
                <div className="space-y-1.5">
                  {services.map((s, idx) => (
                    <div key={idx} className="flex text-xs items-center">
                      <span
                        className={`border px-1.5 py-0.5 rounded mr-2 ${
                          s.color === 'orange'
                            ? 'border-orange-400 text-orange-400'
                            : 'border-gray-400 text-gray-400'
                        }`}
                      >
                        {s.tag}
                      </span>
                      <span className="text-gray-300">{s.desc}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="max-w-[1200px] mx-auto px-4 mt-6">
        {/* 面包屑 */}
        <div className="text-sm text-gray-500 mb-6">
          <span className="cursor-pointer hover:text-primary" onClick={() => router.push('/')}>
            光影票务
          </span>
          {' > '}
          <span className="cursor-pointer hover:text-primary" onClick={() => router.back()}>
            影院
          </span>
          {' > '}
          <span className="text-gray-800">{cinemaInfo.nm}</span>
        </div>

        {/* 电影横向滑块 */}
        {movies.length > 0 && (
          <div className="relative w-full bg-gray-50 rounded-lg overflow-hidden mb-6">
            {/* 背景模糊 */}
            {selectedMovie && (
              <div
                className="absolute inset-0 bg-cover bg-center blur-2xl opacity-20"
                style={{ backgroundImage: `url(${selectedMovie.img})` }}
              />
            )}

            <div className="relative z-10 flex items-center py-6 px-4">
              <div className="flex items-end gap-5 overflow-x-auto hide-scrollbar px-8 py-2 mx-auto">
                {movies.map((movie) => {
                  const isSelected = movie.id === selectedMovieId
                  return (
                    <div
                      key={movie.id}
                      onClick={() => setSelectedMovieId(movie.id)}
                      className={`flex-shrink-0 transition-all duration-300 cursor-pointer border-2 rounded overflow-hidden ${
                        isSelected
                          ? 'w-[120px] h-[170px] border-white shadow-xl scale-110 z-10'
                          : 'w-[100px] h-[140px] border-transparent opacity-70 grayscale hover:grayscale-0 hover:opacity-100'
                      }`}
                    >
                      <img
                        src={imgUrlReplace(movie.img)}
                        className="w-full h-full object-cover"
                        alt={movie.nm}
                      />
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        )}

        {/* 选中电影信息 */}
        {selectedMovie && (
          <div className="text-center border-b border-gray-200 pb-6 mb-6">
            <div className="flex items-center justify-center gap-3 mb-1">
              <h2 className="text-2xl font-bold text-gray-800">{selectedMovie.nm}</h2>
              {selectedMovie.sc && Number(selectedMovie.sc) > 0 && (
                <span className="text-[#ff9900] text-xl font-bold">
                  {Number(selectedMovie.sc).toFixed(1)}分
                </span>
              )}
            </div>
            <div className="text-sm text-gray-500 space-x-4">
              {selectedMovie.cat && <span>类型：{selectedMovie.cat}</span>}
              {selectedMovie.dur && <span>时长：{selectedMovie.dur}分钟</span>}
              {selectedMovie.star && <span>主演：{selectedMovie.star}</span>}
            </div>
          </div>
        )}

        {/* 日期选择 */}
        {dates.length > 0 && (
          <div className="flex gap-6 mb-6 border-b border-gray-100">
            {dates.map((d) => (
              <button
                key={d}
                onClick={() => handleDateSelect(d)}
                className={`pb-2 border-b-2 text-sm transition-colors ${
                  d === activeDate
                    ? 'border-primary text-primary font-medium'
                    : 'border-transparent text-gray-600 hover:text-primary'
                }`}
              >
                {formatDateLabel(d)}
              </button>
            ))}
          </div>
        )}

        {/* 排片表格 */}
        {scheduleLoading ? (
          <Loading />
        ) : schedules.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            {movies.length === 0 ? '该影院暂无排片' : '当日暂无排片'}
          </div>
        ) : (
          <div className="w-full">
            <table className="w-full">
              <thead className="bg-gray-50 h-12 text-gray-500 font-normal text-sm">
                <tr>
                  <th className="text-left pl-8 w-[18%]">放映时间</th>
                  <th className="text-left w-[15%]">语言版本</th>
                  <th className="text-left w-[15%]">放映厅</th>
                  <th className="text-left w-[15%]">售价（元）</th>
                  <th className="text-right pr-8">选座购票</th>
                </tr>
              </thead>
              <tbody>
                {schedules.map((item, idx) => (
                  <tr
                    key={item.id}
                    className={`h-20 ${idx % 2 === 0 ? 'bg-white' : 'bg-[#f9f9f9]'}`}
                  >
                    <td className="pl-8">
                      <div className="text-xl font-bold text-gray-900">{item.showTime}</div>
                      <div className="text-xs text-gray-400">{item.endTime}散场</div>
                    </td>
                    <td className="text-gray-700">{item.lang}</td>
                    <td className="text-gray-700">{item.hallName}</td>
                    <td className="text-primary font-bold text-lg">¥{item.price}</td>
                    <td className="text-right pr-8">
                      <button
                        onClick={() => handleSelectSchedule(item)}
                        className="bg-white border border-primary text-primary hover:bg-primary hover:text-white transition rounded-full px-6 py-1.5 text-sm font-medium shadow-sm"
                      >
                        选座购票
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

export default function CinemaDetailPage() {
  return (
    <Suspense fallback={<Loading />}>
      <CinemaDetailContent />
    </Suspense>
  )
}
