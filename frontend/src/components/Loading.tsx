export default function Loading() {
  return (
    <div className="flex flex-col items-center justify-center py-20">
      <div className="w-10 h-10 border-4 border-gray-200 border-t-primary rounded-full animate-spin" />
      <p className="text-sm text-gray-400 mt-3">加载中...</p>
    </div>
  )
}
