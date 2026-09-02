# Stage 1: Build
FROM node:22-alpine AS builder

WORKDIR /app

# 直接安装 pnpm（不使用 corepack；项目 pnpm-workspace.yaml 用了 allowBuilds，需 pnpm 11）
RUN npm install -g pnpm@11.24.0

# 先复制所有源代码（.dockerignore 会排除不需要的文件）
COPY data-agent-frontend/ .

# 再安装依赖
RUN pnpm install --frozen-lockfile

# Build the application (跳过类型检查，直接打包)
RUN pnpm vite build

# Stage 2: Production with Nginx
FROM nginx:alpine

# Copy custom nginx config
COPY docker/frontend/nginx.conf /etc/nginx/nginx.conf

# Copy built files
COPY --from=builder /app/dist /usr/share/nginx/html

# Add healthcheck
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
