/*
 * Il front-end del G2, in JavaScript e niente altro: nessun framework, nessuna
 * dipendenza da scaricare. Serve a vedere una cosa sola, e a vederla bene:
 * un form che parla con il microservizio via HTTP.
 *
 * PERCHE' QUESTI FILE STANNO IN src/main/resources/static
 * Spring Boot serve da solo tutto quello che trova in static/, quindi le pagine
 * arrivano dallo STESSO host e porta dell'API (localhost:8081). Stessa origine
 * = nessun CORS da configurare. Se domani il front-end diventa un progetto a
 * parte su un'altra porta, il browser bloccherebbe la fetch e servirebbe un
 * @CrossOrigin (o meglio, una WebMvcConfigurer): al G6, con sei servizi, si
 * vedra' perche' non e' un dettaglio.
 *
 * Un solo file per due pagine: in fondo c'e' un piccolo dispatcher che guarda
 * quali elementi esistono nel DOM e attacca solo il codice che serve.
 */

// Relativo, non "http://localhost:8081/shows": l'URL assoluto sarebbe da
// riscrivere a ogni cambio di porta o di ambiente. Stessa origine, stesso path.
const API = '/shows';

/** Mostra un messaggio d'errore leggibile nel riquadro #errore. */
function mostraErrore(testo) {
  const box = document.getElementById('errore');
  box.textContent = testo;
  box.hidden = false;
}

/**
 * Il corpo di una risposta di errore, come testo leggibile.
 *
 * Oggi le rotte rispondono in due formati diversi: il 404 e' una stringa secca
 * (@ExceptionHandler restituisce e.getMessage()), il 400 e' il JSON d'errore di
 * serie di Boot. Al G4, con ProblemDetail, diventeranno lo stesso formato e
 * questa funzione si semplifichera'.
 */
async function messaggioDiErrore(risposta) {
  const testo = await risposta.text();
  if (!testo) {
    return `HTTP ${risposta.status} ${risposta.statusText}`;
  }
  try {
    const json = JSON.parse(testo);
    return `HTTP ${risposta.status} — ${json.message || json.detail || json.error || testo}`;
  } catch {
    // non era JSON: e' il messaggio in chiaro del nostro ExceptionHandler
    return `HTTP ${risposta.status} — ${testo}`;
  }
}

/* ------------------------------------------------------------------ *
 *  Pagina 1 — il form
 * ------------------------------------------------------------------ */

function abilitaForm(form) {
  form.addEventListener('submit', async (evento) => {
    // Senza questo il browser fa la POST da solo, ricarica la pagina e la
    // chiamata AJAX non parte nemmeno: e' LA riga da non dimenticare.
    evento.preventDefault();

    document.getElementById('errore').hidden = true;
    const bottone = document.getElementById('invia');
    bottone.disabled = true;               // niente doppio invio: la POST non e' idempotente

    try {
      const dati = new FormData(form);

      // <input type="datetime-local"> produce "2027-05-01T21:30", senza secondi.
      // LocalDateTime li accetta anche cosi', ma aggiungerli rende esplicito
      // che il formato atteso e' ISO-8601 e non una data localizzata.
      let startTime = dati.get('startTime');
      if (startTime.length === 16) {
        startTime += ':00';
      }

      const corpo = {
        // il film si indica per id, e deve esistere: la FK non perdona
        movie: { id: Number(dati.get('movieId')) },
        startTime: startTime,
        basePrice: Number(dati.get('basePrice')),
        totalSeats: Number(dati.get('totalSeats'))
        // id e availableSeats NON si inviano: non sono dati del client, sono
        // conseguenze. L'id lo assegna il database, i posti disponibili li
        // calcola il dominio a partire dai posti totali.
      };

      const risposta = await fetch(API, {
        method: 'POST',
        // Senza questo header Spring risponde 415 Unsupported Media Type:
        // @RequestBody non sa che sta leggendo del JSON.
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(corpo)
      });

      // fetch NON lancia sui 4xx e 5xx: una risposta d'errore e' comunque una
      // risposta arrivata. Va guardato response.ok, sempre.
      if (!risposta.ok) {
        mostraErrore(await messaggioDiErrore(risposta));
        return;
      }

      // 201 Created: l'id della risorsa nuova sta nell'header Location, non
      // solo nel corpo. E' meta' del significato di "created", e leggerlo di
      // la' e' il modo giusto di usare la risposta.
      const location = risposta.headers.get('Location');
      const creato = await risposta.json();
      const id = location ? location.split('/').pop() : creato.id;

      window.location.href = `successo.html?id=${encodeURIComponent(id)}`;

    } catch (errore) {
      // Qui ci si arriva solo se la richiesta non e' partita affatto:
      // servizio spento, rete giu', DNS. Non per un 400.
      mostraErrore('Impossibile contattare il servizio. E\' avviato su ' +
                   window.location.origin + ' ?\n\n' + errore.message);
    } finally {
      bottone.disabled = false;
    }
  });
}

/* ------------------------------------------------------------------ *
 *  Pagina 2 — il riepilogo
 * ------------------------------------------------------------------ */

async function mostraDettaglio() {
  const id = new URLSearchParams(window.location.search).get('id');
  if (!id) {
    mostraErrore('Manca il parametro ?id= nell\'indirizzo: apri la pagina dal form.');
    return;
  }

  try {
    const risposta = await fetch(`${API}/${encodeURIComponent(id)}`, {
      headers: { 'Accept': 'application/json' }
    });

    if (!risposta.ok) {
      mostraErrore(await messaggioDiErrore(risposta));
      return;
    }

    const show = await risposta.json();

    document.getElementById('d-id').textContent = show.id;
    // il titolo puo' mancare: vedi la nota in fondo al file
    document.getElementById('d-film').textContent =
        show.movie.title ? `${show.movie.title} (id ${show.movie.id})` : `id ${show.movie.id}`;
    document.getElementById('d-inizio').textContent =
        new Date(show.startTime).toLocaleString('it-IT');
    document.getElementById('d-prezzo').textContent =
        Number(show.basePrice).toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
    document.getElementById('d-posti').textContent =
        `${show.availableSeats} disponibili su ${show.totalSeats}`;
    document.getElementById('d-serale').textContent = show.eveningShow ? 'si' : 'no';

    document.getElementById('dettaglio').hidden = false;

  } catch (errore) {
    mostraErrore('Impossibile contattare il servizio: ' + errore.message);
  }
}

/*
 * NOTA sul titolo del film che puo' mancare.
 * La risposta del POST rimanda indietro il film cosi' come e' arrivato dalla
 * richiesta ({id: 1, title: null}), perche' oggi il service salva lo Show senza
 * rileggere il film dal catalogo. Questa pagina non ne soffre, perche' fa una
 * GET /shows/{id} e quindi legge il dato vero dal database. Quando il service
 * risolvera' il film con MovieRepository, anche la risposta del POST sara'
 * completa e il ramo "titolo mancante" qui sopra si potra' togliere.
 */

/* ------------------------------------------------------------------ *
 *  Quale delle due pagine siamo?
 * ------------------------------------------------------------------ */

const form = document.getElementById('form-show');
if (form) {
  abilitaForm(form);
} else if (document.getElementById('dettaglio')) {
  mostraDettaglio();
}
