# Builds the admin portal and serves it with Nginx, which also forwards /api/ to the NIS server.
FROM node:22-bookworm AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_API_BASE_URL=/api/nis-portal
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npx vite build

FROM nginx:1.27-alpine
# Address of the NIS server as seen from this container
ENV NIS_SERVER_URL=http://server:8010
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
