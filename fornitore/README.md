# Il finto fornitore esterno (passo 6.8b)

Questa cartella **non fa parte di shows-service**: e' il catalogo di un
fornitore terzo, servito da un nginx dentro `docker-compose.yml` come servizio
`catalog-provider`. Serve a poter provare il client Feign senza dipendere da
internet ne' da un servizio vero.

    http://catalog-provider/catalog.json     dentro la rete di compose
    http://localhost:8090/catalog.json       dal portatile

Il file e' fatto per raccontare tre cose in una chiamata:

- **3 film sono gia' nel nostro catalogo** (Dune, Oppenheimer, Perfect Days):
  l'import non li duplica;
- **5 sono nuovi**: quelli entrano;
- **"Anora" e' scritto DUE VOLTE**, ed e' voluto. Un fornitore esterno non ci
  garantisce nessuna unicita': se `RemoteCatalogImporter` si fidasse, il secondo
  "Anora" violerebbe il vincolo `uk_movies_title` e farebbe fallire tutto
  l'import. Per questo il controllo dei titoli gia' visti cresce mentre si
  scorre la lista.

Quindi la prima chiamata risponde `importati: 5`, `giaPresenti: 4` (3 nostri +
1 doppione interno), `totaleDalFornitore: 9`. La seconda: `importati: 0`.

Per cambiare lo scenario in aula basta modificare questo file: nginx lo serve
da un volume, senza ricostruire nessuna immagine. Un `docker compose restart
catalog-provider` non serve nemmeno.
