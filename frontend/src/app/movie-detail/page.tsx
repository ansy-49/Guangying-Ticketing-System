'use client'

import { Suspense, useState, useEffect } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { ArrowLeft, MapPin } from 'lucide-react'
import Loading from '@/components/Loading'
import TopIntro from '@/components/movie-detail/TopIntro'
import StagePhoto from '@/components/movie-detail/StagePhoto'
import api from '@/lib/api'
import type { CinemaScheduleGroup, ScheduleItem } from '@/types'

function MovieDetailContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const movieId = searchParams.get('movieId')
  const [isReady, setIsReady] = useState(false)
  const [movieDetail, setMovieDetail] = useState<any>(null)
  const [showSchedule, setShowSchedule] = useState(false)
  const [dates, setDates] = useState<string[]>([])
  const [activeDate, setActiveDate] = useState('')
  const [cinemaGroups, setCinemaGroups] = useState<CinemaScheduleGroup[]>([])
  const [scheduleLoading, setScheduleLoading] = useState(false)

  useEffect(() => {
    if (!movieId) return
    api
      .getDetailMovie({ movieId })
      .then((res) => {
        setMovieDetail(res.detailMovie)
        setIsReady(true)
      })
      .catch(() => setIsReady(true))
  }, [movieId])

  const loadDates = () => {
    if (!movieId) return
    api.getAvailableDates({ movieId: Number(movieId) })
      .then((res) => {
        const dateList = res.data || res || []
        setDates(dateList)
        if (dateList.length > 0) {
          setActiveDate(dateList[0])
          loadSchedules(dateList[0])
        }
      })
      .catch(() => {})
  }

  const loadSchedules = (date: string) => {
    if (!movieId) return
    setScheduleLoading(true)
    api.getSchedulesByCinema({ movieId: Number(movieId), showDate: date })
      .then((res) => {
        setCinemaGroups(res.data || [])
      })
      .catch(() => {})
      .finally(() => setScheduleLoading(false))
  }

  const handleShowSchedule = () => {
    if (!showSchedule) {
      setShowSchedule(true)
      loadDates()
    } else {
      setShowSchedule(false)
    }
  }

  const handleDateSelect = (date: string) => {
    setActiveDate(date)
    loadSchedules(date)
  }

  const handleSelectSchedule = (schedule: ScheduleItem) => {
    router.push(`/seat-selection?scheduleId=${schedule.id}&movieId=${movieId}`)
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

  if (!isReady) return <Loading />
  if (!movieDetail) return <div className="text-center py-20 text-gray-400">未找到电影信息</div>

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="bg-white border-b border-gray-200 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto h-[60px] flex items-center gap-4">
          <ArrowLeft className="w-6 h-6 text-gray-500 cursor-pointer hover:text-primary" onClick={() => router.push('/')} />
          <h1 className="text-lg font-medium">{movieDetail.nm}</h1>
        </div>
      </div>

      <div className="max-w-[1200px] mx-auto mt-6 bg-white rounded shadow-sm">
        <TopIntro movieDetail={movieDetail} />
        {movieDetail.photos && <StagePhoto photos={movieDetail.photos} photoTotal={movieDetail.pn || 0} />}

        <div className="px-8 py-6 border-t border-gray-100">
          <button onClick={handleShowSchedule} className="px-8 py-3 bg-primary text-white rounded-full text-base hover:bg-red-600 transition-colors">
            {showSchedule ? '收起排片' : '特惠购票'}
          </button>
        </div>

        {showSchedule && (
          <div className="px-8 pb-8">
            {dates.length > 0 && (
              <div className="flex gap-3 mb-6 border-b border-gray-100 pb-4">
                {dates.map((d) => (
                  <button key={d} onClick={() => handleDateSelect(d)} className={`px-4 py-2 rounded-lg text-sm transition-colors ${d === activeDate ? 'bg-primary text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
                    {formatDateLabel(d)}
                  </button>
                ))}
              </div>
            )}

            {scheduleLoading ? (
              <Loading />
            ) : cinemaGroups.length === 0 ? (
              <div className="text-center py-12 text-gray-400">当日暂无排片</div>
            ) : (
              <div className="space-y-6">
                {cinemaGroups.map((group) => (
                  <div key={group.cinemaId} className="border border-gray-200 rounded-lg overflow-hidden">
                    <div className="bg-gray-50 px-6 py-4 border-b border-gray-200">
                      <h3 className="font-medium text-gray-800 text-base">{group.cinemaName}</h3>
                      {group.cinemaAddr && (
                        <div className="flex items-center text-sm text-gray-500 mt-1">
                          <MapPin className="w-3.5 h-3.5 mr-1" />
                          <span>{group.cinemaAddr}</span>
                        </div>
                      )}
                    </div>
                    <div className="divide-y divide-gray-100">
                      {group.schedules.map((schedule) => (
                        <div key={schedule.id} className="flex items-center justify-between px-6 py-4 hover:bg-gray-50 transition-colors">
                          <div className="flex items-center gap-8">
                            <div className="text-center">
                              <div className="text-lg font-bold text-gray-800">{schedule.showTime}</div>
                              <div className="text-xs text-gray-400">{schedule.endTime}散场</div>
                            </div>
                            <div className="text-sm text-gray-600">
                              <div className="flex items-center gap-2">
                                <span>{schedule.lang}</span>
                                <span className="text-gray-300">|</span>
                                <span>{schedule.hallName}</span>
                              </div>
                            </div>
                          </div>
                          <div className="flex items-center gap-6">
                            <div className="text-right">
                              <span className="text-xs text-primary">¥</span>
                              <span className="text-xl font-bold text-primary">{schedule.price}</span>
                            </div>
                            <button onClick={() => handleSelectSchedule(schedule)} className="px-6 py-2 bg-primary text-white rounded-full text-sm hover:bg-red-600 transition-colors">
                              选座购票
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default function MovieDetailPage() {
  return (
    <Suspense fallback={<Loading />}>
      <MovieDetailContent />
    </Suspense>
  )
}
