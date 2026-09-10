# PASSO 5.1 — build multi-stage.
#
# Due stadi: il primo compila (e porta con se' Maven, il JDK e il repository
# .m2: quasi un gigabyte), il secondo contiene solo il jar e una JRE.
# Nell'immagine finale finisce SOLO il secondo stadio.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# COPIARE PRIMA IL POM E POI IL SRC NON E' UN VEZZO.
# Docker mette in cache ogni istruzione: se il pom non cambia, lo strato con
# le dipendenze scaricate viene riusato e la build successiva parte da qui.
# Copiando tutto insieme, ogni modifica a una riga di Java riscaricherebbe
# mezzo internet.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# -DskipTests: i test girano nella pipeline, non nella build dell'immagine.
# I test *IT usano Testcontainers, che qui dentro pretenderebbe Docker
# DENTRO Docker: una complicazione che non serve.
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# UTENTE NON ROOT. Di default un container gira come root: se qualcuno esce
# dal processo, esce da root. Due righe, e il rischio si riduce di molto.
#     docker exec shows-service whoami   ->   cinema
RUN addgroup -S cinema && adduser -S cinema -G cinema
USER cinema

COPY --from=build /app/target/*.jar app.jar

# EXPOSE e' documentazione, non apre niente: e' -p a pubblicare la porta.
EXPOSE 8081

# MaxRAMPercentage e non -Xmx: la JVM legge il limite di memoria del container
# e ne usa una percentuale. Con un -Xmx fisso, cambiando i limiti del
# container la JVM non se ne accorge e viene uccisa dall'OOM killer.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
