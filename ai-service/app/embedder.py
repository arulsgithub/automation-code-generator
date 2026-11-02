"""
The Embedder: turns text into a vector of numbers.

This file defines a slot (Embedder) and one thing that fills it
(SentenceTransformerEmbedder). Later, Ollama or a company's own model can
fill the same slot without changing anything that calls it.
"""

from abc import ABC, abstractmethod


class Embedder(ABC):
    """The slot. Anything that can turn text into a vector fits here.

    (In Java this would be an `interface`. In Python, an abstract base
    class with @abstractmethod does the same job.)
    """

    @abstractmethod
    def embed(self, texts: list[str]) -> list[list[float]]:
        """Take a list of texts, return one vector per text."""
        raise NotImplementedError

    @abstractmethod
    def dimensions(self) -> int:
        """How many numbers are in each vector."""
        raise NotImplementedError


class SentenceTransformerEmbedder(Embedder):
    """The default. Loads a small model and runs it inside this service."""

    def __init__(self, model_name: str):
        # Imported here, not at the top, so the service can start even while
        # the (large) library is still downloading on first run.
        from sentence_transformers import SentenceTransformer

        self._model = SentenceTransformer(model_name)

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors = self._model.encode(texts, normalize_embeddings=True)
        # The model returns a NumPy array; convert to plain Python lists so
        # it can be sent back as JSON.
        return [vector.tolist() for vector in vectors]

    def dimensions(self) -> int:
        return self._model.get_sentence_embedding_dimension()
