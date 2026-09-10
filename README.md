# shows-service

Catalogo film e spettacoli, disponibilità e prenotazione dei posti.

Microservizio Spring Boot del corso **ITS Java Backend 2026**. È il servizio del
**G1**: tre strati (web → service → repository), dati in memoria, nessun
database. Serve a fissare le regole di struttura prima che il progetto cresca.

## Requisiti

- **JDK 21** o superiore
- Nessuna installazione di Maven: c'è il wrapper (`mvnw`)

## Avvio

```bash
./mvnw spring-boot:run        # macOS / Linux
.\mvnw.cmd spring-boot:run    # Windows
```

Il servizio parte sulla porta **8081** e carica tre spettacoli di esempio
(`InMemoryShowRepository.datiDiEsempio`).

```bash
curl http://localhost:8081/shows
```

Test:

```bash
./mvnw test
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
- I dati vivono in memoria: **a ogni riavvio si riparte dai tre spettacoli di
  esempio.**

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

## Stack

Spring Boot 4.1.1 · Java 21 · springdoc-openapi 3.1.0 · Jackson 3 · Bean Validation ·
Lombok · Maven
