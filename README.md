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
| `POST` | `/shows/{id}/reserve` | Riserva N posti. Corpo: `{ "sagaId": "...", "quantity": N }`. **Idempotente sul `sagaId`** (G8) |
| `POST` | `/shows/{id}/release` | Rilascia N posti, mai oltre i posti totali. Stesso corpo |
| `POST` | `/movies/importa-da-fornitore` | Importa il catalogo di un fornitore esterno (Feign) — `503` se il fornitore non risponde |

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
├── web/         ShowController, MovieController   parlano HTTP, e solo HTTP
│   ├── dto/     record in ingresso e in uscita    il contratto pubblico (G4)
│   ├── mapper/  ShowMapper, MovieMapper           entita' <-> DTO, a mano
│   └──           GestoreErrori                     ogni errore, un ProblemDetail
├── service/     ShowService, MovieService         le regole del caso d'uso
├── repository/  ShowRepository, MovieRepository   Spring Data JPA (G2)
├── domain/      Show, Movie + le eccezioni        le regole di dominio
├── catalog/     CatalogImporter                   il catalogo da file, all'avvio
│                RemoteCatalogImporter             il catalogo dal fornitore (G6)
├── client/      CatalogClient, MovieJson          cio' che chiamiamo fuori (G6)
└── config/      OpenApiConfig                     l'intestazione della Swagger UI
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

## G6 (anticipato) — OpenFeign: chiamare un servizio che non e' nostro

Il passo **6.8b** di `PASSI.txt`, portato avanti al G5 perche' è la prima volta
che questo servizio dipende da qualcun altro — e tutto il Blocco B parte da
lì. `shows-service` scarica il catalogo film da un **fornitore esterno** e
importa solo i titoli che non ha.

| Cosa | File |
|---|---|
| il client dichiarativo | `client/CatalogClient` |
| la copia locale del contratto remoto | `client/MovieJson` |
| chiamata, import e traduzione degli errori | `catalog/RemoteCatalogImporter` |
| `503` invece di `500` | `web/GestoreErrori`, `domain/CatalogProviderUnavailableException` |
| il finto fornitore (nginx) | `docker-compose.yml`, `fornitore/catalog.json` |
| il test, senza rete | `RemoteCatalogImporterTest` |

### Feign in tre righe

```java
@FeignClient(name = "catalog-provider", url = "${cinema.catalog.remote-url}")
public interface CatalogClient {
    @GetMapping(value = "/catalog.json", produces = "application/json")
    List<MovieJson> scaricaCatalogo();
}
```

Nessuna implementazione: Feign genera il proxy a runtime. Si dichiara **cosa**
si chiama, non **come** — è la differenza con `RestClient` (passo 6.6), che nel
corso useremo fra i nostri servizi. Tre cose da non sbagliare:

- **`@EnableFeignClients`** sulla classe main. Senza, le interfacce non vengono
  scansionate e l'avvio muore con `No qualifying bean of type 'CatalogClient'`,
  che non nomina Feign da nessuna parte.
- **`url` e non solo `name`**: `url` è un indirizzo fisso dalla
  configurazione. Senza `url`, Feign tratterebbe `name` come nome logico da
  risolvere con un service discovery (Eureka), che qui non c'è.
- **il release train**: `spring-cloud-dependencies` **2025.1.3** è la riga del
  `pom.xml` che decide tutto. Ogni train è allineato a una versione di Boot;
  con quello sbagliato il contesto non parte.

### Una nota onesta: Feign è in maintenance mode

**Spring Cloud OpenFeign non riceve funzionalità nuove dal 2022** (train
2022.0): solo correzioni di bug e patch di sicurezza. Funziona, è supportato,
la base installata è enorme — lo incontrerete di sicuro nel codice esistente,
ed è per questo che sta in questo corso. Ma su un progetto **nuovo**, oggi,
non è più la scelta predefinita.

Il motivo per cui si sceglieva Feign era lo stile dichiarativo, e quello stile
adesso ce l'ha Spring core: si chiamano **HTTP Interfaces**, e in Spring
Framework 7 — cioè in questo stesso progetto, senza aggiungere niente — la
stessa interfaccia si scrive così:

```java
@HttpExchange(url = "/catalog.json", accept = "application/json")
public interface CatalogClient {
    @GetExchange
    List<MovieJson> scaricaCatalogo();
}
```

e si registra con una riga, al posto di `@EnableFeignClients`:

```java
@ImportHttpServices(group = "catalogo", types = CatalogClient.class)
```

Cosa si guadagna: sparisce **tutto** `spring-cloud-dependencies` dal `pom.xml`
— niente BOM, niente release train da tenere allineato a Boot, che è la terza
delle tre trappole qui sopra. Sotto il proxy c'è un `RestClient` normale,
quindi i timeout si configurano dove li mette il passo 6.6.

Verificabile senza fidarsi:

```bash
unzip -l ~/.m2/repository/org/springframework/spring-web/7.0.9/spring-web-7.0.9.jar \
  | grep -E "HttpExchange|HttpServiceProxyFactory|ImportHttpServices"
```

