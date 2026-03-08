import boto3
import botocore.exceptions
from app.core.config import settings
from typing import Optional
import random
import json
import time
import hashlib
from collections import OrderedDict


class LRUCache:
    """Simple LRU cache for Bedrock responses to reduce API costs."""

    def __init__(self, max_size: int = 500):
        self._cache = OrderedDict()
        self._max_size = max_size

    def _key(self, prompt: str) -> str:
        return hashlib.sha256(prompt.encode()).hexdigest()

    def get(self, prompt: str):
        key = self._key(prompt)
        if key in self._cache:
            self._cache.move_to_end(key)
            return self._cache[key]
        return None

    def set(self, prompt: str, response: str):
        key = self._key(prompt)
        self._cache[key] = response
        self._cache.move_to_end(key)
        if len(self._cache) > self._max_size:
            self._cache.popitem(last=False)

class LLMService:
    def __init__(self):
        self.client = boto3.client(
            "bedrock-runtime",
            region_name=settings.AWS_REGION,
            aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
            aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
        )
        self.model_id = settings.BEDROCK_MODEL_ID
        self.embed_model_id = settings.BEDROCK_EMBED_MODEL_ID
        self.embedding_dimensions = settings.EMBEDDING_DIMENSIONS
        self._cache = LRUCache(max_size=500)
        print(f"✅ LLMService initialized with Bedrock model: {self.model_id} | Embeddings: {self.embed_model_id} ({self.embedding_dimensions}D)")

    def _generate_text_safe(self, prompt: str, default: str, use_cache: bool = False) -> str:
        """
        Calls Amazon Bedrock with exponential backoff retry on throttling.
        Optionally checks cache for repeated prompts.
        """
        if use_cache:
            cached = self._cache.get(prompt)
            if cached:
                print("✅ Cache hit — skipping Bedrock call")
                return cached

        max_retries = 4
        for attempt in range(max_retries):
            try:
                response = self.client.converse(
                    modelId=self.model_id,
                    messages=[{"role": "user", "content": [{"text": prompt}]}],
                    inferenceConfig={"temperature": 0.3, "maxTokens": 4096},
                )
                # Extract text from Bedrock Converse response
                output_message = response["output"]["message"]
                result_text = ""
                for block in output_message["content"]:
                    if "text" in block:
                        result_text += block["text"]
                result = result_text.strip()
                if use_cache and result != default:
                    self._cache.set(prompt, result)
                return result
            except botocore.exceptions.ClientError as e:
                error_code = e.response["Error"]["Code"]
                if error_code in ("ThrottlingException", "TooManyRequestsException", "ServiceUnavailableException"):
                    wait = 2 ** attempt
                    print(f"⚠️ Bedrock throttled (attempt {attempt + 1}/{max_retries}). Retrying in {wait}s...")
                    time.sleep(wait)
                    continue
                else:
                    print(f"❌ Bedrock Error: {e}")
                    break
            except Exception as e:
                print(f"❌ Generation Error: {e}")
                break
        
        return default

    # UNUSED — get_response
    # The agent goes through LangChain's ChatGoogleGenerativeAI wrapper
    # (get_llm_response_with_retry in nodes.py), not this direct method.
    # Safe to delete.
    # def get_response(self, prompt: str) -> str:
    #     return self._generate_text_safe(prompt, default="I am sorry, I cannot reply right now.")

    def get_embedding(self, text: str) -> list[float]:
        """Get embeddings from Amazon Titan with retry logic."""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                response = self.client.invoke_model(
                    modelId=self.embed_model_id,
                    body=json.dumps({"inputText": text, "dimensions": self.embedding_dimensions}),
                    contentType="application/json",
                    accept="application/json",
                # Re-initialize for this specific key
                embedder = GoogleGenerativeAIEmbeddings(
                    model="models/gemini-embedding-001",
                    google_api_key=api_key
                )
                result = json.loads(response["body"].read())
                return result["embedding"]
            except botocore.exceptions.ClientError as e:
                error_code = e.response["Error"]["Code"]
                if error_code in ("ThrottlingException", "TooManyRequestsException"):
                    wait = 2 ** attempt
                    print(f"⚠️ Embedding throttled (attempt {attempt + 1}). Retrying in {wait}s...")
                    time.sleep(wait)
                    continue
                print(f"❌ Embedding Error: {e}")
                break
            except Exception as e:
                print(f"⚠️ Embedding failed: {e}")
                break
        
        print("❌ Bedrock embedding failed after retries.")
        return []

    def summarize_memory(self, text: str) -> str:
        prompt = (
            "Summarize this user information into a concise factual statement (under 25 words). "
            "Preserve specific details like names, versions, preferences, and technical terms. "
            "Do NOT generalize. Keep it as a retrievable fact.\n\n"
            f"Input: {text}"
        )
        return self._generate_text_safe(prompt, default="User info processed.")

    def extract_profile_signals(self, conversation: str) -> list:
        """
        The Dark Matter Extractor. Analyzes a conversation for implicit signals:
        tech stack, coding preferences, environment constraints, design decisions, pain points.
        Returns a list of profile signals to commit to long-term memory.
        """
        prompt = f"""
Analyze this conversation between a user and AI assistant. Extract IMPLICIT profile signals
that were NOT explicitly asked to be saved but reveal important facts about the user.

CATEGORIES (pick the best fit for each signal):
- "preference": Library/tool/coding style choices (e.g., "Prefers Hilt over Koin")
- "tech_stack": Languages, frameworks, versions, models mentioned (e.g., "Uses Python 3.11, FastAPI")
- "project_decision": Architecture or design choices made (e.g., "Chose Neo4j for graph relationships")
- "pain_point": Errors, frustrations, blockers encountered (e.g., "Struggles with Gemini 429 rate limits")
- "learning_style": How the user learns/works (e.g., "Prefers understanding math before using APIs")
- "environment": OS, hardware, deployment context (e.g., "Deploys on Google Cloud Run")
- "personal": Life facts, job role, goals (e.g., "College student working on hackathon project")

RULES:
- Only extract signals that are MEANINGFUL and REUSABLE in future conversations.
- SKIP: one-off typos, trivial syntax questions, greetings, generic questions.
- SKIP: anything that is just the AI talking (only extract USER signals).
- Each "content" should be a single factual statement (not a conversation quote).
- Include "tags" that link this signal to related concepts (project names, tech, domains).
- If the conversation reveals NOTHING profile-worthy, return an empty array.

CONVERSATION:
{conversation}

OUTPUT: Return STRICT JSON array only. No markdown fences.
[
  {{"content": "factual statement", "category": "category_name", "tags": ["tag1", "tag2"]}},
  ...
]
If nothing worth saving, return: []
"""
        response_text = self._generate_text_safe(prompt, default="[]")
        try:
            clean = response_text.replace("```json", "").replace("```", "").strip()
            signals = json.loads(clean)
            # Validate structure
            valid = []
            for s in signals:
                if isinstance(s, dict) and s.get("content"):
                    valid.append({
                        "content": s["content"],
                        "category": s.get("category", "general"),
                        "tags": s.get("tags", [])
                    })
            return valid
        except json.JSONDecodeError:
            print(f"⚠️ Profile extraction JSON parse failed. Raw: {response_text[:200]}")
            return []

    def generate_day_topic(self, goal_title: str, day_number: int, context: str = "") -> dict:
        prompt = f"""
        Create a lesson plan for Day {day_number} of '{goal_title}'.
        Context: {context}
        OUTPUT JSON: {{"topic": "Title", "sub_tasks": ["Task1", "Task2"]}}
        """
        
        response_text = self._generate_text_safe(prompt, default="", use_cache=True)
        
        try:
            clean_text = response_text.replace("```json", "").replace("```", "").strip()
            return json.loads(clean_text)
        except:
            print(f"⚠️ Failed to parse JSON. Raw text: {response_text}")
            return {
                "topic": f"Day {day_number}: {goal_title}",
                "sub_tasks": ["Research topic", "Practice basics", "Review notes"]
            }
        

    def generate_learning_resources(self, goal_title: str, current_topic: str = "", category: str = "") -> list:
        """
        Generates learning resource suggestions for a goal/topic.
        Returns a list of resources with type, title, platform, description, and search URL.
        """
        topic_context = f" currently studying '{current_topic}'" if current_topic else ""
        category_context = f" in the {category} domain" if category else ""

        prompt = f"""
        Act as an expert learning advisor. A student is learning '{goal_title}'{topic_context}{category_context}.
        
        Suggest 8-10 high-quality learning resources across these categories:
        - "course" (e.g., Coursera, Udemy, edX, Khan Academy)
        - "article" (e.g., MDN, Medium, official docs, tutorials)
        - "video" (e.g., YouTube channels, conference talks)
        - "exercise" (e.g., LeetCode, HackerRank, Codewars, project ideas)
        
        For EACH resource, provide:
        1. title: Specific resource name (e.g., "CS50: Introduction to Computer Science")
        2. type: One of "course", "article", "video", "exercise"
        3. platform: Where to find it (e.g., "YouTube", "Coursera", "MDN")
        4. description: 1-2 sentence summary of what the student will learn
        5. url: A direct search URL to find this resource (use Google search format: https://www.google.com/search?q=<encoded+query>)
        
        CRITICAL:
        - Mix resource types (at least 2 of each category).
        - Order by relevance (most helpful first).
        - Output STRICT JSON array only.
        
        JSON Format:
        [
          {{
            "title": "Resource Name",
            "type": "course",
            "platform": "Coursera",
            "description": "Learn X through Y",
            "url": "https://www.google.com/search?q=resource+name+platform"
          }}
        ]
        """
        
        response_text = self._generate_text_safe(prompt, default="[]")
        
        try:
            clean_text = response_text.replace("```json", "").replace("```", "").strip()
            resources = json.loads(clean_text)
            # Validate each resource has required fields
            valid_resources = []
            for r in resources:
                if all(k in r for k in ["title", "type", "platform", "description"]):
                    # Ensure type is valid
                    if r["type"] not in ("course", "article", "video", "exercise"):
                        r["type"] = "article"
                    if "url" not in r:
                        r["url"] = ""
                    valid_resources.append(r)
            return valid_resources
        except json.JSONDecodeError:
            print(f"❌ Resource JSON Parse Error. Raw: {response_text}")
            return []

    def generate_learning_resources(self, goal_title: str, current_topic: str = "", category: str = "") -> list:
        """
        Generates learning resource suggestions for a goal/topic.
        Returns a list of resources with type, title, platform, description, and search URL.
        """
        topic_context = f" currently studying '{current_topic}'" if current_topic else ""
        category_context = f" in the {category} domain" if category else ""

        prompt = f"""
        Act as an expert learning advisor. A student is learning '{goal_title}'{topic_context}{category_context}.
        
        Suggest 8-10 high-quality learning resources across these categories:
        - "course" (e.g., Coursera, Udemy, edX, Khan Academy)
        - "article" (e.g., MDN, Medium, official docs, tutorials)
        - "video" (e.g., YouTube channels, conference talks)
        - "exercise" (e.g., LeetCode, HackerRank, Codewars, project ideas)
        
        For EACH resource, provide:
        1. title: Specific resource name (e.g., "CS50: Introduction to Computer Science")
        2. type: One of "course", "article", "video", "exercise"
        3. platform: Where to find it (e.g., "YouTube", "Coursera", "MDN")
        4. description: 1-2 sentence summary of what the student will learn
        5. url: A direct search URL to find this resource (use Google search format: https://www.google.com/search?q=<encoded+query>)
        
        CRITICAL:
        - Mix resource types (at least 2 of each category).
        - Order by relevance (most helpful first).
        - Output STRICT JSON array only.
        
        JSON Format:
        [
          {{
            "title": "Resource Name",
            "type": "course",
            "platform": "Coursera",
            "description": "Learn X through Y",
            "url": "https://www.google.com/search?q=resource+name+platform"
          }}
        ]
        """
        
        response_text = self._generate_text_safe(prompt, default="[]")
        
        try:
            clean_text = response_text.replace("```json", "").replace("```", "").strip()
            resources = json.loads(clean_text)
            # Validate each resource has required fields
            valid_resources = []
            for r in resources:
                if all(k in r for k in ["title", "type", "platform", "description"]):
                    # Ensure type is valid
                    if r["type"] not in ("course", "article", "video", "exercise"):
                        r["type"] = "article"
                    if "url" not in r:
                        r["url"] = ""
                    valid_resources.append(r)
            return valid_resources
        except json.JSONDecodeError:
            print(f"❌ Resource JSON Parse Error. Raw: {response_text}")
            return []

    def _schedule_topics(self, topics: list, daily_limit: int, target_days: Optional[int] = None) -> list:
        """
        Internal Scheduler Algorithm (Bin Packing).
        Groups topics into Days so that no day exceeds the daily_limit.

        If target_days is provided:
          - Truncates the schedule if it produces more days than requested.
          - Merges remaining topics into the last day if fewer days are produced.
        """
        schedule = []
        current_day = {
            "day_number": 1, 
            "topics": [], 
            "minutes_used": 0,
            "sub_tasks": []
        }
        
        for topic in topics:
            t_min = topic.get("minutes", 30)

            # If we have a target and already filled it, pile remaining into the last day
            if target_days and len(schedule) >= target_days - 1 and current_day["topics"]:
                current_day["topics"].append(topic["title"])
                current_day["sub_tasks"].extend(topic["sub_tasks"])
                current_day["minutes_used"] += t_min
                continue
            
            # Check if this topic fits in the current day
            # (Allow overflow if the day is empty, so we don't get stuck on huge topics)
            if (current_day["minutes_used"] + t_min <= daily_limit) or (current_day["minutes_used"] == 0):
                # Add to current day
                current_day["topics"].append(topic["title"])
                current_day["sub_tasks"].extend(topic["sub_tasks"])
                current_day["minutes_used"] += t_min
            else:
                # Day is full. Save it and start a new one.
                schedule.append(current_day)
                current_day = {
                    "day_number": len(schedule) + 1,
                    "topics": [topic["title"]],
                    "minutes_used": t_min,
                    "sub_tasks": topic["sub_tasks"]
                }
        
        # Don't forget the last partial day
        if current_day["topics"]:
            schedule.append(current_day)

        # If target_days is set and the AI gave fewer concepts than needed,
        # pad with placeholder days labelled as review/practice sessions.
        if target_days and len(schedule) < target_days:
            while len(schedule) < target_days:
                prev = schedule[-1] if schedule else None
                prev_topic = prev["topics"][0] if prev else "previous concepts"
                day_num = len(schedule) + 1
                schedule.append({
                    "day_number": day_num,
                    "topics": [f"Review & Practice: {prev_topic}"],
                    "minutes_used": daily_limit,
                    "sub_tasks": [
                        "Revisit notes and examples from previous sessions",
                        "Attempt practice problems or exercises",
                        "Identify and clarify any confusing areas"
                    ]
                })
            
        return schedule

# Singleton Instance
llm_service = LLMService()


# ==============================================================================
# ARCHIVE — Functions Not Currently In Use
# These are preserved here for reference. Do not call them directly.
# ==============================================================================

# --- generate_smart_roadmap + _schedule_topics ---
# PURPOSE: Generates a FULL N-day course schedule in one LLM call.
#   Step 1: Asks the LLM for a list of granular concepts with time estimates.
#   Step 2: Runs a bin-packing scheduler to group concepts into days (no day
#           exceeds `daily_minutes`). Supports a `target_days` override to
#           cap/pad the schedule to the user's requested duration.
#
# REPLACED BY: generate_day_topic() called per-day in background threads.
#   The current approach is faster to respond (no blocking LLM call at goal
#   creation time) and generates personalized content per day based on progress.
#
# USEFUL AGAIN IF: You want to pre-compute the entire roadmap upfront
#   (e.g., to display full subtasks for all days immediately, export a PDF plan,
#   or run "smart seeding" via create_initial_days).
#
# class LLMService: (methods below — add back into the class if restoring)
#
#   def generate_smart_roadmap(self, goal_title: str, user_context: str = "",
#                              daily_minutes: int = 60,
#                              target_days: Optional[int] = None) -> list:
#       duration_hint = (
#           f"The user wants to complete this in exactly {target_days} days. "
#           f"Generate enough concepts to fill {target_days} days at {daily_minutes} min/day."
#           if target_days
#           else "Generate a thorough list of at least 15-20 concepts for a normal-paced course."
#       )
#       prompt = f"""
#       Act as an expert curriculum designer. Create a learning path for '{goal_title}'.
#       Context: {user_context}
#       {duration_hint}
#       Break this into GRANULAR topics. For each provide:
#       1. title, 2. minutes (time to learn), 3. sub_tasks (3-4 steps).
#       Output STRICT JSON only:
#       [{{ "title": "Concept", "minutes": 45, "sub_tasks": ["Read docs", "Build X"] }}]
#       """
#       response_text = self._generate_text_safe(prompt, default="[]")
#       try:
#           topics = json.loads(response_text.replace("```json","").replace("```","").strip())
#           return self._schedule_topics(topics, daily_minutes, target_days=target_days)
#       except json.JSONDecodeError:
#           return []
#
#   def _schedule_topics(self, topics: list, daily_limit: int,
#                        target_days: Optional[int] = None) -> list:
#       """Bin-packing scheduler: groups topics into days within daily_limit minutes."""
#       schedule = []
#       current_day = {"day_number": 1, "topics": [], "minutes_used": 0, "sub_tasks": []}
#       for topic in topics:
#           t_min = topic.get("minutes", 30)
#           if target_days and len(schedule) >= target_days - 1 and current_day["topics"]:
#               current_day["topics"].append(topic["title"])
#               current_day["sub_tasks"].extend(topic["sub_tasks"])
#               current_day["minutes_used"] += t_min
#               continue
#           if (current_day["minutes_used"] + t_min <= daily_limit) or (current_day["minutes_used"] == 0):
#               current_day["topics"].append(topic["title"])
#               current_day["sub_tasks"].extend(topic["sub_tasks"])
#               current_day["minutes_used"] += t_min
#           else:
#               schedule.append(current_day)
#               current_day = {"day_number": len(schedule)+1, "topics": [topic["title"]],
#                              "minutes_used": t_min, "sub_tasks": topic["sub_tasks"]}
#       if current_day["topics"]:
#           schedule.append(current_day)
#       if target_days and len(schedule) < target_days:
#           while len(schedule) < target_days:
#               prev_topic = schedule[-1]["topics"][0] if schedule else "previous concepts"
#               schedule.append({"day_number": len(schedule)+1,
#                                "topics": [f"Review & Practice: {prev_topic}"],
#                                "minutes_used": daily_limit,
#                                "sub_tasks": ["Revisit notes", "Attempt exercises", "Clarify doubts"]})
#       return schedule
