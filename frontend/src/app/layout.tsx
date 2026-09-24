import type { Metadata } from 'next'
import { Toaster } from 'sonner'
import './globals.css'

export const metadata: Metadata = {
  title: '光影票务 - 好电影，即刻出发',
  description: '光影票务电影购票平台',
  icons: {
    icon: '/guangying-logo.png',
    apple: '/guangying-logo.png',
  },
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="zh-CN">
      <body className="bg-white min-h-screen font-sans text-[#333]" suppressHydrationWarning>
        {children}
        <Toaster position="top-center" richColors />
      </body>
    </html>
  )
}
