'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, Ticket, XCircle, Clock, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import Loading from '@/components/Loading'
import api from '@/lib/api'
import type { OrderItem } from '@/types'

const statusConfig: Record<number, { label: string; color: string; icon: React.ReactNode }> = {
  0: { label: '待支付', color: 'text-amber-500', icon: <Clock className="w-4 h-4 text-amber-500" /> },
  1: { label: '已完成', color: 'text-green-500', icon: <CheckCircle2 className="w-4 h-4 text-green-500" /> },
  2: { label: '已取消', color: 'text-gray-400', icon: <XCircle className="w-4 h-4 text-gray-400" /> },
}

export default function OrdersPage() {
  const router = useRouter()
  const [orders, setOrders] = useState<OrderItem[]>([])
  const [loading, setLoading] = useState(true)

  const loadOrders = () => {
    setLoading(true)
    api.getUserOrders({})
      .then((res) => setOrders(res.data || []))
      .catch(() => toast.error('获取订单失败'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadOrders() }, [])

  const handleCancel = async (orderNo: string) => {
    if (!confirm('确定取消该订单吗？')) return
    try {
      const res = await api.cancelOrder(orderNo)
      if (res.code === 200) {
        toast.success('订单已取消')
        loadOrders()
      } else {
        toast.error(res.message || '取消失败')
      }
    } catch {
      toast.error('取消失败')
    }
  }

  const handlePay = (orderNo: string) => {
    router.push(`/payment?orderNo=${orderNo}`)
  }

  if (loading) return <Loading />

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Top bar */}
      <div className="bg-white border-b border-gray-200 min-w-[1200px]">
        <div className="max-w-[1200px] mx-auto h-[60px] flex items-center gap-4">
          <ArrowLeft className="w-6 h-6 text-gray-500 cursor-pointer hover:text-primary" onClick={() => router.push('/')} />
          <Ticket className="w-5 h-5 text-primary" />
          <h1 className="text-lg font-medium">我的订单</h1>
        </div>
      </div>

      <div className="max-w-[900px] mx-auto mt-8">
        {orders.length === 0 ? (
          <div className="text-center py-20">
            <Ticket className="w-16 h-16 text-gray-200 mx-auto mb-4" />
            <p className="text-gray-400">暂无订单</p>
            <button onClick={() => router.push('/')} className="mt-4 px-6 py-2 bg-primary text-white rounded-full text-sm">
              去选电影
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {orders.map((order) => {
              const sc = statusConfig[order.status] || statusConfig[2]
              return (
                <div key={order.orderNo} className="bg-white rounded-lg shadow-sm overflow-hidden hover:shadow-md transition-shadow">
                  <div className="flex">
                    {/* 电影海报 */}
                    {order.movieImg && (
                      <div className="w-[100px] h-[140px] flex-shrink-0">
                        <img src={order.movieImg} alt={order.movieName} className="w-full h-full object-cover" />
                      </div>
                    )}

                    {/* 信息 */}
                    <div className="flex-1 p-4 flex justify-between">
                      <div className="space-y-1">
                        <div className="text-base font-medium text-gray-800">{order.movieName}</div>
                        <div className="text-sm text-gray-500">{order.cinemaName}{order.hallName ? ` · ${order.hallName}` : ''}</div>
                        <div className="text-sm text-gray-500">{order.showTime}</div>
                        <div className="text-sm text-gray-500">座位：{order.seatsInfo || `${order.seatCount}张`}</div>
                        <div className="text-xs text-gray-400 font-mono">订单号：{order.orderNo}</div>
                      </div>

                      <div className="text-right flex flex-col justify-between items-end">
                        <div className="flex items-center gap-1">
                          {sc.icon}
                          <span className={`text-sm font-medium ${sc.color}`}>{sc.label}</span>
                        </div>
                        <div>
                          <span className="text-xs text-primary">¥</span>
                          <span className="text-xl font-bold text-primary">{order.totalPrice}</span>
                        </div>
                        <div className="flex gap-2">
                          {order.status === 0 && (
                            <>
                              <button
                                onClick={() => handleCancel(order.orderNo)}
                                className="px-3 py-1 text-xs border border-gray-300 rounded-full text-gray-500 hover:bg-gray-50"
                              >
                                取消订单
                              </button>
                              <button
                                onClick={() => handlePay(order.orderNo)}
                                className="px-3 py-1 text-xs bg-primary text-white rounded-full hover:bg-red-600"
                              >
                                去支付
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
