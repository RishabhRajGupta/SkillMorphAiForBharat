"""
Tests for the Neural Blueprint / Enhanced Memory System.
Tests cover:
1. MemoryService - save_memory() with user_id, categories, tags
2. MemoryService - search_memory() with user-scoped filtering
3. MemoryService - get_user_profile() grouped retrieval
4. MemoryService - extract_and_save_implicit_profile() dark matter extraction
5. LLMService - extract_profile_signals() prompt & parsing
6. Agent Tool - save_memory_note with categories & tags
7. Agent Tool - search_user_context proactive retrieval
8. FastAPI Endpoints - /memory/, /memory/search, /memory/profile
9. Background Sync - /agent/chat triggers implicit profiling
10. Tool Registration - new tools in ALL_TOOLS
"""
import json
import sys
import os

# CRITICAL: Set env vars BEFORE any app imports
os.environ.setdefault("AWS_ACCESS_KEY_ID", "fake_aws_key")
os.environ.setdefault("AWS_SECRET_ACCESS_KEY", "fake_aws_secret")
os.environ.setdefault("AWS_REGION", "us-east-1")
os.environ.setdefault("BEDROCK_MODEL_ID", "anthropic.claude-3-5-sonnet-20241022-v2:0")
os.environ.setdefault("BEDROCK_EMBED_MODEL_ID", "amazon.titan-embed-text-v2:0")
os.environ.setdefault("NEO4J_URI", "bolt://localhost:7687")
os.environ.setdefault("NEO4J_USER", "neo4j")
os.environ.setdefault("NEO4J_PASSWORD", "testpassword")
os.environ.setdefault("QDRANT_URL", "http://localhost:6333")
os.environ.setdefault("QDRANT_API_KEY", "test_qdrant_key")

import pytest
from unittest.mock import patch, MagicMock, AsyncMock, call

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

# Pre-import modules for @patch resolution
import app.services.memory_service
import app.services.llm_service
import app.agent.tools
import app.main


# ============================================================
# TEST 1: MemoryService - save_memory() with metadata
# ============================================================

class TestMemoryServiceSave:
    """Tests that save_memory correctly stores user_id, category, and tags."""

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_save_memory_with_user_id_and_category(self, mock_qdrant_cls, mock_llm):
        """Memory payloads must include user_id, category, and tags."""
        from app.services.memory_service import MemoryService

        mock_client = MagicMock()
        mock_client.collection_exists.return_value = True
        mock_qdrant_cls.return_value = mock_client

        mock_llm.summarize_memory.return_value = "Prefers Hilt over Koin for DI"
        mock_llm.get_embedding.return_value = [0.1] * 768

        svc = MemoryService()
        result = svc.save_memory(
            "I prefer Hilt over Koin because of compile-time safety",
            user_id="user_42",
            category="preference",
            tags=["Hilt", "Koin", "Android"]
        )

        assert result["status"] == "saved"
        assert result["category"] == "preference"
        assert "Hilt" in result["tags"]

        # Verify the Qdrant upsert payload
        upsert_call = mock_client.upsert.call_args
        point = upsert_call.kwargs["points"][0]
        assert point.payload["user_id"] == "user_42"
        assert point.payload["category"] == "preference"
        assert "Hilt" in point.payload["tags"]
        print("✅ Test 1a PASSED: save_memory includes user_id, category, tags")

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_save_memory_invalid_category_falls_back(self, mock_qdrant_cls, mock_llm):
        """Invalid categories should default to 'general'."""
        from app.services.memory_service import MemoryService

        mock_client = MagicMock()
        mock_client.collection_exists.return_value = True
        mock_qdrant_cls.return_value = mock_client
        mock_llm.summarize_memory.return_value = "Some fact"
        mock_llm.get_embedding.return_value = [0.1] * 768

        svc = MemoryService()
        result = svc.save_memory("test", user_id="u1", category="nonexistent_cat")

        assert result["category"] == "general"
        print("✅ Test 1b PASSED: Invalid category falls back to 'general'")

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_save_memory_no_client_returns_error(self, mock_qdrant_cls, mock_llm):
        """Should return error when Qdrant client is None."""
        from app.services.memory_service import MemoryService

        # Simulate QDRANT_URL being empty
        with patch.object(app.services.memory_service.settings, "QDRANT_URL", ""):
            svc = MemoryService()

        result = svc.save_memory("test fact", user_id="u1")
        assert result["status"] == "error"
        assert "No DB Connection" in result["reason"]
        print("✅ Test 1c PASSED: No client returns error gracefully")

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_save_memory_embedding_failure_skips(self, mock_qdrant_cls, mock_llm):
        """Should skip saving when embedding fails."""
        from app.services.memory_service import MemoryService

        mock_client = MagicMock()
        mock_client.collection_exists.return_value = True
        mock_qdrant_cls.return_value = mock_client
        mock_llm.summarize_memory.return_value = "Summary"
        mock_llm.get_embedding.return_value = []  # Empty = failure

        svc = MemoryService()
        result = svc.save_memory("test", user_id="u1")

        assert result["status"] == "skipped"
        mock_client.upsert.assert_not_called()
        print("✅ Test 1d PASSED: Embedding failure skips save gracefully")


