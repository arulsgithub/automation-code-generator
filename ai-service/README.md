# AI Service

The Python half of the Test Automation Generator. Owns embeddings (and, later,
the LLM). The Java application calls it over HTTP.

## Run it

```bash
cd ai-service
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt    # first run downloads the model (~400MB)

uvicorn app.main:app --reload --port 8000
```

First start takes a minute while the model downloads. After that it is instant
and fully offline.

## Try it

```bash
curl http://localhost:8000/health

curl -X POST http://localhost:8000/embed \
  -H "Content-Type: application/json" \
  -d '{"texts": ["log in as admin", "delete the account"]}'
```

Interactive API docs (FastAPI gives these for free): http://localhost:8000/docs

## Choosing a different model

Set environment variables before starting — no code change:

```bash
EMBEDDING_MODEL=all-mpnet-base-v2 uvicorn app.main:app --port 8000
```

A company can point `EMBEDDER` / `EMBEDDING_MODEL` at their own model. New
providers (Ollama, a hosted API) are added in `app/config.py` and `app/embedder.py`.

## Layout

| File | Job |
|---|---|
| `app/main.py` | The endpoints: `/embed`, `/health` |
| `app/embedder.py` | The Embedder slot + the sentence-transformers default |
| `app/config.py` | Which model to load, read from env vars |
| `requirements.txt` | Dependencies |
