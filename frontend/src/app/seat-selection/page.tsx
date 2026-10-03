'use client'

import { Suspense, useState, useEffect, useCallback, useRef } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { ArrowLeft, Monitor, Coins } from 'lucide-react'
import { toast } from 'sonner'
import Loading from '@/components/Loading'
import QueueWaiting from '@/components/QueueWaiting'
import api from '@/lib/api'
import { imgUrlReplace } from '@/lib/utils'
import { useUserStore } from '@/store/user'
import type { SeatInfo, SeatLayoutData } from '@/types'

function SeatSelectionContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const scheduleId = searchParams.get('scheduleId')
  const movieId = searchParams.get('movieId')
  const { isLogged } = useUserStore()

  const [layout, setLayout] = useState<SeatLayoutData | null>(null)
  const [movieDetail, setMovieDetail] = useState<any>(null)
  const [schedule, setSchedule] = useState<any>(null)
  const [selectedSeats, setSelectedSeats] = useState<SeatInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [locking, setLocking] = useState(false)
  const pendingIdempotencyKey = useRef<string | null>(null)

  // 排队状态
  const [queueAdmitted, setQueueAdmitted] = useState(false)
  const [queuePosition, setQueuePosition] = useState(0)
  const [queueWait, setQueueWait] = useState(0)
  const [queueChecked, setQueueChecked] = useState(false)

  const MAX_SEATS = 6

  // 第一步：检查排队准入
  useEffect(() => {
    if (!scheduleId) return

    if (isLogged) {
      api
        .queueEnter({ scheduleId: Number(scheduleId) })
        .then((res) => {
          const data = res.data || res
          if (data.admitted) {
            setQueueAdmitted(true)
          } else {
            setQueuePosition(data.position)
            setQueueWait(data.estimatedWaitSeconds)
          }
        })
        .catch(() => {
          // 排队接口失败 → 直接放行（降级安全）
          setQueueAdmitted(true)
        })
        .finally(() => setQueueChecked(true))
    } else {
      // 未登录 → 跳过排队，展示座位图（但锁座时会提示登录）
      setQueueAdmitted(true)
      setQueueChecked(true)
    }
  }, [scheduleId, isLogged])

  // 第二步：排队通过后加载数据
  useEffect(() => {
    if (!queueAdmitted || !scheduleId || !queueChecked) return

    const loadData = async () => {
      try {
        const [layoutRes, movieRes] = await Promise.all([
          api.getSeatLayout({ scheduleId: Number(scheduleId) }),
          movieId ? api.getDetailMovie({ movieId }) : null,
        ])

        setLayout(layoutRes.data || layoutRes)
        if (movieRes) setMovieDetail(movieRes.detailMovie)

        if (movieId) {
          const scheduleRes = await api.getSchedules({ movieId: Number(movieId) })
          const schedules = scheduleRes.data || []
          const found = schedules.find((s: any) => s.id === Number(scheduleId))
          if (found) setSchedule(found)
        }
      } catch (e) {
        toast.error('加载座位信息失败')
      } finally {
        setLoading(false)
      }
    }

    loadData()
  }, [queueAdmitted, scheduleId, movieId, queueChecked])

  const handleAdmitted = useCallback(() => {
    setQueueAdmitted(true)
    setLoading(true)
  }, [])

  const handleQueueLeave = useCallback(() => {
    router.back()
  }, [router])

  // 排队中
  if (queueChecked && !queueAdmitted && scheduleId && isLogged) {
    return (
      <QueueWaiting
        scheduleId={Number(scheduleId)}
        position={queuePosition}
        estimatedWaitSeconds={queueWait}
        onAdmitted={handleAdmitted}
        onLeave={handleQueueLeave}
      />
    )
  }

  // 点击座位
  const handleSeatClick = (seat: SeatInfo) => {
    if (seat.status === -1 || seat.status === 1 || seat.status === 2) return

    const isSelected = selectedSeats.some(s => s.row === seat.row && s.col === seat.col)
    if (isSelected) {
      setSelectedSeats(prev => prev.filter(s => !(s.row === seat.row && s.col === seat.col)))
    } else {
      if (selectedSeats.length >= MAX_SEATS) {
        toast.warning(`最多选择${MAX_SEATS}个座位`)
        return
      }
      setSelectedSeats(prev => [...prev, seat])
    }
  }

  // 确认选座
  const handleConfirm = async () => {
    if (!isLogged) {
      toast.error('请先登录')
      router.push('/login')
      return
    }
    if (selectedSeats.length === 0) {
      toast.warning('请选择座位')
      return
    }

    setLocking(true)
    try {
      // 锁座 + 建单（一个请求完成，直接返回 orderNo）
      const lockRes = await api.lockSeats({
        idempotencyKey: pendingIdempotencyKey.current ??= crypto.randomUUID(),
        scheduleId: Number(scheduleId),
        seats: selectedSeats.map(s => ({ row: s.row, col: s.col })),
      })

      if (lockRes.code !== 200) {
        toast.error(lockRes.message || '锁座失败')
        setLocking(false)
        return
      }

      const orderNo = lockRes.data?.orderNo
      if (!orderNo) {
        toast.error('订单创建失败')
        setLocking(false)
        return
      }

      toast.success('锁座成功，请在15分钟内完成支付')
      pendingIdempotencyKey.current = null

      // 直接跳转支付页
      router.push(`/payment?orderNo=${orderNo}`)
    } catch (e: any) {
      const msg = e?.response?.data?.message || '操作失败，请重试'
      toast.error(msg)
    } finally {
      setLocking(false)
    }
  }

  const getSeatColor = (seat: SeatInfo, isSelected: boolean) => {
    if (seat.status === -1) return 'bg-transparent'
    if (seat.status === 1) return 'bg-gray-300'
    if (seat.status === 2) return 'bg-orange-200'
    if (seat.status === 3 || isSelected) return 'bg-primary'
    if (seat.couple) return 'bg-pink-100 border-pink-300 hover:bg-pink-200'
    return 'bg-green-100 border-green-300 hover:bg-green-200'
  }

  const getSeatCursor = (seat: SeatInfo) => {
    if (seat.status === -1 || seat.status === 1 || seat.status === 2) return 'cursor-not-allowed'
    return 'cursor-pointer'
  }

  const totalPoints = schedule ? Math.ceil(selectedSeats.length * (schedule.price || 0)) : 0

  if (loading) return <Loading />

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Top bar */}
      <div className="bg-white border-b border-gray-200 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto h-[60px] flex items-center gap-4">
          <ArrowLeft className="w-6 h-6 text-gray-500 cursor-pointer hover:text-primary" onClick={() => router.back()} />
          <h1 className="text-lg font-medium">选择座位</h1>
        </div>
      </div>

      {/* 步骤指示 */}
      <div className="bg-white border-b border-gray-100 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto flex items-center justify-center py-4 gap-2 text-sm">
          {['选择场次', '选择座位', '积分支付', '影院取票观影'].map((step, idx) => (
            <div key={step} className="flex items-center gap-2">
              <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs ${idx <= 1 ? 'bg-primary text-white' : 'bg-gray-200 text-gray-500'}`}>
                {idx + 1}
              </span>
              <span className={idx <= 1 ? 'text-primary font-medium' : 'text-gray-400'}>{step}</span>
              {idx < 3 && <span className="text-gray-300 mx-2">→</span>}
            </div>
          ))}
        </div>
      </div>

      <div className="max-w-[1200px] mx-auto mt-6 flex gap-6">
        {/* 左侧：座位图 */}
        <div className="flex-1 bg-white rounded shadow-sm p-6">
          {layout ? (
            <>
              {/* 影厅信息 */}
              <div className="text-center mb-6">
                <h2 className="text-lg font-medium text-gray-800">{layout.hallName}</h2>
                <span className="text-sm text-gray-400">{layout.hallType}</span>
              </div>

              {/* 银幕 */}
              <div className="flex justify-center mb-8">
                <div className="w-[60%] h-1 bg-gradient-to-r from-transparent via-gray-400 to-transparent rounded-full relative">
                  <div className="absolute -top-5 left-1/2 -translate-x-1/2 text-xs text-gray-400 flex items-center gap-1">
                    <Monitor className="w-3.5 h-3.5" />
                    银幕
                  </div>
                </div>
              </div>

              {/* 座位图例 */}
              <div className="flex items-center justify-center gap-6 mb-4 text-xs text-gray-500">
                <div className="flex items-center gap-1">
                  <div className="w-4 h-4 bg-green-100 border border-green-300 rounded" />
                  <span>可选</span>
                </div>
                <div className="flex items-center gap-1">
                  <div className="w-4 h-4 bg-primary rounded" />
                  <span>已选</span>
                </div>
                <div className="flex items-center gap-1">
                  <div className="w-4 h-4 bg-gray-300 rounded" />
                  <span>已售</span>
                </div>
                <div className="flex items-center gap-1">
                  <div className="w-4 h-4 bg-orange-200 rounded" />
                  <span>锁定</span>
                </div>
                <div className="flex items-center gap-1">
                  <div className="w-4 h-4 bg-pink-100 border border-pink-300 rounded" />
                  <span>情侣座</span>
                </div>
              </div>

              {/* 座位网格 */}
              <div className="flex justify-center overflow-x-auto">
                <div className="inline-block">
                  {layout.seats.map((row, rowIdx) => (
                    <div key={rowIdx} className="flex items-center mb-1">
                      {/* 行号 */}
                      <div className="w-6 text-xs text-gray-400 text-right mr-2 shrink-0">
                        {rowIdx + 1}
                      </div>
                      {row.map((seat, colIdx) => {
                        const isSelected = selectedSeats.some(s => s.row === seat.row && s.col === seat.col)
                        const hasAisle = layout.aisles.includes(colIdx + 1)
                        return (
                          <div key={colIdx} className={`flex items-center ${hasAisle ? 'mr-4' : ''}`}>
                            {seat.status === -1 ? (
                              <div className="w-7 h-7 m-0.5" />
                            ) : (
                              <div
                                onClick={() => handleSeatClick(seat)}
                                title={seat.label}
                                className={`w-7 h-7 m-0.5 rounded border text-[10px] flex items-center justify-center transition-all ${getSeatColor(seat, isSelected)} ${getSeatCursor(seat)} ${isSelected ? 'text-white scale-110' : 'text-gray-600'}`}
                              >
                                {isSelected ? '✓' : seat.col}
                              </div>
                            )}
                          </div>
                        )
                      })}
                    </div>
                  ))}
                </div>
              </div>
            </>
          ) : (
            <div className="text-center py-12 text-gray-400">暂无座位信息</div>
          )}
        </div>

        {/* 右侧：选座信息 */}
        <div className="w-[320px] shrink-0">
          <div className="bg-white rounded shadow-sm p-6 sticky top-[100px]">
            {/* 电影信息 */}
            {movieDetail && (
              <div className="flex gap-3 mb-6 pb-4 border-b border-gray-100">
                <img src={imgUrlReplace(movieDetail.img)} alt={movieDetail.nm} className="w-16 h-22 rounded object-cover" />
                <div className="flex-1 min-w-0">
                  <h3 className="font-medium text-gray-800 truncate">{movieDetail.nm}</h3>
                  {movieDetail.cat && <p className="text-xs text-gray-400 mt-1">{movieDetail.cat}</p>}
                  {movieDetail.dur && <p className="text-xs text-gray-400">{movieDetail.dur}分钟</p>}
                </div>
              </div>
            )}

            {/* 场次信息 */}
            {schedule && (
              <div className="mb-6 pb-4 border-b border-gray-100 text-sm text-gray-600">
                <p>{schedule.showDate} {schedule.showTime} - {schedule.endTime}</p>
                <p className="text-gray-400 mt-1">{schedule.hallName} / {schedule.lang}</p>
              </div>
            )}

            {/* 已选座位 */}
            <div className="mb-6">
              <h4 className="text-sm font-medium text-gray-700 mb-3">
                已选座位 ({selectedSeats.length}/{MAX_SEATS})
              </h4>
              {selectedSeats.length === 0 ? (
                <p className="text-sm text-gray-400">请在左侧选择座位</p>
              ) : (
                <div className="space-y-2">
                  {selectedSeats.map((seat) => (
                    <div key={`${seat.row}-${seat.col}`} className="flex items-center justify-between text-sm">
                      <span className="text-gray-700">{seat.label}</span>
                      <div className="flex items-center gap-2">
                        <span className="text-primary font-medium">{Math.ceil(schedule?.price || 0)} 积分</span>
                        <button
                          onClick={() => setSelectedSeats(prev => prev.filter(s => !(s.row === seat.row && s.col === seat.col)))}
                          className="text-gray-400 hover:text-red-500 text-xs"
                        >
                          ✕
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 总价 + 确认 */}
            <div className="border-t border-gray-100 pt-4">
              <div className="flex items-baseline justify-between mb-4">
                <span className="text-gray-600 text-sm">总计</span>
                <div className="flex items-center gap-1">
                  <Coins className="w-5 h-5 text-primary" />
                  <span className="text-2xl font-bold text-primary">{totalPoints}</span>
                  <span className="text-sm text-primary">积分</span>
                </div>
              </div>
              <button
                onClick={handleConfirm}
                disabled={selectedSeats.length === 0 || locking}
                className={`w-full py-3 rounded-full text-white font-medium transition-colors ${
                  selectedSeats.length === 0 || locking
                    ? 'bg-gray-300 cursor-not-allowed'
                    : 'bg-primary hover:bg-red-600'
                }`}
              >
                {locking ? '锁座中...' : `确认选座（${selectedSeats.length}张）`}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function SeatSelectionPage() {
  return (
    <Suspense fallback={<Loading />}>
      <SeatSelectionContent />
    </Suspense>
  )
}
