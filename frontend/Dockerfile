# Etape 1 : Build Angular

FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./

# `npm ci` strict, sans --legacy-peer-deps : le drapeau ne servait qu'a masquer un
# @angular/animations reste en 20.1.8 ; la dependance a depuis disparu du projet
# (toutes les animations sont en CSS). Le garder ferait diverger l'image de ce que
# valide la CI, qui installe en mode strict.
RUN npm ci

# Copie ciblee plutot que `COPY . .` : seules les entrees du build invalident le
# cache. Un README, un proxy.conf.json ou le nginx.conf modifie ne relance plus
# `npm run build`.
COPY angular.json tsconfig.json tsconfig.app.json ./
COPY public ./public
COPY src ./src

RUN npm run build

# Precompression des assets servis par `gzip_static` (cf. nginx.conf) : la
# compression est faite une fois au build, au niveau 9, plutot qu'a chaque requete.
RUN find dist/frontend/browser -type f \
        \( -name '*.js' -o -name '*.css' -o -name '*.html' -o -name '*.svg' -o -name '*.json' \) \
        -exec sh -c 'gzip -9 -c "$1" > "$1.gz"' _ {} \;


# Etape 2 : Image Runtime (Nginx)

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf

COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

# Sonde de disponibilite : `docker compose up --wait` (job compose de la CI)
# n'attend l'etat "healthy" que si le conteneur declare un healthcheck.
# wget vient de busybox, deja present dans l'image alpine — pas de paquet a ajouter.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