# ============================================================
# TEST 2: MemoryService - search_memory() with user-scoped filtering
# ============================================================

class TestMemoryServiceSearch:
    """Tests for user-scoped and category-filtered memory search."""

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_search_filters_by_user_id(self, mock_qdrant_cls, mock_llm):
        """Search must filter by user_id via Qdrant query_filter."""
        from app.services.memory_service import MemoryService

        mock_client = MagicMock()
        mock_client.collection_exists.return_value = True
        mock_qdrant_cls.return_value = mock_client
        mock_llm.get_embedding.return_value = [0.1] * 768

        # Simulate search results
        mock_hit = MagicMock()
        mock_hit.payload = {"summary": "Uses Neo4j", "category": "tech_stack", "tags": ["Neo4j"]}
        mock_hit.score = 0.92
        mock_client.search.return_value = [mock_hit]

        svc = MemoryService()
        results = svc.search_memory("database", user_id="user_42")

        assert len(results) == 1
        assert results[0]["summary"] == "Uses Neo4j"
        assert results[0]["category"] == "tech_stack"
        assert results[0]["score"] == 0.92

        # Verify user_id filter was passed
        search_call = mock_client.search.call_args
        query_filter = search_call.kwargs.get("query_filter") or search_call[1].get("query_filter")
        assert query_filter is not None
        print("✅ Test 2a PASSED: search_memory filters by user_id")

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_search_filters_by_category(self, mock_qdrant_cls, mock_llm):
        """Search with category filter should include category condition."""
        from app.services.memory_service import MemoryService

        mock_client = MagicMock()
        mock_client.collection_exists.return_value = True
        mock_qdrant_cls.return_value = mock_client
        mock_llm.get_embedding.return_value = [0.1] * 768
        mock_client.search.return_value = []

        svc = MemoryService()
        svc.search_memory("docker gpu", user_id="u1", category="pain_point")

        search_call = mock_client.search.call_args
        query_filter = search_call.kwargs.get("query_filter") or search_call[1].get("query_filter")
        # Must have 2 conditions: user_id + category
        assert query_filter is not None
        assert len(query_filter.must) == 2
        print("✅ Test 2b PASSED: search_memory filters by user_id + category")

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_search_no_client_returns_empty(self, mock_qdrant_cls, mock_llm):
        """Should return empty list when Qdrant is unavailable."""
        from app.services.memory_service import MemoryService

        with patch.object(app.services.memory_service.settings, "QDRANT_URL", ""):
            svc = MemoryService()

        results = svc.search_memory("anything", user_id="u1")
        assert results == []
        print("✅ Test 2c PASSED: No client returns empty list")


# ============================================================
# TEST 3: MemoryService - get_user_profile()
# ============================================================

