'use client'

import { Suspense, useState, useEffect, useRef } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { ArrowLeft, Coins, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import Loading from '@/components/Loading'
import api from '@/lib/api'
import { useUserStore } from '@/store/user'
import type { OrderItem } from '@/types'

function PaymentContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const orderNo = searchParams.get('orderNo')
  const { points, setPoints, fetchPoints, isLogged } = useUserStore()

  const [order, setOrder] = useState<OrderItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [paying, setPaying] = useState(false)
  const [timeLeft, setTimeLeft] = useState(0)
  const timerRef = useRef<NodeJS.Timeout | null>(null)

  // 加载订单详情 + 刷新积分
  useEffect(() => {
    if (!orderNo) return
    fetchPoints()
    api.getOrderDetail({ orderNo })
      .then((res) => {
        const data = res.data || res
        setOrder(data)
        if (data.expireTime) {
          const expire = new Date(data.expireTime).getTime()
          const now = Date.now()
          const diff = Math.max(0, Math.floor((expire - now) / 1000))
          setTimeLeft(diff)
        }
      })
      .catch(() => toast.error('加载订单失败'))
      .finally(() => setLoading(false))
  }, [orderNo, fetchPoints])

  // 倒计时
  const hasActiveCountdown = timeLeft > 0
  useEffect(() => {
    if (!hasActiveCountdown) return
    timerRef.current = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timerRef.current!)
          toast.error('订单已超时，请重新选座')
          router.push('/')
          return 0
        }
        return prev - 1
      })
    }, 1000)
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  }, [hasActiveCountdown, router])

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60)
    const s = seconds % 60
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }

  const pointsCost = order ? Math.ceil(Number(order.totalPrice)) : 0
  const hasEnoughPoints = points >= pointsCost

  // 积分支付
  const handlePay = async () => {
    if (!orderNo || paying) return
    if (!hasEnoughPoints) {
      toast.error(`积分不足，需要${pointsCost}积分，当前${points}积分`)
      return
    }
    setPaying(true)
    try {
      const res = await api.payOrder({ orderNo })
      if (res.code === 200) {
        // 更新本地积分
        if (res.data?.remainingPoints != null) {
          setPoints(res.data.remainingPoints)
        } else {
          setPoints(Math.max(0, points - pointsCost))
        }
        toast.success('支付成功！')
        router.push(`/order-success?orderNo=${orderNo}`)
      } else {
        toast.error(res.message || '支付失败')
      }
    } catch (e: any) {
      const msg = e?.response?.data?.message || '支付失败，请重试'
      toast.error(msg)
    } finally {
      setPaying(false)
    }
  }

  if (loading) return <Loading />
  if (!order) return <div className="text-center py-20 text-gray-400">订单不存在</div>

  const isPending = order.status === 0

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="bg-white border-b border-gray-200 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto h-[60px] flex items-center gap-4">
          <ArrowLeft className="w-6 h-6 text-gray-500 cursor-pointer hover:text-primary" onClick={() => router.back()} />
          <h1 className="text-lg font-medium">确认订单</h1>
        </div>
      </div>

      <div className="bg-white border-b border-gray-100 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto flex items-center justify-center py-4 gap-2 text-sm">
          {['选择场次', '选择座位', '积分支付', '影院取票观影'].map((step, idx) => (
            <div key={step} className="flex items-center gap-2">
              <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs ${idx <= 2 ? 'bg-primary text-white' : 'bg-gray-200 text-gray-500'}`}>{idx + 1}</span>
              <span className={idx <= 2 ? 'text-primary font-medium' : 'text-gray-400'}>{step}</span>
              {idx < 3 && <span className="text-gray-300 mx-2">→</span>}
            </div>
          ))}
        </div>
      </div>

      <div className="max-w-[800px] mx-auto mt-8">
        {isPending && timeLeft > 0 && (
          <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 mb-6 flex items-center justify-between">
            <span className="text-amber-700">请在规定时间内完成支付，超时订单将自动取消</span>
            <span className="text-2xl font-bold text-primary">{formatTime(timeLeft)}</span>
          </div>
        )}

        {/* 订单信息 */}
        <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
          <h2 className="text-lg font-medium text-gray-800 mb-4">订单信息</h2>
          <div className="grid grid-cols-2 gap-y-3 text-sm">
            <div className="text-gray-400">订单编号</div>
            <div className="text-gray-700 font-mono">{order.orderNo}</div>
            <div className="text-gray-400">电影</div>
            <div className="text-gray-700">{order.movieName || '-'}</div>
            <div className="text-gray-400">影院</div>
            <div className="text-gray-700">{order.cinemaName || '-'}</div>
            <div className="text-gray-400">影厅</div>
            <div className="text-gray-700">{order.hallName || '-'}</div>
            <div className="text-gray-400">场次</div>
            <div className="text-gray-700">{order.showTime || '-'}</div>
            <div className="text-gray-400">座位</div>
            <div className="text-gray-700">{order.seatsInfo || `${order.seatCount}张`}</div>
            <div className="text-gray-400">单价</div>
            <div className="text-gray-700">{Math.ceil(Number(order.unitPrice))} 积分</div>
          </div>
          <div className="border-t border-gray-100 mt-4 pt-4 flex items-baseline justify-between">
            <span className="text-gray-600">需支付</span>
            <div className="flex items-center gap-1">
              <Coins className="w-5 h-5 text-amber-500" />
              <span className="text-3xl font-bold text-primary">{pointsCost}</span>
              <span className="text-sm text-gray-500">积分</span>
            </div>
          </div>
        </div>

        {/* 积分信息 & 支付 */}
        {isPending && (
          <>
            <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
              <h2 className="text-lg font-medium text-gray-800 mb-4">积分支付</h2>
              <div className="flex items-center justify-between p-4 rounded-lg border-2 border-primary bg-red-50">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-amber-100 flex items-center justify-center">
                    <Coins className="w-5 h-5 text-amber-500" />
                  </div>
                  <div>
                    <div className="font-medium text-gray-800">我的积分</div>
                    <div className="text-sm text-gray-400">1积分 = 1元</div>
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-2xl font-bold text-primary">{points}</div>
                  <div className="text-xs text-gray-400">当前余额</div>
                </div>
              </div>
              {!hasEnoughPoints && (
                <div className="mt-3 text-sm text-red-500 bg-red-50 p-3 rounded">
                  积分不足！需要 {pointsCost} 积分，当前仅有 {points} 积分
                </div>
              )}
              {hasEnoughPoints && (
                <div className="mt-3 text-sm text-green-600 bg-green-50 p-3 rounded">
                  支付后剩余 {points - pointsCost} 积分
                </div>
              )}
            </div>

            <button
              onClick={handlePay}
              disabled={paying || !hasEnoughPoints}
              className={`w-full py-4 rounded-full text-white font-medium text-lg transition-colors ${
                paying || !hasEnoughPoints ? 'bg-gray-300 cursor-not-allowed' : 'bg-primary hover:bg-red-600'
              }`}
            >
              {paying ? '支付处理中...' : `积分支付 ${pointsCost} 积分`}
            </button>
          </>
        )}

        {!isPending && (
          <div className="bg-white rounded-lg shadow-sm p-8 text-center">
            <div className="text-lg text-gray-600">
              订单状态：<span className="font-medium text-primary">{order.statusDesc}</span>
            </div>
            <button onClick={() => router.push('/')} className="mt-4 px-6 py-2 bg-primary text-white rounded-full">返回首页</button>
          </div>
        )}
      </div>
    </div>
  )
}

export default function PaymentPage() {
  return (
    <Suspense fallback={<Loading />}>
      <PaymentContent />
    </Suspense>
  )
}