**Perché allora il codice qui sotto usa ancora Feign?** Perché il passo 6.8b lo
prescrive, e perché vedere Feign una volta ha valore pratico: è quello che
troverete nei progetti che vi capiteranno fra le mani. La regola da portarsi a
casa non è «usate Feign», è:

> Lo stile dichiarativo conviene quando le rotte remote sono **molte e
> stabili** e gli errori si trattano **tutti allo stesso modo**. Oggi quello
> stile si ottiene con le HTTP Interfaces di Spring; Feign lo si tiene dove
> c'è già.

### I timeout non sono tuning

```yaml
spring.cloud.openfeign.client.config.default:
  connectTimeout: 2000
  readTimeout: 5000
```

Il default di Feign è generoso (60s in lettura). Un fornitore **lento** tiene
occupati i nostri thread per un minuto a richiesta e ci trascina giù con sé: fa
più danni di un fornitore **spento**, che almeno risponde subito "connessione
rifiutata". È la stessa lezione del passo 6.6, e al G7 diventerà un circuit
breaker.

### Il guasto di qualcun altro non è un nostro 500

Feign lancia eccezioni *unchecked* — `FeignException` per una risposta di
errore, `RetryableException` (che la estende) per timeout e connessione
rifiutata. Se risalgono fino al controller, il catch-all di `GestoreErrori` le
racconta al client come `500`: "colpa nostra, un bug". `RemoteCatalogImporter`
le traduce in un'eccezione di dominio, e l'advice la trasforma in **`503` con
`Retry-After: 30`**.

Verificato: con `docker compose stop catalog-provider`,
`POST /movies/importa-da-fornitore` risponde

```json
{ "type": "https://cinema.its.it/errori/fornitore-non-disponibile",
  "title": "Fornitore non disponibile", "status": 503 }
```

e nel frattempo `GET /movies` continua a rispondere `200`, con il container
ancora `healthy`. **Una dipendenza esterna giù non ci porta giù**: è il motivo
per cui il database sta nella health e il fornitore no.

### Il finto fornitore

Una lezione su come si chiama un servizio remoto ha bisogno di un servizio
remoto, e dipendere da un'API su internet in aula significa dipendere dal wifi.
Nel compose c'è un `nginx:alpine` che serve `./fornitore/catalog.json`:

```
http://catalog-provider/catalog.json     dentro la rete di compose
http://localhost:8090/catalog.json       dal portatile, per provare dall'IDE
```

Il file è montato da un volume: si modifica e la chiamata successiva vede i
film nuovi, senza ricostruire nessuna immagine. Contiene 9 film, di cui 3 già
nostri e uno **scritto due volte di proposito** — un fornitore esterno non
garantisce nessuna unicità, e senza il controllo dei titoli già visti il
secondo `"Anora"` violerebbe `uk_movies_title` e farebbe fallire tutto
l'import. Esito atteso della prima chiamata:

```json
{ "importati": 5, "giaPresenti": 4, "totaleDalFornitore": 9 }
```

della seconda: `"importati": 0`. Le richieste pronte sono in fondo a
[`http/movies.http`](http/movies.http).

### Due importatori, di proposito

`CatalogImporter` (passo 4.4) **non è stato toccato**: legge un file e gira una
volta all'avvio. Il catalogo remoto ha il suo, `RemoteCatalogImporter`, che
chiama un servizio e gira quando lo chiede un umano.

Le due classi si somigliano nella parte di fusione, e la somiglianza è un costo
accettato con gli occhi aperti. Un importatore condiviso avrebbe legato il
codice del G4 — che funziona, è spiegato e sta in un commit — alle esigenze di
una sorgente che si comporta in modo diverso: un file c'è o non c'è, un servizio
va in timeout, risponde `500`, o manda dati sporchi (il doppione `"Anora"`). Il
giorno che una delle due sorgenti cambia regola, l'altra non se ne accorge.

Ognuno mappa il proprio contratto sul dominio: `FilmDelCatalogo` per il file,
`MovieJson` per il fornitore.

## G6 — `reserve` e `release` diventano passi di una saga

Dal G6 `shows-service` non è più solo: accanto nascono `pricing-service` (8082)
e `booking-service` (8083), ognuno nel suo repository. Il sistema completo lo
monta `cinema-deploy`.

Qui cambia una cosa sola, ed è il **passo 6.4**: i parametri di `reserve` e
`release` passano dalla query string a un **corpo JSON**.

```diff
- POST /shows/1/reserve?quantity=2
+ POST /shows/1/reserve
+ Content-Type: application/json
+
+ { "sagaId": "3f2a1b9c-6d4e-4a7b-9c2f-1e8d0a5b7c31", "quantity": 2 }
```

### Perché un corpo, e non un parametro in più

Insieme alla quantità deve viaggiare il **`sagaId`**, e un identificativo di
correlazione appiccicato alla query string è la strada più breve per vederlo
finire nei log di accesso di ogni proxy che la richiesta attraversa.

### Cos'è il `sagaId`, oggi che la saga ancora non c'è

