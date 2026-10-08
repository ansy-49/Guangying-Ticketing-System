# =========================================
# 光影前端 - 多阶段构建 Dockerfile
#
# Stage 1: Node.js 编译
# Stage 2: Node.js 运行 standalone server
# =========================================

# ---- Stage 1: Build ----
FROM node:20-alpine AS builder

WORKDIR /build

# 复制包管理文件（利用缓存）
COPY package.json package-lock.json* ./

# 安装依赖
RUN npm ci --prefer-offline

# 复制源码
COPY . .

# 构建 Next.js standalone
ENV NEXT_TELEMETRY_DISABLED=1
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=${BACKEND_URL}
RUN npm run build

# ---- Stage 2: Runtime ----
FROM node:20-alpine

WORKDIR /app

# 复制 standalone 输出
COPY --from=builder /build/.next/standalone ./
COPY --from=builder /build/.next/static ./.next/static

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