class TestMemoryServiceProfile:
    """Tests for the user profile (Neural Blueprint) retrieval."""

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_get_user_profile_groups_by_category(self, mock_qdrant_cls, mock_llm):
        """Profile should return memories grouped by category."""
        from app.services.memory_service import MemoryService

        mock_client = MagicMock()
        mock_client.collection_exists.return_value = True
        mock_qdrant_cls.return_value = mock_client

        # Simulate scroll results
        point1 = MagicMock()
        point1.payload = {"summary": "Uses FastAPI", "category": "tech_stack"}
        point2 = MagicMock()
        point2.payload = {"summary": "Prefers Hilt", "category": "preference"}
        point3 = MagicMock()
        point3.payload = {"summary": "Gemini rate limits", "category": "pain_point"}

        mock_client.scroll.return_value = ([point1, point2, point3], None)

        svc = MemoryService()
        profile = svc.get_user_profile("user_42")

        assert "tech_stack" in profile
        assert "preference" in profile
        assert "pain_point" in profile
        assert "Uses FastAPI" in profile["tech_stack"]
        assert "Prefers Hilt" in profile["preference"]
        # Empty categories should be excluded
        assert "personal" not in profile
        print("✅ Test 3a PASSED: get_user_profile groups by category, excludes empties")

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_get_user_profile_no_client(self, mock_qdrant_cls, mock_llm):
        """Should return empty dict when client unavailable."""
        from app.services.memory_service import MemoryService

        with patch.object(app.services.memory_service.settings, "QDRANT_URL", ""):
            svc = MemoryService()

        profile = svc.get_user_profile("u1")
        assert profile == {}
        print("✅ Test 3b PASSED: No client returns empty profile")


# ============================================================
# TEST 4: MemoryService - extract_and_save_implicit_profile()
# ============================================================

class TestDarkMatterExtractor:
    """Tests for the background implicit profiling."""

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_extract_saves_multiple_signals(self, mock_qdrant_cls, mock_llm):
        """Should call save_memory for each extracted signal."""
        from app.services.memory_service import MemoryService

        mock_client = MagicMock()
        mock_client.collection_exists.return_value = True
        mock_qdrant_cls.return_value = mock_client

        # LLM returns 2 profile signals
        mock_llm.extract_profile_signals.return_value = [
            {"content": "Uses Python 3.11 with FastAPI", "category": "tech_stack", "tags": ["Python", "FastAPI"]},
            {"content": "Prefers Neo4j over SQL", "category": "preference", "tags": ["Neo4j", "database"]}
        ]
        mock_llm.summarize_memory.return_value = "Test summary"
        mock_llm.get_embedding.return_value = [0.1] * 768

        svc = MemoryService()
        saved = svc.extract_and_save_implicit_profile("user_42", "User: I'm using Python 3.11\nAssistant: Great!")

        assert len(saved) == 2
        mock_llm.extract_profile_signals.assert_called_once()
        assert mock_client.upsert.call_count == 2
        print("✅ Test 4a PASSED: Dark matter extractor saves multiple signals")

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_extract_empty_conversation_saves_nothing(self, mock_qdrant_cls, mock_llm):
        """Should save nothing when LLM finds no signals."""
        from app.services.memory_service import MemoryService

        mock_client = MagicMock()
        mock_client.collection_exists.return_value = True
        mock_qdrant_cls.return_value = mock_client

        mock_llm.extract_profile_signals.return_value = []

        svc = MemoryService()
        saved = svc.extract_and_save_implicit_profile("u1", "User: Hello\nAssistant: Hi there!")

        assert saved == []
        mock_client.upsert.assert_not_called()
        print("✅ Test 4b PASSED: Trivial conversation produces no memory commits")

    @patch("app.services.memory_service.llm_service")
    @patch("app.services.memory_service.QdrantClient")
    def test_extract_no_client_returns_empty(self, mock_qdrant_cls, mock_llm):
        """Should return empty when Qdrant is unavailable."""
        from app.services.memory_service import MemoryService

        with patch.object(app.services.memory_service.settings, "QDRANT_URL", ""):
            svc = MemoryService()

        saved = svc.extract_and_save_implicit_profile("u1", "nothing")
        assert saved == []
        print("✅ Test 4c PASSED: No client returns empty on extraction")


# ============================================================
# TEST 5: LLMService - extract_profile_signals()
# ============================================================

