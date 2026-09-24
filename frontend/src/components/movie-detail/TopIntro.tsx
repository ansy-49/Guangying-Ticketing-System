'use client'

import { useState } from 'react'
import { ChevronDown, ChevronUp, Play } from 'lucide-react'
import { imgUrlReplace } from '@/lib/utils'

interface Props {
  movieDetail: any
}

export default function TopIntro({ movieDetail }: Props) {
  const [showFullIntro, setShowFullIntro] = useState(false)
  const [showVideo, setShowVideo] = useState(false)

  return (
    <div>
      {/* 电影头部信息 */}
      <div className="relative flex items-start h-[280px] overflow-hidden rounded-t">
        {/* 模糊背景 */}
        <div className="absolute inset-0 z-0">
          <img
            src={imgUrlReplace(movieDetail.img)}
            alt=""
            className="w-full h-full object-cover blur-[30px] scale-125"
          />
          <div className="absolute inset-0 bg-gray-800/60" />
        </div>

        {/* 海报 */}
        <div
          className="relative z-10 ml-8 mt-8 flex-shrink-0 cursor-pointer"
          onClick={() => movieDetail.vd && setShowVideo(true)}
        >
          <img
            src={imgUrlReplace(movieDetail.img)}
            alt={movieDetail.nm}
            className="w-[180px] h-[240px] rounded-lg object-cover shadow-lg"
          />
          {movieDetail.vd && (
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="w-12 h-12 bg-black/40 rounded-full flex items-center justify-center hover:bg-black/60 transition">
                <Play className="w-6 h-6 text-white fill-white" />
              </div>
            </div>
          )}
        </div>

        {/* 文字信息 */}
        <div className="relative z-10 flex-1 mt-8 ml-8 mr-8 space-y-2 text-gray-200 min-w-0">
          <h1 className="text-white text-2xl font-bold">
            {movieDetail.nm}
          </h1>
          {movieDetail.enm && (
            <p className="text-white/70 text-sm">{movieDetail.enm}</p>
          )}
          {movieDetail.globalReleased && movieDetail.sc ? (
            <div className="flex items-baseline gap-2 mt-2">
              <span className="text-white/80 text-sm">观众评分</span>
              <span className="text-gold text-3xl font-bold">
                {movieDetail.sc}
              </span>
            </div>
          ) : movieDetail.globalReleased ? (
            <p className="text-gray-300 text-sm mt-2">暂无评分</p>
          ) : (
            <p className="mt-2">
              <span className="text-gold text-lg font-bold">
                {movieDetail.wish}
              </span>
              <span className="text-white/80 text-sm ml-1">人想看</span>
            </p>
          )}
          <div className="pt-2 space-y-1.5 text-sm text-gray-300">
            {movieDetail.cat && <p>{movieDetail.cat}</p>}
            {movieDetail.src && (
              <p>
                {movieDetail.src} / {movieDetail.dur}分钟
              </p>
            )}
            {movieDetail.pubDesc && <p>{movieDetail.pubDesc}</p>}
          </div>
        </div>
      </div>

      {/* 简介 */}
      {movieDetail.dra && (
        <div
          className="px-8 py-5 cursor-pointer border-b border-gray-100"
          onClick={() => setShowFullIntro(!showFullIntro)}
        >
          <h3 className="text-base font-medium mb-2">简介</h3>
          <p
            className={`text-sm text-gray-600 leading-relaxed ${
              !showFullIntro ? 'line-clamp-3' : ''
            }`}
          >
            {movieDetail.dra}
          </p>
          <div className="flex justify-center mt-2 text-gray-400">
            {showFullIntro ? (
              <ChevronUp className="w-4 h-4" />
            ) : (
              <ChevronDown className="w-4 h-4" />
            )}
          </div>
        </div>
      )}

      {/* 视频播放遮罩 */}
      {showVideo && movieDetail.vd && (
        <div
          className="fixed inset-0 z-[999] bg-black/70 flex items-center justify-center"
          onClick={() => setShowVideo(false)}
        >
          <video
            controls
            autoPlay
            src={movieDetail.vd}
            className="max-w-[800px] w-full max-h-[60vh]"
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      )}
    </div>
  )
}
