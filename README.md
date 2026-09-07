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
- Il formato degli errori — oggi il `404` restituisce una stringa. **Al G4**
  diventa `ProblemDetail` (RFC 9457).
- `GET /shows` non è paginata — **lo diventa al G3**.

Il service dipende da `ShowRepository`, l'interfaccia, non dalla sua
implementazione: è per questo che al G2 il service non cambia di una riga.

## Limiti noti

Da sistemare nelle prossime tappe, elencati qui perché non siano una sorpresa:

- Solo `ShowNotFoundException` viene tradotta in uno status HTTP.
  `NotEnoughSeatsException` e `IllegalArgumentException` risalgono non gestite e
  diventano **`500`**, mentre Swagger promette `409` e `400`. È esattamente il
  problema che l'`@ExceptionHandler` globale del G4 risolve.
- `POST /shows` salva lo `Show` deserializzato così com'è: `availableSeats` non
  viene ricalcolato da `totalSeats`, quindi uno spettacolo appena creato nasce
  con **0 posti disponibili** se il client non li invia.
- Nel `POST` di `http/shows.http` il film ha il campo `duration`, ma `Movie`
  espone `durationMinutes`: Jackson ignora la proprietà sconosciuta e la durata
  arriva a **`0`**.
- I dati vivono in memoria: **a ogni riavvio si riparte dai tre spettacoli di
  esempio.**

## Stack

Spring Boot 4.1.1 · Java 21 · springdoc-openapi 3.1.0 · Lombok · Maven