class TestLLMProfileExtraction:
    """Tests for the LLM-based profile signal extraction."""

    def _make_service(self, mock_client):
        from app.services.llm_service import LLMService
        svc = LLMService()
        svc.client = mock_client
        return svc

    @patch("app.services.llm_service.boto3")
    def test_extract_returns_valid_signals(self, mock_boto3):
        """Should parse valid JSON signals from LLM response."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        
        response_text = json.dumps([
            {"content": "Uses Fedora with WSL2", "category": "environment", "tags": ["Fedora", "WSL2"]},
            {"content": "Prefers manual backprop over wrappers", "category": "learning_style", "tags": ["ML", "backpropagation"]}
        ])
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": response_text}]}}
        }

        svc = self._make_service(mock_client)
        signals = svc.extract_profile_signals("User: I use Fedora with WSL2\nAssistant: Got it!")

        assert len(signals) == 2
        assert signals[0]["category"] == "environment"
        assert "Fedora" in signals[0]["tags"]
        assert signals[1]["category"] == "learning_style"
        print("✅ Test 5a PASSED: extract_profile_signals returns valid signals")

    @patch("app.services.llm_service.boto3")
    def test_extract_handles_empty_array(self, mock_boto3):
        """Should return empty list when LLM returns []."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": "[]"}]}}
        }

        svc = self._make_service(mock_client)
        signals = svc.extract_profile_signals("User: Hi\nAssistant: Hello!")

        assert signals == []
        print("✅ Test 5b PASSED: Empty conversation yields empty signals")

    @patch("app.services.llm_service.boto3")
    def test_extract_handles_bad_json(self, mock_boto3):
        """Should return empty list on malformed JSON."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": "This is not valid JSON {{"}]}}
        }

        svc = self._make_service(mock_client)
        signals = svc.extract_profile_signals("some conversation")

        assert signals == []
        print("✅ Test 5c PASSED: Malformed JSON returns empty gracefully")

    @patch("app.services.llm_service.boto3")
    def test_extract_filters_missing_content(self, mock_boto3):
        """Should filter out signals missing 'content' field."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        
        response_text = json.dumps([
            {"content": "Valid signal", "category": "tech_stack", "tags": []},
            {"category": "preference", "tags": ["missing content field"]},
            {"content": "", "category": "general", "tags": []}
        ])
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": response_text}]}}
        }

        svc = self._make_service(mock_client)
        signals = svc.extract_profile_signals("conversation text")

        # Only the first signal has non-empty content
        assert len(signals) == 1
        assert signals[0]["content"] == "Valid signal"
        print("✅ Test 5d PASSED: Signals with missing/empty content filtered out")

    @patch("app.services.llm_service.boto3")
    def test_summarize_memory_preserves_details(self, mock_boto3):
        """Enhanced summarize_memory should preserve technical terms."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": "Prefers Hilt DI over Koin for compile-time safety in Android projects"}]}}
        }

        svc = self._make_service(mock_client)
        summary = svc.summarize_memory("I always use Hilt instead of Koin because compile-time checks catch DI errors early in my Android projects")

        assert "Hilt" in summary
        assert len(summary.split()) <= 30  # Should be concise
        print("✅ Test 5e PASSED: summarize_memory preserves technical terms")


# ============================================================
# TEST 6: Agent Tool - save_memory_note (enhanced)
# ============================================================

class TestSaveMemoryNoteTool:
    """Tests for the enhanced save_memory_note tool with categories."""

    @patch("app.agent.tools.memory_service")
    def test_tool_saves_with_category_and_tags(self, mock_mem):
        """Tool should pass category and parsed tags to memory service."""
        from app.agent.tools import save_memory_note

        mock_mem.save_memory.return_value = {"status": "saved", "summary": "Uses Python 3.11"}

        result = save_memory_note.invoke({
            "user_id": "user_42",
            "content": "I use Python 3.11 with FastAPI",
            "category": "tech_stack",
            "tags": "Python,FastAPI,backend"
        })

        mock_mem.save_memory.assert_called_once_with(
            "I use Python 3.11 with FastAPI",
            user_id="user_42",
            category="tech_stack",
            tags=["Python", "FastAPI", "backend"]
        )
        assert "tech_stack" in result
        print("✅ Test 6a PASSED: save_memory_note passes category + parsed tags")

    @patch("app.agent.tools.memory_service")
    def test_tool_saves_with_defaults(self, mock_mem):
        """Tool should work with only content (category=general, tags=[])."""
        from app.agent.tools import save_memory_note

        mock_mem.save_memory.return_value = {"status": "saved", "summary": "Some fact"}

        result = save_memory_note.invoke({
            "user_id": "u1",
            "content": "I am vegan"
        })

        mock_mem.save_memory.assert_called_once_with(
            "I am vegan",
            user_id="u1",
            category="general",
            tags=[]
        )
        print("✅ Test 6b PASSED: save_memory_note works with defaults")


# ============================================================
# TEST 7: Agent Tool - search_user_context (new)
# ============================================================

class TestSearchUserContextTool:
    """Tests for the proactive memory retrieval tool."""

    @patch("app.agent.tools.memory_service")
    def test_search_returns_matches(self, mock_mem):
        """Should return matching memories as JSON."""
        from app.agent.tools import search_user_context

        mock_mem.search_memory.return_value = [
            {"summary": "Uses Neo4j over SQL", "category": "preference", "tags": ["Neo4j"], "score": 0.95}
        ]

        result = search_user_context.invoke({
            "user_id": "user_42",
            "query": "database preferences"
        })

        parsed = json.loads(result)
        assert len(parsed["matches"]) == 1
        assert parsed["matches"][0]["summary"] == "Uses Neo4j over SQL"
        print("✅ Test 7a PASSED: search_user_context returns matching memories")

    @patch("app.agent.tools.memory_service")
    def test_search_with_category_filter(self, mock_mem):
        """Should pass category filter to memory service."""
        from app.agent.tools import search_user_context

        mock_mem.search_memory.return_value = []

        search_user_context.invoke({
            "user_id": "u1",
            "query": "GPU issues",
            "category": "pain_point"
        })

        mock_mem.search_memory.assert_called_once_with(
            "GPU issues", user_id="u1", category="pain_point", limit=5
        )
        print("✅ Test 7b PASSED: search_user_context passes category filter")

    @patch("app.agent.tools.memory_service")
    def test_search_empty_returns_no_context_note(self, mock_mem):
        """Should return a 'no context' note when nothing found."""
        from app.agent.tools import search_user_context

        mock_mem.search_memory.return_value = []

        result = search_user_context.invoke({
            "user_id": "u1",
            "query": "obscure topic"
        })

        parsed = json.loads(result)
        assert parsed["matches"] == []
        assert "No prior context" in parsed["note"]
        print("✅ Test 7c PASSED: Empty results returns informative note")


# ============================================================
# TEST 8: FastAPI Endpoints - /memory/ routes
# ============================================================

class TestMemoryEndpoints:
    """Tests for the memory API endpoints."""

    @patch("app.main.memory_service")
    @patch("app.main.graph_db")
    def test_save_memory_endpoint_passes_user_id(self, mock_db, mock_mem):
        """POST /memory/ should pass x-user-id to memory_service."""
        mock_db.connect = MagicMock()
        mock_db.close = MagicMock()
        mock_mem.save_memory.return_value = {"status": "saved", "summary": "test"}

        from fastapi.testclient import TestClient
        from app.main import app

        client = TestClient(app)
        response = client.post(
            "/memory/",
            json={"text": "I use Fedora Linux"},
            headers={"x-user-id": "user_42"}
        )

        assert response.status_code == 200
        mock_mem.save_memory.assert_called_once_with("I use Fedora Linux", user_id="user_42")
        print("✅ Test 8a PASSED: /memory/ endpoint passes user_id")

    @patch("app.main.memory_service")
    @patch("app.main.graph_db")
    def test_search_memory_endpoint_with_category(self, mock_db, mock_mem):
        """GET /memory/search should pass query + category + user_id."""
        mock_db.connect = MagicMock()
        mock_db.close = MagicMock()
        mock_mem.search_memory.return_value = [
            {"summary": "Uses Neo4j", "category": "tech_stack", "tags": [], "score": 0.9}
        ]

        from fastapi.testclient import TestClient
        from app.main import app

        client = TestClient(app)
        response = client.get(
            "/memory/search?query=database&category=tech_stack",
            headers={"x-user-id": "user_42"}
        )

        assert response.status_code == 200
        data = response.json()
        assert len(data["matches"]) == 1
        mock_mem.search_memory.assert_called_once_with("database", user_id="user_42", category="tech_stack")
        print("✅ Test 8b PASSED: /memory/search passes query + category + user_id")

    @patch("app.main.memory_service")
    @patch("app.main.graph_db")
    def test_profile_endpoint_returns_grouped(self, mock_db, mock_mem):
        """GET /memory/profile should return grouped Neural Blueprint."""
        mock_db.connect = MagicMock()
        mock_db.close = MagicMock()
        mock_mem.get_user_profile.return_value = {
            "tech_stack": ["Uses FastAPI", "Python 3.11"],
            "preference": ["Prefers Hilt over Koin"]
        }

        from fastapi.testclient import TestClient
        from app.main import app

        client = TestClient(app)
        response = client.get(
            "/memory/profile",
            headers={"x-user-id": "user_42"}
        )

        assert response.status_code == 200
        data = response.json()
        assert data["user_id"] == "user_42"
        assert "tech_stack" in data["profile"]
        assert "Uses FastAPI" in data["profile"]["tech_stack"]
        print("✅ Test 8c PASSED: /memory/profile returns grouped Neural Blueprint")


# ============================================================
# TEST 9: Background Sync - /agent/chat triggers extraction
# ============================================================

class TestBackgroundSync:
    """Tests that the chat endpoint triggers implicit profiling."""

    @patch("app.main.memory_service")
    @patch("app.main.agent_app")
    @patch("app.main.save_message_to_session")
    @patch("app.main.get_or_create_daily_session")
    @patch("app.main.graph_db")
    def test_chat_triggers_background_extraction(
        self, mock_db, mock_get_session, mock_save_msg, mock_agent, mock_mem
    ):
        """POST /agent/chat should add extract_and_save_implicit_profile as background task."""
        mock_db.connect = MagicMock()
        mock_db.close = MagicMock()
        mock_get_session.return_value = {"session_id": "sess_123"}

        # Mock agent response
        mock_ai_msg = MagicMock()
        mock_ai_msg.content = "Here's how to set up Neo4j with Python..."
        mock_agent.ainvoke = AsyncMock(return_value={"messages": [mock_ai_msg]})

        from fastapi.testclient import TestClient
        from app.main import app

        client = TestClient(app)
        response = client.post(
            "/agent/chat",
            json={"message": "How do I connect Neo4j with my Python backend?"},
            headers={"x-user-id": "user_42"}
        )

        assert response.status_code == 200

        # Background task should have been called with extract_and_save_implicit_profile
        # In TestClient, background tasks run synchronously, so we can check the call
        mock_mem.extract_and_save_implicit_profile.assert_called_once()
        call_args = mock_mem.extract_and_save_implicit_profile.call_args
        assert call_args[0][0] == "user_42"  # user_id
        assert "Neo4j" in call_args[0][1]     # conversation text contains the query
        print("✅ Test 9a PASSED: /agent/chat triggers background profile extraction")


# ============================================================
# TEST 10: Tool Registration
# ============================================================

class TestToolRegistration:
    """Verify new tools are properly registered."""

    def test_search_user_context_in_all_tools(self):
        from app.agent.tools import ALL_TOOLS
        tool_names = [t.name for t in ALL_TOOLS]
        assert "search_user_context" in tool_names
        print("✅ Test 10a PASSED: search_user_context is in ALL_TOOLS")

    def test_save_memory_note_in_all_tools(self):
        from app.agent.tools import ALL_TOOLS
        tool_names = [t.name for t in ALL_TOOLS]
        assert "save_memory_note" in tool_names
        print("✅ Test 10b PASSED: save_memory_note is in ALL_TOOLS")

    def test_all_tools_count_updated(self):
        from app.agent.tools import ALL_TOOLS
        assert len(ALL_TOOLS) == 10  # 8 original + search_user_context + suggest_learning_resources
        print("✅ Test 10c PASSED: ALL_TOOLS count is 10")

    def test_save_memory_note_has_category_param(self):
        """save_memory_note should accept category as a parameter."""
        from app.agent.tools import save_memory_note
        schema = save_memory_note.args_schema.schema()
        prop_names = list(schema["properties"].keys())
        assert "category" in prop_names
        assert "tags" in prop_names
        print("✅ Test 10d PASSED: save_memory_note schema has category + tags params")
