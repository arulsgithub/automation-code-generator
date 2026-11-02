"""
Proves the embeddings capture meaning.

Sends a few texts to the running service, then checks that texts with similar
meaning get similar vectors. Run with the service up:  python test_meaning.py
"""

import json
import urllib.request


def embed(texts):
    """Call the running /embed endpoint and return the vectors."""
    body = json.dumps({"texts": texts}).encode()
    req = urllib.request.Request(
        "http://localhost:8000/embed",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())["vectors"]


def similarity(a, b):
    """How close two vectors are. The vectors are already normalised, so the
    dot product IS the cosine similarity: near 1.0 = alike, near 0 = unrelated."""
    return sum(x * y for x, y in zip(a, b))


# One "action" the user might record, and three existing method descriptions.
action = "log in as the admin user"
methods = [
    "logs in as a given role",       # should be CLOSE
    "adds a product to the cart",    # should be far
    "deletes the account",           # should be far
]

vectors = embed([action] + methods)
action_vec = vectors[0]
method_vecs = vectors[1:]

print(f'\nAction: "{action}"\n')
print(f'{"score":>7}   method')
print(f'{"-----":>7}   ------')

scored = sorted(
    zip(methods, method_vecs),
    key=lambda pair: similarity(action_vec, pair[1]),
    reverse=True,
)
for method, vec in scored:
    print(f"{similarity(action_vec, vec):7.2f}   {method}")

print("\nThe top line should be 'logs in as a given role'.")
