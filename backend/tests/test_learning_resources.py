"""
Tests for the Learning Resource Suggestions feature.
Tests cover:
1. LLM service - generate_learning_resources() method
2. Graph CRUD - save/retrieve resources from Neo4j (mocked)
3. API endpoint - GET /goals/{goal_id}/resources
4. Agent tool - suggest_learning_resources
"""
import json
import sys
import os

# CRITICAL: Set env vars BEFORE any app imports, since Settings() runs at import time
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
from unittest.mock import patch, MagicMock

# Add the backend directory to path so imports work
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

# Pre-import all modules that @patch decorators need to reference
# This ensures mock patching can resolve "app.main", "app.agent.tools" etc.
import app.services.llm_service
import app.services.graph_crud
import app.agent.tools
import app.main


# ============================================================
# TEST 1: LLM Service - generate_learning_resources()
# ============================================================

class TestLLMServiceResourceGeneration:
    """Tests for the LLM-based resource generation."""

    def _make_service(self, mock_boto_client):
        """Create an LLMService instance with mocked boto3 client."""
        from app.services.llm_service import LLMService
        svc = LLMService()
        svc.client = mock_boto_client
        return svc

    @patch("app.services.llm_service.boto3")
    def test_generate_resources_returns_list(self, mock_boto3):
        """Should return a list of resource dicts when LLM responds with valid JSON."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        
        response_text = json.dumps([
            {
                "title": "CS50: Intro to Computer Science",
                "type": "course",
                "platform": "edX",
                "description": "Harvard's famous CS intro course",
                "url": "https://www.google.com/search?q=CS50+edX"
            },
            {
                "title": "Python Official Tutorial",
                "type": "article",
                "platform": "python.org",
                "description": "The official Python docs tutorial",
                "url": "https://www.google.com/search?q=python+official+tutorial"
            },
            {
                "title": "Corey Schafer Python Series",
                "type": "video",
                "platform": "YouTube",
                "description": "Comprehensive Python tutorial series",
                "url": "https://www.google.com/search?q=corey+schafer+python"
            },
            {
                "title": "LeetCode Python Track",
                "type": "exercise",
                "platform": "LeetCode",
                "description": "Practice Python coding problems",
                "url": "https://www.google.com/search?q=leetcode+python"
            }
        ])
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": response_text}]}}
        }

        svc = self._make_service(mock_client)
        resources = svc.generate_learning_resources("Python Programming")

        assert isinstance(resources, list)
        assert len(resources) == 4
        assert resources[0]["title"] == "CS50: Intro to Computer Science"
        assert resources[0]["type"] == "course"
        assert resources[1]["type"] == "article"
        assert resources[2]["type"] == "video"
        assert resources[3]["type"] == "exercise"
        print("✅ Test 1a PASSED: generate_learning_resources() returns valid list")

    @patch("app.services.llm_service.boto3")
    def test_generate_resources_with_context(self, mock_boto3):
        """Should accept current_topic and category context."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        
        response_text = json.dumps([
            {
                "title": "React Hooks Deep Dive",
                "type": "article",
                "platform": "Medium",
                "description": "Understanding useState and useEffect"
            }
        ])
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": response_text}]}}
        }

        svc = self._make_service(mock_client)
        resources = svc.generate_learning_resources(
            goal_title="React Development",
            current_topic="React Hooks: useState",
            category="Coding"
        )

        assert len(resources) == 1
        assert resources[0]["platform"] == "Medium"
        assert "url" in resources[0]
        print("✅ Test 1b PASSED: generate_learning_resources() with context works")

    @patch("app.services.llm_service.boto3")
    def test_generate_resources_invalid_json(self, mock_boto3):
        """Should return empty list on invalid JSON response."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": "This is not JSON at all"}]}}
        }

        svc = self._make_service(mock_client)
        resources = svc.generate_learning_resources("Python")

        assert resources == []
        print("✅ Test 1c PASSED: Invalid JSON returns empty list gracefully")

    @patch("app.services.llm_service.boto3")
    def test_generate_resources_filters_invalid_items(self, mock_boto3):
        """Should filter out resources missing required fields."""
        mock_client = MagicMock()
        mock_boto3.client.return_value = mock_client
        
        response_text = json.dumps([
            {
                "title": "Valid Resource",
                "type": "course",
                "platform": "Coursera",
                "description": "A valid resource"
            },
            {
                "title": "Missing type and platform"
            },
            {
                "title": "Invalid type resource",
                "type": "podcast",
                "platform": "Spotify",
                "description": "Should be corrected to article"
            }
        ])
        mock_client.converse.return_value = {
            "output": {"message": {"content": [{"text": response_text}]}}
        }

        svc = self._make_service(mock_client)
        resources = svc.generate_learning_resources("Python")

        assert len(resources) == 2
        assert resources[0]["type"] == "course"
        assert resources[1]["type"] == "article"
        print("✅ Test 1d PASSED: Invalid items filtered, bad types corrected")


# ============================================================
# TEST 2: Graph CRUD - save/get learning resources (mocked Neo4j)
# ============================================================

class TestGraphCRUDResources:
    """Tests for Neo4j CRUD functions for learning resources."""

    @patch("app.services.graph_crud.graph_db")
    def test_save_learning_resources(self, mock_db):
        """Should run the correct Cypher query to save resources."""
        from app.services.graph_crud import save_learning_resources

        mock_session = MagicMock()
        mock_db.get_session.return_value.__enter__ = MagicMock(return_value=mock_session)
        mock_db.get_session.return_value.__exit__ = MagicMock(return_value=False)

        resources = [
            {"title": "Test Course", "type": "course", "platform": "Udemy", "description": "A test course", "url": "https://google.com"}
        ]

        save_learning_resources("goal_123", resources)
        
        mock_session.run.assert_called_once()
        call_args = mock_session.run.call_args
        assert "goal_123" in str(call_args)
        assert "HAS_RESOURCE" in call_args[0][0]
        print("✅ Test 2a PASSED: save_learning_resources() runs correct Cypher")

    @patch("app.services.graph_crud.graph_db")
    def test_get_learning_resources(self, mock_db):
        """Should return a list of resources fetched from Neo4j."""
        from app.services.graph_crud import get_learning_resources

        mock_record1 = {
            "id": "res_1", "title": "Python Crash Course", "type": "course",
            "platform": "Udemy", "description": "Learn Python fast", "url": "http://example.com"
        }
        mock_record2 = {
            "id": "res_2", "title": "Automate the Boring Stuff", "type": "article",
            "platform": "Web", "description": "Free online book", "url": "http://example.com/book"
        }

        mock_session = MagicMock()
        mock_session.run.return_value = [mock_record1, mock_record2]
        mock_db.get_session.return_value.__enter__ = MagicMock(return_value=mock_session)
        mock_db.get_session.return_value.__exit__ = MagicMock(return_value=False)

        result = get_learning_resources("goal_123")
        
        assert len(result) == 2
        assert result[0]["title"] == "Python Crash Course"
        assert result[1]["type"] == "article"
        print("✅ Test 2b PASSED: get_learning_resources() returns correct data")

    @patch("app.services.graph_crud.graph_db")
    def test_get_goal_title_and_category(self, mock_db):
        """Should return goal title and category."""
        from app.services.graph_crud import get_goal_title_and_category

        mock_record = MagicMock()
        mock_record.__getitem__ = lambda self, key: {"title": "Learn Python", "category": "Coding"}[key]

        mock_session = MagicMock()
        mock_result = MagicMock()
        mock_result.single.return_value = mock_record
        mock_session.run.return_value = mock_result
        mock_db.get_session.return_value.__enter__ = MagicMock(return_value=mock_session)
        mock_db.get_session.return_value.__exit__ = MagicMock(return_value=False)

        result = get_goal_title_and_category("goal_123")
        
        assert result["title"] == "Learn Python"
        assert result["category"] == "Coding"
        print("✅ Test 2c PASSED: get_goal_title_and_category() works")


# ============================================================
# TEST 3: FastAPI Endpoint - GET /goals/{goal_id}/resources
# ============================================================

class TestResourcesEndpoint:
    """Tests for the REST API endpoint."""

    @patch("app.main.get_learning_resources")
    @patch("app.main.get_goal_title_and_category")
    @patch("app.main.llm_service")
    @patch("app.main.graph_db")
    def test_endpoint_returns_cached(self, mock_db, mock_llm, mock_get_goal, mock_get_res):
        """Should return cached resources if they exist."""
        mock_db.connect = MagicMock()
        mock_db.close = MagicMock()
        
        mock_get_res.return_value = [
            {"id": "r1", "title": "Cached Resource", "type": "course", "platform": "Udemy", "description": "Already cached", "url": ""}
        ]
        
        from fastapi.testclient import TestClient
        from app.main import app
        
        client = TestClient(app)
        response = client.get("/goals/goal_123/resources")
        
        assert response.status_code == 200
        data = response.json()
        assert data["source"] == "cached"
        assert len(data["resources"]) == 1
        assert data["resources"][0]["title"] == "Cached Resource"
        mock_llm.generate_learning_resources.assert_not_called()
        print("✅ Test 3a PASSED: Endpoint returns cached resources")

    @patch("app.main.save_learning_resources")
    @patch("app.main.get_learning_resources")
    @patch("app.main.get_goal_title_and_category")
    @patch("app.main.llm_service")
    @patch("app.main.graph_db")
    def test_endpoint_generates_when_no_cache(self, mock_db, mock_llm, mock_get_goal, mock_get_res, mock_save_res):
        """Should generate resources via LLM when no cache exists."""
        mock_db.connect = MagicMock()
        mock_db.close = MagicMock()
        
        mock_get_res.return_value = []
        mock_get_goal.return_value = {"title": "Learn Python", "category": "Coding"}
        mock_llm.generate_learning_resources.return_value = [
            {"title": "New Resource", "type": "video", "platform": "YouTube", "description": "Fresh from LLM", "url": ""}
        ]
        
        from fastapi.testclient import TestClient
        from app.main import app
        
        client = TestClient(app)
        response = client.get("/goals/goal_456/resources")
        
        assert response.status_code == 200
        data = response.json()
        assert data["source"] == "generated"
        assert len(data["resources"]) == 1
        mock_llm.generate_learning_resources.assert_called_once()
        mock_save_res.assert_called_once()
        print("✅ Test 3b PASSED: Endpoint generates and caches resources")

    @patch("app.main.save_learning_resources")
    @patch("app.main.get_learning_resources")
    @patch("app.main.get_goal_title_and_category")
    @patch("app.main.llm_service")
    @patch("app.main.graph_db")
    def test_endpoint_refresh_regenerates(self, mock_db, mock_llm, mock_get_goal, mock_get_res, mock_save_res):
        """Should regenerate resources when refresh=True, even if cached."""
        mock_db.connect = MagicMock()
        mock_db.close = MagicMock()
        
        mock_get_goal.return_value = {"title": "Learn React", "category": "Coding"}
        mock_llm.generate_learning_resources.return_value = [
            {"title": "Refreshed Resource", "type": "course", "platform": "Coursera", "description": "Newly generated", "url": ""}
        ]
        
        from fastapi.testclient import TestClient
        from app.main import app
        
        client = TestClient(app)
        response = client.get("/goals/goal_789/resources?refresh=true")
        
        assert response.status_code == 200
        data = response.json()
        assert data["source"] == "generated"
        mock_llm.generate_learning_resources.assert_called()
        print("✅ Test 3c PASSED: Endpoint refresh=true regenerates resources")


# ============================================================
# TEST 4: Agent Tool - suggest_learning_resources
# ============================================================

class TestAgentTool:
    """Tests for the LangChain agent tool."""

    @patch("app.agent.tools.get_learning_resources")
    @patch("app.agent.tools.llm_service")
    def test_tool_returns_cached(self, mock_llm, mock_get_res):
        """Should return cached resources if goal_id is provided and cache exists."""
        from app.agent.tools import suggest_learning_resources
        
        mock_get_res.return_value = [
            {"title": "Cached Tool Resource", "type": "article", "platform": "MDN", "description": "From cache", "url": ""}
        ]

        result = suggest_learning_resources.invoke({
            "user_id": "user_1",
            "goal_title": "JavaScript",
            "goal_id": "goal_js"
        })

        parsed = json.loads(result)
        assert len(parsed) == 1
        assert parsed[0]["title"] == "Cached Tool Resource"
        mock_llm.generate_learning_resources.assert_not_called()
        print("✅ Test 4a PASSED: Agent tool returns cached resources")

    @patch("app.agent.tools.save_learning_resources")
    @patch("app.agent.tools.get_learning_resources")
    @patch("app.agent.tools.llm_service")
    def test_tool_generates_when_no_cache(self, mock_llm, mock_get_res, mock_save_res):
        """Should generate resources when no cache exists."""
        from app.agent.tools import suggest_learning_resources
        
        mock_get_res.return_value = []
        mock_llm.generate_learning_resources.return_value = [
            {"title": "Fresh Resource", "type": "video", "platform": "YouTube", "description": "Generated", "url": ""}
        ]

        result = suggest_learning_resources.invoke({
            "user_id": "user_1",
            "goal_title": "Data Science",
            "goal_id": "goal_ds"
        })

        parsed = json.loads(result)
        assert len(parsed) == 1
        assert parsed[0]["title"] == "Fresh Resource"
        mock_llm.generate_learning_resources.assert_called_once()
        mock_save_res.assert_called_once()
        print("✅ Test 4b PASSED: Agent tool generates and caches new resources")

    @patch("app.agent.tools.llm_service")
    def test_tool_without_goal_id(self, mock_llm):
        """Should still work without a goal_id (no caching, just generation)."""
        from app.agent.tools import suggest_learning_resources
        
        mock_llm.generate_learning_resources.return_value = [
            {"title": "Ad-hoc Resource", "type": "exercise", "platform": "HackerRank", "description": "Practice problems", "url": ""}
        ]

        result = suggest_learning_resources.invoke({
            "user_id": "user_1",
            "goal_title": "Algorithms"
        })

        parsed = json.loads(result)
        assert len(parsed) == 1
        assert parsed[0]["type"] == "exercise"
        print("✅ Test 4c PASSED: Agent tool works without goal_id")

    @patch("app.agent.tools.llm_service")
    def test_tool_returns_error_on_empty(self, mock_llm):
        """Should return error message when LLM returns nothing."""
        from app.agent.tools import suggest_learning_resources
        
        mock_llm.generate_learning_resources.return_value = []

        result = suggest_learning_resources.invoke({
            "user_id": "user_1",
            "goal_title": "Obscure Topic"
        })

        assert "Sorry" in result or "couldn't" in result
        print("✅ Test 4d PASSED: Agent tool handles empty LLM response")


# ============================================================
# TEST 5: Tool Registration - Verify tool is in ALL_TOOLS
# ============================================================

class TestToolRegistration:
    """Verify the new tool is properly registered."""

    def test_suggest_learning_resources_in_all_tools(self):
        from app.agent.tools import ALL_TOOLS
        tool_names = [t.name for t in ALL_TOOLS]
        assert "suggest_learning_resources" in tool_names
        print("✅ Test 5a PASSED: suggest_learning_resources is in ALL_TOOLS")

    def test_all_tools_count(self):
        from app.agent.tools import ALL_TOOLS
        assert len(ALL_TOOLS) == 10  # 8 original + suggest_learning_resources + search_user_context
        print("✅ Test 5b PASSED: ALL_TOOLS has correct count (10)")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])
