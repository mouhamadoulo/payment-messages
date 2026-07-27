# Etape 1 : Build Maven

FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B clean package -DskipTests


# Etape 2 : Decoupage du jar en couches
#
# Un jar Spring Boot mono-couche fait ~60 Mo dont l'essentiel est immuable (dependances) :
# sans decoupage, chaque build reconstruit et re-pousse la totalite pour quelques Ko de
# code applicatif. `jarmode=tools extract --layers` separe dependencies /
# spring-boot-loader / snapshot-dependencies / application, copiees ensuite du plus stable
# au plus volatil pour que le cache Docker ne casse que sur la derniere couche.

FROM eclipse-temurin:21-jre AS extractor

WORKDIR /build

COPY --from=builder /app/target/*.jar app.jar

RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted


# Etape 3 : Image runtime

FROM eclipse-temurin:21-jre

# curl : sonde de disponibilite du conteneur (cf. healthcheck docker-compose).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Execution sans privileges : uid numerique (et non un nom) pour que Kubernetes puisse
# verifier `runAsNonRoot`. Groupe 0 + droits de groupe : convention OpenShift, ou l'uid
# est reattribue arbitrairement au demarrage.
RUN useradd --system --uid 1001 --gid 0 --no-create-home --shell /usr/sbin/nologin spring

WORKDIR /app

COPY --from=extractor --chown=1001:0 /build/extracted/dependencies/ ./
COPY --from=extractor --chown=1001:0 /build/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=1001:0 /build/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=1001:0 /build/extracted/application/ ./

# Journaux et FFST du client IBM MQ : ecrits dans le repertoire courant par defaut, donc
# dans une couche en lecture seule ou dans le volume applicatif. Rediriges vers un
# repertoire dedie, inscriptible par l'uid 1001 (cf. PaymentMessagesApplication).
RUN mkdir -p /app/logs && chown 1001:0 /app/logs && chmod g+rwX /app/logs

USER 1001

# MaxRAMPercentage : sans lui la JVM dimensionne le tas a 1/4 de la memoire du conteneur
# et laisse les 3/4 restants inutilises. G1 par defaut : a ces tailles de tas (< 4 Go) il
# tient les memes pauses que ZGC pour une empreinte memoire inferieure ; basculer via
# JAVA_OPTS ("-XX:+UseZGC -XX:+ZGenerational") si le p99 de latence l'exige.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# `exec` : la JVM devient PID 1 et recoit SIGTERM, condition de l'arret propre du
# conteneur d'ecoute JMS (sinon le shell l'absorbe et Docker tue apres le delai de grace).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
