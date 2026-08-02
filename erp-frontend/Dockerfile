# ---- 构建阶段 ----
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

# ---- 运行阶段 (Nginx 托管 SPA + 反代 /api) ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
