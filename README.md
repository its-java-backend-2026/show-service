# shows-service

Catalogo film e spettacoli, disponibilità e prenotazione dei posti.

Microservizio Spring Boot del corso **ITS Java Backend 2026**. Tre strati
(web → service → repository), PostgreSQL con migrazioni Flyway, e dal **G5**
containerizzato: parte con un solo comando.

## Requisiti

Con Docker, dal **G5**, non serve nient'altro:

- **Docker Desktop** (o Docker Engine + Compose v2)

Per lavorare fuori dal container servono anche:

- **JDK 21** o superiore
- un **PostgreSQL** raggiungibile (dal G2) — Maven no: c'è il wrapper (`mvnw`)

## Avvio — un solo comando

```bash
docker compose up --build
```

Costruisce l'immagine, tira su PostgreSQL e il finto fornitore del catalogo,
aspetta che siano **sani** e solo allora avvia il servizio. Poi:

| | |
|---|---|
| Swagger UI | <http://localhost:8081/swagger-ui.html> |
| Spettacoli | <http://localhost:8081/shows> |

Se la porta 8081 è già occupata (tipicamente da un'istanza avviata dall'IDE),
o la 5432 da un PostgreSQL locale, si cambiano **senza toccare i file**:

```bash
SHOWS_PORT=8181 SHOWS_DB_PORT=5433 docker compose up --build
```

Per fermare:

```bash
docker compose down       # ferma, i dati restano
docker compose down -v    # ferma E CANCELLA i dati
```

### Avvio senza Docker

Serve un PostgreSQL su `localhost:5432` (database `shows_db`, utente e
password `cinema`); lo schema lo crea Flyway all'avvio.

```bash
./mvnw spring-boot:run        # macOS / Linux
.\mvnw.cmd spring-boot:run    # Windows
```

Test:

```bash
./mvnw test      # 10 test, non serve Docker
./mvnw verify    # + i test *IT su un PostgreSQL vero (Testcontainers)
```

## Documentazione delle API

Con il servizio avviato:

| | |
|---|---|
| Swagger UI | <http://localhost:8081/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8081/v3/api-docs> |

Le rotte sono documentate con le annotazioni `@Operation` / `@ApiResponses`
direttamente su `ShowController`.

## Endpoint

| Metodo | Rotta | Cosa fa |
|---|---|---|
| `GET` | `/shows` | Elenca gli spettacoli, ordinati per orario di inizio |
| `GET` | `/shows/{id}` | Un singolo spettacolo — `404` se non esiste |
| `POST` | `/shows` | Crea uno spettacolo — `201` con header `Location` |
| `PUT` | `/shows/{id}` | Aggiorna **orario e prezzo** (film e posti totali non si toccano) |
| `DELETE` | `/shows/{id}` | Elimina — `204`, nessun corpo |
| `POST` | `/shows/{id}/reserve?quantity=N` | Riserva N posti. Non idempotente |
| `POST` | `/shows/{id}/release?quantity=N` | Rilascia N posti, mai oltre i posti totali |

Le rotte di servizio (`/actuator/**`) non sono in questa tabella perché non
fanno parte dell'API: sono spiegate in
[L'Actuator](#lactuator-le-rotte-per-chi-gestisce-il-servizio).

Il file [`http/shows.http`](http/shows.http) contiene tutte le richieste già
pronte, casi d'errore compresi. Si esegue con:

- **VS Code** + estensione [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) (gratis)
- **IntelliJ IDEA Ultimate**, client HTTP integrato — *non* la Community, che non ce l'ha

## Struttura

```
src/main/java/it/its/cinema/showsservice/
├── web/         ShowController          parla HTTP, e solo HTTP
├── service/     ShowService             le regole del caso d'uso
├── repository/  ShowRepository          l'interfaccia
│                InMemoryShowRepository  l'unica implementazione di oggi
├── domain/      Show, Movie             le regole di dominio + le eccezioni
└── config/      OpenApiConfig           l'intestazione della Swagger UI
```

Due regole che il codice rispetta e che vale la pena notare:

- **Nessuno strato salta un livello.** Il controller conosce `ShowService` e
  ignora che esista un repository. Se lì comparisse un repository, i tre strati
  sarebbero già due.
- **Le regole stanno nel dominio.** `reserveSeats` e `releaseSeats` sono metodi
  di `Show`, non del service: chiunque abbia in mano uno `Show` non può portarlo
  in uno stato assurdo.

L'iniezione è **da costruttore** (`@RequiredArgsConstructor` di Lombok su campi
`final`), mai `@Autowired` sul campo.

