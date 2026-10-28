# Imagem única: front (React) compilado dentro do back-end (Spring Boot), servidos na mesma origem.
# Build:  docker build -t crm-loja .
# Rodar:  docker run -p 8080:8080 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... -e JWT_SECRET=... crm-loja

# 1) Front-end
FROM node:22-alpine AS front
WORKDIR /front
RUN corepack enable
COPY crm-loja-frontend/package.json crm-loja-frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY crm-loja-frontend/ ./
# Mesma origem da API: chamadas vão para /auth, /crm… sem o prefixo /api do Vite.
ENV VITE_API_BASE=""
RUN pnpm build

# 2) Back-end, já com as telas em classpath:/static
FROM eclipse-temurin:21-jdk AS back
WORKDIR /back
COPY crm-loja-backend/.mvn .mvn
COPY crm-loja-backend/mvnw crm-loja-backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY crm-loja-backend/src src
COPY --from=front /front/dist src/main/resources/static
RUN ./mvnw -B -q -DskipTests package

# 3) Execução: só o JRE e o .jar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 crm
COPY --from=back /back/target/crm-loja-backend-0.1.0.jar app.jar
USER crm
EXPOSE 8080
# Cabe nos 512 MB do plano grátis: heap em 55% (o resto fica para metaspace, código e threads),
# JIT só no nível 1 (menos memória e subida mais rápida com pouca CPU), pilhas menores.
# ExitOnOutOfMemoryError: se faltar memória, o log mostra isso claramente em vez de travar.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m -Xss512k -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
