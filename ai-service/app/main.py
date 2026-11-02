"""
The AI service. One real endpoint for now: POST /embed.

    Java  ──►  POST /embed {"texts": ["log in as admin"]}
    Java  ◄──  {"vectors": [[0.9, 0.1, ...]], "dimensions": 384}
"""

from fastapi import FastAPI
from pydantic import BaseModel

from app.config import EMBEDDER, EMBEDDING_MODEL, build_embedder

app = FastAPI(title="Test Automation Generator — AI Service")

# Build the embedder once, when the service starts, not on every request.
# (Loading the model takes a few seconds; we do it a single time.)
embedder = build_embedder()


# ---- request / response shapes -------------------------------------------
# pydantic validates incoming JSON automatically. (Like a DTO in Java, but it
# also checks types and rejects bad input with a 422 before your code runs.)

class EmbedRequest(BaseModel):
    texts: list[str]


class EmbedResponse(BaseModel):
    vectors: list[list[float]]
    dimensions: int


# ---- endpoints ------------------------------------------------------------

@app.get("/health")
def health() -> dict:
    """Quick check that the service is up and which model it loaded."""
    return {
        "status": "ok",
        "embedder": EMBEDDER,
        "model": EMBEDDING_MODEL,
        "dimensions": embedder.dimensions(),
    }


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    """Turn each text into a vector."""
    vectors = embedder.embed(request.texts)
    return EmbedResponse(vectors=vectors, dimensions=embedder.dimensions())