## Cos'è un ponteggio

Parti scritte per essere buttate via nelle tappe successive:

- `InMemoryShowRepository` — **sparisce al G2**, sostituito da PostgreSQL. Al
  suo posto arrivano Spring Data JPA e le migrazioni Flyway, e con loro anche i
  dati di esempio.
- Il formato degli errori — al G1 il `404` restituiva una stringa. **Fatto al
  G4**: ora ogni errore e' un `ProblemDetail` (RFC 7807), prodotto da
  `GestoreErrori`.
- `GET /shows` non è paginata — **lo diventa al G3**.

Il service dipende da `ShowRepository`, l'interfaccia, non dalla sua
implementazione: è per questo che al G2 il service non cambia di una riga.

## Limiti noti

Da sistemare nelle prossime tappe, elencati qui perché non siano una sorpresa:

- ~~Solo `ShowNotFoundException` viene tradotta in uno status HTTP.~~
  **Risolto al G4**: `GestoreErrori` è un `@RestControllerAdvice` che copre
  tutte le eccezioni di dominio, in un formato solo.
- `POST /shows` salva lo `Show` deserializzato così com'è: `availableSeats` non
  viene ricalcolato da `totalSeats`, quindi uno spettacolo appena creato nasce
  con **0 posti disponibili** se il client non li invia.
- Nel `POST` di `http/shows.http` il film ha il campo `duration`, ma `Movie`
  espone `durationMinutes`: Jackson ignora la proprietà sconosciuta e la durata
  arriva a **`0`**.
- ~~I dati vivono in memoria.~~ **Risolto al G2**: stanno in PostgreSQL, e con
  `docker compose down` (senza `-v`) sopravvivono al riavvio.

## G4 — DTO, validazione, errori uniformi

Cosa è cambiato, e dove guardare:

| Passo | Cosa | File |
|---|---|---|
| 4.1 | DTO come **record**, non entità, sul confine HTTP | `web/dto/` |
| 4.2 | Mapper scritti a mano, niente MapStruct | `web/mapper/` |
| 4.3 | Jackson 3: `tools.jackson`, non `com.fasterxml` | `catalog/CatalogImporter` |
| 4.4 | Catalogo film importato da JSON all'avvio, **idempotente** | `catalog/CatalogImporter`, `resources/catalog.json` |
| 4.5 | Bean Validation sui DTO, con `@Valid` sul controller | `web/dto/`, i due controller |
| 4.6 | `@RestControllerAdvice` + `ProblemDetail` | `web/GestoreErrori` |
| 4.7 | OpenAPI (già dal G1, springdoc **3.x**: la 2.x non parte su Boot 4) | `pom.xml` |
| 4.8 | Test della fetta web con `@WebMvcTest` | `ShowControllerTest` |

**Il contratto HTTP è cambiato in modo incompatibile.** Chi ha script del G3:

- `POST /shows` vuole `"movieId": 1`, non più `"movie": {"id": 1}`;
- la risposta di uno spettacolo espone `movieId` e `movieTitle` appiattiti,
  non più l'oggetto `movie` annidato;
- `id`, `version` e `availableSeats` inviati dal client non danno errore:
  vengono ignorati, perché i DTO in ingresso non li hanno;
- `PUT /movies/{id}` non rifiuta più con `400` un `id` discordante nel corpo,
  per la stessa ragione;
- ogni errore è un `ProblemDetail`, non più una stringa secca.

Il catalogo si carica da `src/main/resources/catalog.json`, e il percorso si
cambia senza toccare il codice:

```bash
CINEMA_CATALOGFILE=file:/percorso/catalog.json ./mvnw spring-boot:run
```

L'import gira a **ogni** avvio e non duplica niente: al secondo avvio i log
dicono `catalogo gia' allineato`.

## G5 — Docker: parte con un solo comando

Cosa è cambiato, e dove guardare:

