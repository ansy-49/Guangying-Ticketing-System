import Image from 'next/image'

interface BrandLogoProps {
  imageClassName?: string
  textClassName?: string
  priority?: boolean
}

export default function BrandLogo({
  imageClassName = 'w-10 h-10',
  textClassName = 'text-2xl',
  priority = false,
}: BrandLogoProps) {
  return (
    <div className="flex items-center">
      <Image
        src="/guangying-logo.png"
        alt="光影票务 Logo"
        width={48}
        height={48}
        priority={priority}
        className={`${imageClassName} object-contain mr-2`}
      />
      <span className={`${textClassName} font-bold text-primary tracking-tight`}>
        光影票务
      </span>
    </div>
  )
}
