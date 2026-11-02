"""
Which embedder to use, and which model. Read from environment variables so a
company can point this at their own model without touching the code.
"""

import os

from app.embedder import Embedder, SentenceTransformerEmbedder

# Env var EMBEDDER picks the provider; EMBEDDING_MODEL picks the model.
# Defaults give a small, fast, offline model that needs no setup.
EMBEDDER = os.getenv("EMBEDDER", "sentence-transformers")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "all-MiniLM-L6-v2")


def build_embedder() -> Embedder:
    """Create the embedder named by the config. One place to add new ones."""
    if EMBEDDER == "sentence-transformers":
        return SentenceTransformerEmbedder(EMBEDDING_MODEL)

    # Future: "ollama", "company-api", etc. fit here.
    raise ValueError(f"Unknown EMBEDDER: {EMBEDDER!r}")