| Passo | Cosa | File |
|---|---|---|
| 5.1 | `Dockerfile` **multi-stage**: compila in uno stadio, spedisce l'altro | `Dockerfile` |
| 5.2 | `.dockerignore`: `target/` e `.git/` non entrano nel contesto di build | `.dockerignore` |
| 5.3 | l'immagine finale è **402 MB**, quella di build 829 MB | — |
| 5.4 | profilo `docker` + liveness/readiness distinte | `application.yaml`, `pom.xml` |
| 5.5 | `docker compose`: servizio + database, con `healthcheck` | `docker-compose.yml` |
| 5.6 | `docker compose down -v && docker compose up --build` da zero | — |

Tre scelte che vale la pena difendere:

- **`COPY pom.xml` prima di `COPY src`.** Docker mette in cache ogni
  istruzione: se il pom non cambia, lo strato con le dipendenze scaricate
  viene riusato. Copiando tutto insieme, ogni modifica a una riga di Java
  riscaricherebbe mezzo internet.
- **`USER cinema`.** Di default un container gira come root: se qualcuno esce
  dal processo, esce da root. Due righe, e il rischio si riduce di molto
  (`docker exec shows-service whoami` → `cinema`).
- **`-XX:MaxRAMPercentage=75.0`, non `-Xmx`.** La JVM legge il limite di
  memoria del container e ne usa una percentuale. Con un `-Xmx` fisso,
  cambiando i limiti del container la JVM non se ne accorge e viene uccisa
  dall'OOM killer.

### I due errori che fanno tutti al primo `docker compose up`

**`localhost`.** Dentro la rete di compose `localhost` è *il container
stesso*: l'indirizzo del database è **il nome del servizio**, `shows-db`. Il
messaggio che si legge (`Connection refused`) non aiuta a capirlo.

**`depends_on` da solo.** Aspetta che il container sia **avviato**, non che il
servizio dentro sia **pronto**. PostgreSQL impiega qualche secondo ad
accettare connessioni: nel frattempo il servizio parte, Flyway fallisce e il
container muore. È l'`healthcheck` con `pg_isready` — più
`condition: service_healthy` — a rendere deterministico l'avvio.

### L'Actuator: le rotte per chi gestisce il servizio

`spring-boot-starter-actuator` aggiunge delle rotte sotto `/actuator` che non
servono ai clienti dell'API, ma a chi il servizio lo **gestisce**. Una
dipendenza sola, nessun codice da scrivere.

Il problema che risolve è: **come fa un programma a chiedere a un altro
programma se sta bene?** Non un umano che legge i log — un programma: Docker,
un load balancer, Kubernetes al G9. Serve una rotta HTTP che risponda in modo
prevedibile, ed è quella che interroga l'`healthcheck` del compose:

```yaml
test: ["CMD-SHELL", "wget -q -O- http://localhost:8081/actuator/health/readiness | grep -q UP"]
```

Da quella risposta Docker decide se scrivere `healthy` o `unhealthy` in
`docker compose ps` — e `depends_on: condition: service_healthy` decide se far
partire il servizio. Senza Actuator, quella riga non avrebbe niente da
chiamare: si ripiegherebbe su "il processo java esiste?", che è una domanda
molto più povera. Un servizio può avere il processo vivo e il database
staccato.

#### `/actuator/health` non è un `return "OK"`

Actuator raccoglie un **health indicator** per ogni pezzo di infrastruttura
che riconosce sul classpath, e li compone. Siccome c'è un `DataSource`, arriva
gratis un indicatore che fa una query di prova sul database:

```json
{
  "status": "UP",
  "components": {
    "db":        { "status": "UP", "details": { "database": "PostgreSQL" } },
    "diskSpace": { "status": "UP" },
    "ping":      { "status": "UP" }
  }
}
```

Lo stato complessivo è il **peggiore** dei componenti: se PostgreSQL cade,
`/actuator/health` diventa `DOWN` con `503`, senza una riga di codice. È la
differenza fra "il mio processo è acceso" e "il mio processo è in grado di
lavorare".

#### Liveness e readiness sono due domande diverse

| Rotta | Domanda | Chi la fa, e cosa fa se la risposta è no |
|---|---|---|
| `/actuator/health/liveness` | il processo è **vivo**? | l'orchestratore: **riavvia** il container |
| `/actuator/health/readiness` | posso **ricevere traffico**? | il bilanciatore: **smette di mandare richieste**, non riavvia |

