from qdrant_client import QdrantClient
from qdrant_client.models import PointStruct, VectorParams, Distance, Filter, FieldCondition, MatchValue
from app.core.config import settings
from app.services.llm_service import llm_service
import uuid
import json

# Valid memory categories for the Neural Blueprint
MEMORY_CATEGORIES = [
    "preference",        # Library choices, tool preferences, coding style
    "tech_stack",        # Languages, frameworks, versions, environment
    "project_decision",  # Architecture choices, design rationale
    "pain_point",        # Recurring errors, known frustrations, env issues
    "personal",          # Life facts, job, name, dietary, etc.
    "learning_style",    # Depth preference (math vs API), pace, modality
    "environment",       # OS, IDE, hardware constraints, deployment
    "general",           # Catch-all for anything that doesn't fit above
]

class MemoryService:
    def __init__(self):
        if not settings.QDRANT_URL:
            print("⚠️ MemoryService Disabled: QDRANT_URL not found in .env")
            self.client = None
            return

        self.client = QdrantClient(
            url=settings.QDRANT_URL,
            api_key=settings.QDRANT_API_KEY,
            timeout=60.0
        )
        self.collection_name = "user_memories"
        self._ensure_collection_exists()

    def _ensure_collection_exists(self):
        if not self.client: return
        try:
            if not self.client.collection_exists(self.collection_name):
                print("🧠 Memory: Creating new Qdrant collection...")
                self.client.create_collection(
                    collection_name=self.collection_name,
                    vectors_config=VectorParams(size=settings.EMBEDDING_DIMENSIONS, distance=Distance.COSINE)
                    # gemini-embedding-001 produces 3072-dimensional vectors
                    vectors_config=VectorParams(size=3072, distance=Distance.COSINE)
                )
        except Exception as e:
            print(f"❌ Qdrant Connection Error: {e}")

    # --- CORE: Save a single memory with full metadata ---
    def save_memory(self, text: str, user_id: str = "unknown", category: str = "general", tags: list = None):
        if not self.client: return {"status": "error", "reason": "No DB Connection"}

        category = category if category in MEMORY_CATEGORIES else "general"
        tags = tags or []

        summary = llm_service.summarize_memory(text)
        if not summary:
            return {"status": "skipped", "reason": "Summarization failed"}

        vector = llm_service.get_embedding(summary)
        if not vector:
            return {"status": "skipped", "reason": "Embedding failed"}

        try:
            self.client.upsert(
                collection_name=self.collection_name,
                points=[
                    PointStruct(
                        id=str(uuid.uuid4()),
                        vector=vector,
                        payload={
                            "user_id": user_id,
                            "original_text": text,
                            "summary": summary,
                            "category": category,
                            "tags": tags,
                        }
                    )
                ]
            )
            print(f"🧠 Memory Saved [{category}]: {summary}")
            return {"status": "saved", "summary": summary, "category": category, "tags": tags}
        except Exception as e:
            print(f"❌ Memory Save Failed: {e}")
            return {"status": "error", "reason": str(e)}

    # --- SEARCH: User-scoped, optionally category-filtered ---
    def search_memory(self, query: str, user_id: str = None, category: str = None, limit: int = 5):
        if not self.client: return []

        vector = llm_service.get_embedding(query)
        if not vector: return []

        # Build Qdrant filter for user_id and optional category
        conditions = []
        if user_id:
            conditions.append(FieldCondition(key="user_id", match=MatchValue(value=user_id)))
        if category and category in MEMORY_CATEGORIES:
            conditions.append(FieldCondition(key="category", match=MatchValue(value=category)))

        query_filter = Filter(must=conditions) if conditions else None

        try:
            results = self.client.search(
                collection_name=self.collection_name,
                query_vector=vector,
                query_filter=query_filter,
                limit=limit
            )
            return [
                {
                    "summary": hit.payload.get("summary", ""),
                    "category": hit.payload.get("category", "general"),
                    "tags": hit.payload.get("tags", []),
                    "score": hit.score
                }
                for hit in results
            ]
        except Exception as e:
            print(f"❌ Memory Search Failed: {e}")
            return []

    # --- PROFILE RETRIEVAL: Get all memories for user, grouped by category ---
    def get_user_profile(self, user_id: str) -> dict:
        """Retrieves the full Neural Blueprint for a user grouped by category."""
        if not self.client: return {}

        profile = {cat: [] for cat in MEMORY_CATEGORIES}
        try:
            # Scroll through all user memories (no vector needed)
            results, _ = self.client.scroll(
                collection_name=self.collection_name,
                scroll_filter=Filter(must=[
                    FieldCondition(key="user_id", match=MatchValue(value=user_id))
                ]),
                limit=200
            )
            for point in results:
                cat = point.payload.get("category", "general")
                profile.setdefault(cat, []).append(point.payload.get("summary", ""))

            # Remove empty categories
            return {k: v for k, v in profile.items() if v}
        except Exception as e:
            print(f"❌ Profile Retrieval Failed: {e}")
            return {}

    # --- BACKGROUND SYNC: Extract implicit signals from a conversation ---
    def extract_and_save_implicit_profile(self, user_id: str, conversation: str):
        """
        The "Dark Matter Extractor." Runs after each chat interaction.
        Analyzes the full conversation for implicit signals the user never
        explicitly asked to save: tech stack, preferences, pain points, etc.
        """
        if not self.client: return []

        signals = llm_service.extract_profile_signals(conversation)
        if not signals:
            return []

        saved = []
        for signal in signals:
            content = signal.get("content", "")
            category = signal.get("category", "general")
            tags = signal.get("tags", [])
            if content:
                result = self.save_memory(content, user_id=user_id, category=category, tags=tags)
                if result.get("status") == "saved":
                    saved.append(result)

        if saved:
            print(f"🧠 Background Sync: Committed {len(saved)} implicit memories for {user_id}")
        return saved

memory_service = MemoryService()