'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { useUserStore } from '@/store/user'
import BrandLogo from '@/components/BrandLogo'

export default function LoginPage() {
  const router = useRouter()
  const { loginAsync } = useUserStore()
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)

  const handleLogin = async () => {
    if (!account.trim()) return toast.error('用户名不能为空！')
    if (!password) return toast.error('请输入密码！')

    setLoading(true)
    try {
      const result = await loginAsync(account, password)
      if (result.success) {
        toast.success('登录成功')
        router.push('/')
      } else {
        toast.error(result.msg || '登录失败')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="w-[400px] bg-white rounded-lg shadow-lg p-8">
        <div className="flex items-center justify-center mb-8">
          <BrandLogo priority />
        </div>
        <h2 className="text-xl font-medium text-center mb-6">登 录</h2>
        <div className="space-y-4">
          <input
            type="text"
            placeholder="用户名"
            value={account}
            onChange={(e) => setAccount(e.target.value)}
            className="w-full py-3 px-4 border border-gray-300 rounded outline-none text-base focus:border-primary"
          />
          <input
            type="password"
            placeholder="密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleLogin()}
            className="w-full py-3 px-4 border border-gray-300 rounded outline-none text-base focus:border-primary"
          />
          <button
            onClick={handleLogin}
            disabled={loading}
            className="w-full py-3 bg-primary text-white rounded text-base disabled:opacity-60 hover:bg-red-600 transition-colors"
          >
            {loading ? '登录中...' : '登 录'}
          </button>
          <p className="text-sm text-gray-500 text-center">
            没有账号？
            <span
              className="text-secondary cursor-pointer hover:underline ml-1"
              onClick={() => router.push('/register')}
            >
              点我注册
            </span>
          </p>
          <p className="text-sm text-gray-400 text-center">
            <span className="cursor-pointer hover:text-primary" onClick={() => router.push('/')}>
              返回首页
            </span>
          </p>
        </div>
      </div>
    </div>
  )
}