Un servizio che sta ancora applicando le migrazioni di Flyway è **vivo ma non
pronto**. Confonderle porta a due guasti opposti: usare la liveness dove serve
la readiness manda richieste a chi non sa ancora rispondere; usare la readiness
come liveness fa **riavviare in loop** un servizio sano ogni volta che il
database ha un singhiozzo — e il riavvio non guarisce il database.

Le due sotto-rotte non esistono per default: le accende `probes.enabled: true`,
che sta nel profilo `docker`. Fuori dal container `/actuator/health` risponde,
`/actuator/health/readiness` dà `404`. È voluto: servono solo dove c'è
qualcosa che le interroga. Le richieste già pronte sono in fondo a
[`http/shows.http`](http/shows.http).

#### Le altre rotte, e perché sono chiuse

Actuator ne porta molte: `/actuator/metrics` (memoria, thread, richieste HTTP,
pool di connessioni), `/actuator/env` (tutta la configurazione),
`/actuator/loggers` (per cambiare il livello di log **a runtime**, senza
riavviare), `/actuator/flyway` (le migrazioni applicate), `/actuator/beans`,
`/actuator/heapdump`.

Sono utilissime, e sono anche una **superficie d'attacco**: `/env` mostra la
configurazione, `/heapdump` scarica la memoria del processo — dentro c'è tutto,
password comprese. Per questo Boot espone via HTTP solo `health` di default, e
qui siamo espliciti:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info      # solo queste due, il resto non e' raggiungibile
```

Stessa logica per `show-details: always`: va bene su una rete interna e in
aula, dove vedere `db: DOWN` fa capire subito il guasto. Esposto su internet
racconta a un estraneo che database usi. Al **G9**, con il gateway e
l'observability, queste rotte si spostano su una porta separata
(`management.server.port`) e si tengono fuori dalla rete pubblica.

### Configurazione: solo variabili d'ambiente

Nessun valore di ambiente è scritto nel codice. Le URL sono
`${VARIABILE:default}` **dal G2**, e oggi si capisce perché: il default fa
funzionare `./mvnw spring-boot:run` sul portatile, la variabile fa funzionare
la stessa identica immagine dentro compose.

| Variabile | Default | A cosa serve |
|---|---|---|
| `SHOWS_DB_URL` | `jdbc:postgresql://localhost:5432/shows_db` | il database |
| `SHOWS_DB_USER` / `SHOWS_DB_PASSWORD` | `cinema` / `cinema` | le credenziali |
| `SHOWS_PORT` | `8081` | la porta **sull'host** |
| `SHOWS_DB_PORT` | `5432` | la porta del database sull'host |
| `CINEMA_CATALOGFILE` | `classpath:catalog.json` | da dove si legge il catalogo |
| `SQL_LOG_LEVEL` | `INFO` nel container | il log delle query (passo 3.2) |

Il `.env` **non va nel repository** (è nel `.gitignore`); ci va
[`.env.example`](.env.example), che dice quali variabili servono senza
rivelarne i valori.

Il catalogo dei film si popola all'avvio da `src/main/resources/catalog.json`,
e l'import è **idempotente**: al secondo avvio i log dicono
`catalogo gia' allineato`. Per usare un file diverso senza ricostruire
l'immagine basta montarlo e puntarci:

```bash
CINEMA_CATALOGFILE=file:/etc/cinema/catalog.json docker compose up
```

### Consegna G5 — verificata

> *"Il progetto parte sul portatile di un compagno con un solo comando."*

| Controllo | Esito |
|---|---|
| `docker build` | immagine `cinema/shows-service:1.0`, **402 MB** |
| `shows-db` / `shows-service` | `Up (healthy)` entrambi |
| `GET /actuator/health/{liveness,readiness}` | `200`, `UP` |
| `GET /shows` | `200`, 3 spettacoli |
| catalogo importato da JSON | 7 film, e al riavvio `catalogo gia' allineato` |
| Swagger UI | `200` |
| gira come utente non root | `cinema` |

Il compose oggi sta **dentro** questo repository (`build: .`), così chi clona
la cartella ha tutto. Al **G6**, quando accanto a `shows-service` nasceranno
gli altri servizi, salirà di un livello e lo stesso file li orchestrerà tutti.

## Stack

Spring Boot 4.1.1 · Java 21 · PostgreSQL 17 · Flyway · springdoc-openapi 3.1.0 ·
Jackson 3 · Bean Validation · Lombok · Maven · Docker + Compose v2