È l'identificativo dell'**intera operazione di acquisto**: lo genera
`booking-service` una volta sola e lo ripete identico a ogni passo, verso ogni
servizio.

Oggi serve a leggere i log. Tre processi, tre flussi di log, e una stringa
comune per ricucire la storia di *un* acquisto:

```bash
docker compose logs | grep 3f2a1b9c-
```

Dal G8 è diventato di più: la chiave con cui riconoscere che un `release` è la
compensazione di **quel** `reserve`, e la chiave dell'**idempotenza** — la
stessa saga che ritenta non scala i posti due volte. Vedi la sezione G8 qui
sotto.

Si chiede **già oggi**, anche se oggi lo scriviamo solo nel log: aggiungerlo al
contratto dopo significherebbe cambiarlo mentre due servizi lo stanno già
usando.

### `shows-service` non sa che esiste una saga

Ed è il punto. Riceve un identificativo opaco, lo scrive nei log e lo dimentica:
nessuna logica di coordinamento, nessuna conoscenza di chi lo sta chiamando. Il
coordinamento è un problema di chi coordina.

È la stessa ragione per cui `/release` è esistita per due giornate senza che
nessuno la chiamasse: è la compensazione di `/reserve`, e chi decide *quando*
compensare sta altrove. Dal G8 la chiama `BookingSaga`.

---

## G8 — `reserve` e `release` diventano idempotenti (passo 8.3)

Fino al G7 ripetere una riserva scalava i posti una seconda volta. Non era un
difetto nascosto: era scritto nei commenti di `booking-service`, ed era il
motivo per cui `POST /shows/{id}/reserve` era l'unica chiamata **senza
`@Retry`**.

Il problema è il timeout. *Timeout* non vuol dire «non è arrivata», vuol dire
**«non so se è arrivata»** — e il più delle volte la richiesta era arrivata
benissimo, era la risposta a essersi persa.

### La tabella, ed è tutto qui

```sql
CREATE TABLE show_operations (
    saga_id        VARCHAR(64) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,      -- RESERVE | RELEASE
    show_id        BIGINT      NOT NULL,
    quantity       INTEGER     NOT NULL,
    ...
    CONSTRAINT uk_show_operations_saga_tipo UNIQUE (saga_id, operation_type)
);
```

**Il tipo sta nella chiave**, e non è un dettaglio: la stessa saga passa di qui
**due volte** quando compensa. Con il solo `saga_id` nel vincolo, il rilascio
verrebbe scambiato per una riserva ripetuta e non verrebbe eseguito mai — i
posti resterebbero bloccati per sempre, che è esattamente il guasto che la saga
esiste per evitare.

Il vincolo `UNIQUE` è la protezione **vera**. Il controllo in Java
(`existsBySagaIdAndOperationType`) copre il caso normale — un retry a distanza
di secondi — senza far scrivere niente al database; due chiamate simultanee lo
superano entrambe, e a decidere è il vincolo, che è l'unico posto condiviso da
tutte le istanze del servizio.

### Due scritture, una transazione (passo 8.6)

Disponibilità e riga dell'operazione si scrivono **insieme**, e per questo
stanno in `PostiSaga`, un bean a parte:

- se passasse solo la disponibilità, la richiesta ritentata scalerebbe di nuovo
  — non troverebbe nessuna operazione registrata;
- se passasse solo l'operazione, i posti non tornerebbero mai indietro.

È in una classe separata perché `@Transactional` funziona **solo attraverso il
proxy di Spring**: un metodo privato di `ShowService` non aprirebbe nessuna
transazione, in silenzio. È lo stesso avvertimento che questo servizio porta in
cima a `ShowService` dal G3.

### La prova

```bash
curl -s localhost:8081/shows/1 | grep -o '"availableSeats":[0-9]*'

for i in 1 2; do
  curl -s -o /dev/null -X POST localhost:8081/shows/1/reserve \
       -H 'Content-Type: application/json' \
       -d '{"sagaId":"prova-idempotenza","quantity":2}'
done

# due posti in meno, non quattro
curl -s localhost:8081/shows/1 | grep -o '"availableSeats":[0-9]*'
```

`IdempotenzaSagaIT` lo verifica con PostgreSQL vero, compreso il caso che si
nota di meno: *«rilascia 2 posti» eseguito due volte ne rilascia 2, non 4*.
Dei posti in regalo non fanno arrabbiare nessuno subito — si scopre la sera
della proiezione, con due persone sulla stessa fila.

### Cosa NON è cambiato

`shows-service` continua a non sapere che esiste una saga. Riceve un
identificativo opaco, lo usa come chiave di idempotenza e lo dimentica: non
conosce i passi, non conosce l'ordine, non sa che esistono un pagamento e dei
punti fedeltà. Un partecipante deve solo saper fare — e **rifare senza danni**
— la sua parte.

## Stack

Spring Boot 4.1.1 · Java 21 · PostgreSQL 17 · Flyway · springdoc-openapi 3.1.0 ·
Jackson 3 · Bean Validation · Lombok · Maven · Docker + Compose v2 ·
Spring Cloud OpenFeign 5.0.3 (train 2025.1.3)
