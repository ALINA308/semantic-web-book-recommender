# semantic-web-book-recommender

**Authors:** Pasaroiu Mihai-Octavian, Bilciurescu Alina-Elena
**Repo:** https://github.com/ALINA308/semantic-web-book-recommender

## Contributions

**Bilciurescu Alina-Elena — Ex 1-6**
- Wrote the RDF/XML dataset and the OWL ontology for the recommendation domain
- Built the RDF/XML upload + graph visualisation page
- Implemented book listing, detail pages and add/edit flows via Apache Jena
- Authored the 5 SPARQL queries and captured the GraphDB/Protégé screenshots

**Pasaroiu Mihai-Octavian — Ex 7 (chatbot)**
- Floating chat widget present on every page, with context-aware starters
- RAG pipeline: pgvector store ingested from `books.rdf` (books + users)
- Stateless identity-aware multi-turn — the LLM asks "Are you Alice or Bob?" when needed
- Deterministic author+theme matcher (e.g. `Frank Herbert` + `Science Fiction` → `Dune`)
