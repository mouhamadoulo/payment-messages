# Etape 1 : Build Angular

FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./

# --legacy-peer-deps : @angular/animations est encore en 20.1.8 alors que le reste
# du projet est en 22.x (conflit de peer deps). A retirer une fois la dep alignee.
RUN npm ci --legacy-peer-deps

COPY . .

RUN npm run build


# Etape 2 : Image Runtime (Nginx)

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
