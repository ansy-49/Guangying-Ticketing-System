'use client'

import { ChevronRight } from 'lucide-react'
import { imgUrlReplace } from '@/lib/utils'

interface Props {
  photos: string[]
  photoTotal: number
}

export default function StagePhoto({ photos, photoTotal }: Props) {
  return (
    <div className="px-8 py-5 border-b border-gray-100">
      {/* 标题栏 */}
      <div className="flex items-center justify-between mb-3">
        <span className="text-base font-medium">剧照</span>
        <div className="flex items-center text-sm text-gray-400 cursor-pointer hover:text-gray-600">
          <span>全部 {photoTotal} 张</span>
          <ChevronRight className="w-4 h-4" />
        </div>
      </div>
      {/* 照片列表 */}
      <div className="flex gap-3 overflow-x-auto scrollbar-hide">
        {photos.map((photo, index) => (
          <img
            key={index}
            src={imgUrlReplace(photo)}
            alt={`剧照 ${index + 1}`}
            className="w-[200px] h-[130px] rounded-lg object-cover flex-shrink-0 hover:opacity-90 transition cursor-pointer"
          />
        ))}
      </div>
    </div>
  )
}
