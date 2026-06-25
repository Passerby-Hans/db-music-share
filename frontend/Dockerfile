# ---- build：pnpm 构建静态产物 ----
FROM node:20-alpine AS build
WORKDIR /build
# 容器内 npm/pnpm 换阿里 npmmirror(原淘宝镜像),加速 pnpm install
ENV npm_config_registry=https://registry.npmmirror.com
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# ---- run：nginx 托管 ----
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80 9000
