'use client'

import { useState } from 'react'
import Header from '@/components/Header'
import Footer from '@/components/Footer'
import HomeTab from '@/components/HomeTab'
import MoviesTab from '@/components/MoviesTab'
import CinemasTab from '@/components/CinemasTab'
import type { Tab } from '@/types'

export default function RootPage() {
  const [activeTab, setActiveTab] = useState<Tab>('home')

  return (
    <div className="min-h-screen bg-white text-[#333] font-sans flex flex-col">
      <Header activeTab={activeTab} setActiveTab={setActiveTab} />

      <main className="max-w-[1200px] mx-auto mt-12 min-h-[600px] pb-10 w-full">
        {activeTab === 'home' && <HomeTab />}
        {activeTab === 'movies' && <MoviesTab />}
        {activeTab === 'cinemas' && <CinemasTab />}
      </main>

      <Footer />
    </div>
  )
}
