# SkillMorph

An AI-powered learning companion that creates personalized skill roadmaps, manages daily tasks, and builds an evolving profile of each user's learning style over time. Built with a **LangGraph agentic backend** powered by **Claude Haiku 4.5 on Amazon Bedrock** and a native **Android (Jetpack Compose)** frontend.

---

## Table of Contents

1. [Features](#features)
2. [Tech Stack](#tech-stack)
3. [Architecture](#architecture)
4. [Neo4j Graph Schema](#neo4j-graph-schema)
5. [LangGraph Agent](#langgraph-agent)
6. [Neural Blueprint (Memory System)](#neural-blueprint-memory-system)
7. [Smart Roadmap Generation](#smart-roadmap-generation)
8. [Android App Screens](#android-app-screens)
9. [API Endpoints](#api-endpoints)
10. [Project Structure](#project-structure)
11. [Data Models](#data-models)
12. [Setup & Installation](#setup--installation)
13. [Configuration](#configuration)
14. [Testing](#testing)
15. [Key Dependencies](#key-dependencies)
16. [Design Decisions](#design-decisions)

---

## Features

### Core AI Features
- **Smart Roadmap Generation** — Describe a skill you want to learn; the AI breaks it into 15-20 granular concepts, estimates time per topic, and packs them into a daily schedule using a bin-packing algorithm (default: 60 minutes/day).
- **Agentic Chat** — Conversational interface backed by a LangGraph state machine with 10 tool-calling capabilities. The agent can create goals, complete tasks, manage memory, search user context, and suggest learning resources — all through natural language.
- **Neural Blueprint (Long-Term Memory)** — A RAG-based system that builds an evolving profile of the user's preferences, tech stack, pain points, and learning style. Uses Qdrant vector search with Amazon Titan embeddings (768-dim, cosine similarity).
- **Dark Matter Extractor** — Background process that runs after every chat interaction, analyzing the conversation for implicit signals (tech stack mentions, tool preferences, pain points, learning style) and auto-saves them to the user's Neural Blueprint without explicit user action.
- **Learning Resources** — AI-generated recommendations across 4 types: courses (Coursera, Udemy), articles (MDN, docs), videos (YouTube), and exercises (LeetCode, HackerRank). Cached in Neo4j for instant retrieval.
- **Voice Mode** — Toggle between voice and text chat. Voice mode optimizes responses for TTS (under 3 sentences, no markdown, no code blocks, natural speech patterns).
- **Daily Briefing** — Background worker (6:00 AM IST) that fetches today's tasks and asks the AI agent to generate a morning briefing summary. Cached in SharedPreferences.

### Task & Goal Management
- **Metro Map Visualization** — Goal progress displayed as an interactive metro/subway-style timeline. Day nodes show status: completed (green glow), current (cyan pulsating), locked (gray).
- **Pacing Logic** — Advanced Cypher query that calculates which task to show based on the user's progress, with a "cooldown" shift that prevents burnout (if you finished Day 3 today, Day 4 appears tomorrow, not today).
- **Side Quests** — Standalone one-off tasks ("Buy milk", "Email boss") scheduled to specific dates, separate from learning goals.
- **Rolling Content Generation** — When a day is completed, the backend triggers background generation of content for Day N+2, so future days always have pre-generated subtasks.
- **Subtask Checkboxes** — Each day has 3-5 specific actionable subtasks with persistent checkbox states saved to Neo4j.

### Gamification & Profile
- **XP System** — 10 XP per completed task. Level = XP / 500 + 1.
- **Streak Tracking** — Current streak, max streak, active days calculated from completion dates.
- **Heatmap** — GitHub-style activity calendar (365 days, intensity scale 0-4).
- **Skill Radar** — Normalized skill matrix visualization based on goal categories.
- **Badges** — Streak Starter (5-day), Streak Master (50-day), Century Club (100-day).
- **Dynamic Titles** — Category-based titles that evolve with level: "Script Kiddie" → "Programmer" → "Code Ninja" → "System Architect" (Coding), "Walker" → "Titan" (Health), etc.
- **Virtual Day (3:30 AM Cutoff)** — Day boundaries aligned to IST sleep cycles, not UTC midnight. If it's 2:00 AM on Jan 24, it counts as Jan 23.

### Chat & Sessions
- **Daily Session Management** — One chat session per virtual day, auto-created on first interaction. Stored in Neo4j with `User → HAS_SESSION → Session → HAS_MESSAGE → Message` graph structure.
- **Session History** — Full chat history with sidebar navigation to previous sessions.
- **Response Sanitization** — Handles complex AI response formats (string, list of content parts, dict with citations) and normalizes to clean strings before saving to Neo4j.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Android App** | Kotlin 1.9.23, Jetpack Compose (BOM 2024.05.00), Hilt 2.51.1, Retrofit 2.9.0 + OkHttp 4.12.0, Room 2.6.1, Firebase Auth + Firestore (BOM 33.0.0), Navigation Compose 2.7.7 |
| **Backend API** | Python 3.11, FastAPI 0.128.0, Uvicorn 0.40.0 |
| **AI / Agent** | LangGraph 1.0.6, LangChain Core 1.2.7, langchain-aws >=0.2.0, Claude Haiku 4.5 via Amazon Bedrock (`anthropic.claude-haiku-4-5-20251001-v1:0`) |
| **Embeddings** | Amazon Titan Embed Text v2 (`amazon.titan-embed-text-v2:0`) — 768 dimensions |
| **Graph Database** | Neo4j 6.1.0 (Goal → Day → Task chain, Chat Sessions, User Profiles, Learning Resources) |
| **Vector Database** | Qdrant 1.16.2 (Neural Blueprint memory storage, cosine similarity search) |
| **Auth** | Firebase Authentication (Google Sign-In via Credential Manager) + Firestore (user profiles) |
| **Deployment** | Docker (Python 3.11-slim), Google Cloud Run (port 8080), Render.com (current hosted backend) |
| **Build** | Gradle (AGP 8.4.1, KSP 1.9.23-1.0.19), compileSdk 34, minSdk 26 |

---

## Architecture

### System Overview

```
┌──────────────────────────┐        HTTP + x-user-id header        ┌──────────────────────────┐
│      Android App         │ ────────────────────────────────────── │     FastAPI Backend       │
│  Kotlin · Compose · Hilt │                                       │     Python 3.11           │
│                          │                                       │                           │
│  ┌─────────┐ ┌────────┐ │                                       │  ┌──────────┐             │
│  │ Retrofit │ │  Room  │ │                                       │  │  main.py │ REST API    │
│  │  + Auth  │ │ Cache  │ │                                       │  └─────┬────┘             │
│  │Interceptor│ │(SQLite)│ │                                       │        │                  │
│  └─────────┘ └────────┘ │                                       │  ┌─────▼──────────────┐   │
│                          │                                       │  │   LangGraph Agent   │   │
│  ┌──────────────────┐   │                                       │  │  (StateGraph +      │   │
│  │   Firebase Auth   │   │                                       │  │   MemorySaver +     │   │
│  │  + Firestore      │   │                                       │  │   10 Tools)         │   │
│  └──────────────────┘   │                                       │  └─────┬──────────────┘   │
└──────────────────────────┘                                       │        │                  │
                                                                   │  ┌─────▼──────┐           │
                                                                   │  │ LLMService │           │
                                                                   │  │ (Bedrock   │           │
                                                                   │  │  Converse  │           │
                                                                   │  │  API)      │           │
                                                                   │  └────────────┘           │
                                                                   │        │                  │
                                                          ┌────────┼────────┼──────────┐       │
                                                          │        │        │          │       │
                                                    ┌─────▼──┐ ┌───▼───┐ ┌─▼────────┐ │       │
                                                    │ Neo4j  │ │Qdrant │ │ Amazon   │ │       │
                                                    │Graph DB│ │Vector │ │ Bedrock  │ │       │
                                                    │        │ │  DB   │ │(Claude + │ │       │
                                                    │Goals   │ │Memory │ │ Titan)   │ │       │
                                                    │Days    │ │Search │ │          │ │       │
                                                    │Tasks   │ │       │ │          │ │       │
                                                    │Sessions│ │       │ │          │ │       │
                                                    └────────┘ └───────┘ └──────────┘ │       │
                                                          │                           │       │
                                                          └───────────────────────────┘       │
                                                                                              │
                                                                   └──────────────────────────┘
```

### Request Flow (Chat Example)

```
1. User types "I want to learn Python" in Android app
2. AuthInterceptor injects x-user-id header (Firebase UID)
3. POST /agent/chat → FastAPI receives ChatRequest
4. Get/create daily session (3:30 AM virtual day logic)
5. Save user message to Neo4j Session
6. LangGraph agent invoked:
   a. Agent Node → Claude Haiku 4.5 decides to call create_new_goal tool
   b. Tool Node → Executes create_new_goal:
      i.   Creates Goal node in Neo4j
      ii.  LLM generates 15-20 granular concepts with time estimates
      iii. Bin-packing algorithm groups concepts into days (60 min/day)
      iv.  Creates Day nodes with subtasks in Neo4j
   c. Agent Node → Claude reads tool result, formulates response
   d. No more tool calls → END
7. Sanitize AI response (handle string/list/dict content formats)
8. Save AI message to Neo4j Session
9. Background task: Dark Matter Extractor analyzes conversation for implicit signals
10. Return response to Android → display in chat UI
```

### LangGraph Agent Flow

```
                    ┌──────────────────┐
                    │   System Prompt  │
                    │  (Neural Blueprint│
                    │   Protocol +     │
                    │   6 Capabilities)│
                    └────────┬─────────┘
                             │
User Message ──────► [Agent Node] ──────► Claude Haiku 4.5 (ChatBedrockConverse)
                         │                      │
                         │                temperature: 0.3
                         │                maxTokens: 4096
                         │                      │
                   ┌─────▼─────┐               │
                   │ Tool Calls?│◄──────────────┘
                   ├─── Yes ────┤──► [Tool Node] ──► Execute tool ──► ToolMessage
                   │            │         │                               │
                   │            │         └───────────────────────────────┘
                   │            │                    (loops back to Agent)
                   └─── No ────┘──► END (return text response to user)
```

**Retry Logic:** Exponential backoff (2^attempt seconds) for Bedrock throttling errors, up to 4 retries. Both the direct `LLMService` and `ChatBedrockConverse` wrapper implement this independently.

---

## Neo4j Graph Schema

```
(:User {id})
  ├──[:HAS_GOAL]──►(:Goal {id, title, category, created_at, progress_percentage, completed_tasks, total_tasks})
  │                   ├──[:HAS_DAY]──►(:Day {id, day_number, topic, sub_tasks[], sub_task_states[], is_locked, is_completed, completed_date, scheduled_date})
  │                   │                  └──[:UNLOCKS]──►(:Day) ... (chain: Day1→Day2→Day3)
  │                   └──[:HAS_RESOURCE]──►(:Resource {id, title, type, platform, description, url, created_at})
  │
  ├──[:HAS_TASK]──►(:Task {id, title, is_completed, scheduled_date, created_at})
  │
  └──[:HAS_SESSION]──►(:Session {id, date, user_id, title, created_at})
                         └──[:HAS_MESSAGE]──►(:Message {id, text, sender, timestamp})
```

### Key Relationships
- **Goal → Day chain**: Days are linked via `[:UNLOCKS]` to enforce sequential progression. Day 1 is unlocked by default; completing Day N unlocks Day N+1.
- **Cascade Delete**: Deleting a goal removes all connected Day nodes via `DETACH DELETE`.
- **Pacing Logic**: The task query uses `duration.between()` and a "shift" calculation to determine which day to show based on completion timing.

---

## LangGraph Agent

### Agent State

```python
class AgentState(TypedDict):
    messages: Annotated[List[BaseMessage], operator.add]  # Chat history (append-only)
    is_voice_mode: bool        # Optimize responses for TTS
    user_id: str               # Firebase UID
    user_mood: str             # (reserved) Mood detection
    current_goal: dict         # (reserved) Active goal context
    next_step: str             # Graph routing control
```

### System Prompt Capabilities

The agent's system prompt defines 6 protocols:

1. **Task Management** — Semantic matching ("I bought milk" → finds "Buy Milk" in task list), future date handling, goal-day completion with rolling generation.
2. **Task Creation** — "I want to learn X" → `create_new_goal`, "Remind me to buy milk" → `create_todo_item`, relative date conversion to YYYY-MM-DD.
3. **Goal Management** — Fetch-before-delete pattern: always list goals first to get IDs, then delete by ID.
4. **Neural Blueprint Protocol** — Proactive retrieval (search user context before answering), auto-save triggers (8 categories), discard threshold (no trivial saves), contextual tag linking.
5. **Learning Resources** — Fetch goal ID first, then call `suggest_learning_resources`.
6. **General** — Concise, always pass user_id, dates must be YYYY-MM-DD.

### Agent Tools (10)

| Tool | Args | Description |
|------|------|-------------|
| `create_new_goal` | user_id, title, category, context | Creates Goal node → LLM generates 15-20 concepts → bin-packs into days → saves Day nodes with subtasks |
| `create_todo_item` | user_id, title, due_date | Creates a Side Quest task for a specific date. Auto-defaults to today if no date given. |
| `get_task_list_json` | user_id, target_date | Returns JSON list of all tasks (Main Quests + Side Quests) for a date with IDs |
| `confirm_task_completion` | user_id, task_id, task_type, goal_id, day_number | Marks task complete. For GOAL tasks: triggers background content generation for Day N+2 via threading |
| `save_memory_note` | user_id, content, category, tags | Saves a fact to Neural Blueprint. Agent auto-detects triggers (preference, tech_stack, pain_point, etc.) |
| `search_user_context` | user_id, query, category | RAG search over user's long-term memory. Used proactively before answering technical questions. |
| `get_active_goals_json` | user_id | Returns simplified JSON list of goals (title + ID) for lookup |
| `delete_specific_goal` | user_id, goal_id | Cascade-deletes a goal and all its Day nodes |
| `delete_specific_task` | user_id, task_id | Deletes a standalone Side Quest |
| `suggest_learning_resources` | user_id, goal_title, goal_id | Generates 8-10 resources via LLM (courses, articles, videos, exercises). Caches in Neo4j. |

---

## Neural Blueprint (Memory System)

### How It Works

The Neural Blueprint is a RAG-based long-term memory system that builds an evolving profile of each user.

```
                    ┌─────────────────────────┐
                    │   User Conversation     │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │  Dark Matter Extractor  │  (Background, every chat)
                    │  extract_profile_signals│
                    │  → JSON array of        │
                    │    {content, category,   │
                    │     tags}               │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────▼──────────────────┐
              │         For each signal:            │
              │  1. LLM summarizes (≤25 words)      │
              │  2. Titan embeds → 768-dim vector   │
              │  3. Qdrant upserts with metadata    │
              │     (user_id, category, tags)       │
              └──────────────────┬──────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │      Qdrant DB          │
                    │  Collection: user_memories│
                    │  Vector: 768-dim cosine  │
                    │  Filter: user_id + category│
                    └─────────────────────────┘
```

### Memory Categories (8)

| Category | Trigger | Example |
|----------|---------|---------|
| `preference` | Tool/library choices | "Prefers Hilt over Koin for DI" |
| `tech_stack` | Languages, frameworks | "Uses Python 3.11, FastAPI" |
| `project_decision` | Architecture choices | "Chose Neo4j for graph relationships" |
| `pain_point` | Errors, frustrations | "Struggles with Gemini 429 rate limits" |
| `learning_style` | How user learns | "Prefers understanding math before using APIs" |
| `environment` | OS, hardware, deployment | "Deploys on Google Cloud Run" |
| `personal` | Life facts, goals | "College student working on hackathon" |
| `general` | Catch-all | Everything else |

### Retrieval

- **Proactive**: Agent calls `search_user_context` before answering technical questions to check for known preferences.
- **Profile**: `GET /memory/profile` returns all memories grouped by category (scrolls entire Qdrant collection for user).
- **Filtered Search**: `GET /memory/search?query=...&category=...` with optional category filtering.

---

## Smart Roadmap Generation

The roadmap generation is a 2-step process:

### Step 1: LLM Concept Extraction

The LLM is prompted to act as a "curriculum designer" and return 15-20 granular concepts:

```json
[
  {"title": "Variables & Data Types", "minutes": 30, "sub_tasks": ["Read Python docs on types", "Practice in REPL"]},
  {"title": "Control Flow: if/elif/else", "minutes": 45, "sub_tasks": ["Write 5 conditional programs"]},
  ...
]
```

### Step 2: Bin-Packing Scheduler (Python)

A First-Fit Decreasing bin-packing algorithm groups concepts into days so no day exceeds the daily time limit (default 60 minutes):

```
Day 1: [Variables (30 min) + Control Flow (30 min)] = 60 min ✓
Day 2: [Loops (45 min)] = 45 min ✓ (next topic would overflow)
Day 3: [Functions (40 min) + Scope (20 min)] = 60 min ✓
...
```

- Topics that are too large for a single day are allowed to overflow (so the algorithm never gets stuck).
- The AI decides the roadmap length (not a fixed N days).
- Day 1 is scheduled for today; subsequent days are floating (unlocked as previous days complete).

---

## Android App Screens

### 1. Auth Screen
- **Google Sign-In** via Android Credential Manager → Firebase Auth
- SkillMorph branded logo with animated particle ring
- Glass-morphism button design

### 2. Home Screen (AI Agent)
- **Dual-mode interface**: Toggle between Voice and Type mode with animated glass chip
- **Voice Mode**: Microphone input via Android Speech Recognizer → TTS output
- **Type Mode**: Chat interface with message bubbles, Markdown rendering
- **Session sidebar**: Hamburger menu opens drawer with past chat session history
- `AgentViewModel` manages session lifecycle, message persistence (Room + API sync), TTS

### 3. Goals Screen
- List of `GoalCard` components with animated circular progress rings (1000ms animation)
- Each card shows: title, category, start date, projected end date, progress %
- Tap → navigates to Metro Map detail screen
- API: `GET /goals`

### 4. Metro Map Screen (Goal Detail)
- **Subway-line timeline** visualization with connecting lines
- Day node states: ✅ Completed (green glow), 🔵 Current (cyan pulse), 🔒 Locked (gray)
- Each day card shows: topic title, 3-5 subtasks with checkboxes, "Complete Day" button
- "Complete Day" requires all subtasks checked
- Subtask states persist immediately via: `PUT /goals/{goalId}/days/{dayNumber}/subtasks`
- Top-right button → Learning Resources screen

### 5. Tasks Screen
- **Week calendar selector** (Mon-Sun, dynamically generated) + full calendar day picker dialog
- Two sections: **Main Quests** (goal tasks, cyan border) + **Side Quests** (user tasks, gray border)
- Optimistic UI: checkbox toggles instantly, syncs in background, reverts on failure
- "+" FAB → `AddTaskDialog` for creating side quests
- API: `GET /tasks/today?date=YYYY-MM-DD`

### 6. Profile Screen
- **Header**: Avatar ring (gradient cyan→purple), level, XP progress bar
- **Stats Row**: Current streak 🔥, active days, max streak
- **Heatmap**: GitHub-style activity calendar (365 days, right-to-left, 4-intensity scale)
- **Skill Radar**: Normalized radar chart of skill categories (0.0-1.0)
- **Badges**: Unlocked achievement icons
- Data flows: API → `ProfileRepository` → Room DB → `Flow<ProfileState>` → UI

### 7. Learning Resources Screen
- Filter chips: All, Course, Article, Video, Exercise
- Each card: type icon, title, platform, description, "Open in Browser" button
- API: `GET /goals/{goalId}/resources?refresh=true/false`

### 8. Settings Screen
- **Account**: Edit profile (name, DOB), avatar selection grid (6 options)
- **Preferences**: Notifications, appearance (placeholders)
- **Danger Zone**: Delete account (mailto: intent)
- Logout confirmation dialog
- Data: Firestore `users/{uid}` document + Firebase Auth

### Navigation Graph

```
auth_route → AuthScreen
                │ (on login success)
                ▼
main_route → MainScreen (Scaffold + BottomNav + Drawer)
              ├── home_screen → HomeScreen (AI Chat)
              ├── goals_screen → GoalsScreen
              ├── tasks_screen → TasksScreen
              ├── profile_screen → ProfileScreen
              └── settings_screen → SettingsScreen

metro_map_screen/{goalId} → MetroMapScreen
resources_screen/{goalId} → LearningResourcesScreen
```

**Theme**: Cyberpunk dark palette — DarkPurple (#1A1A2E) background, NeonCyan (#00E5FF) accent, CyberPurple (#9C27B0) secondary, 9-color gradient overlay (teal → deep cyan → purple).

---

## API Endpoints

### Goals & Roadmap

| Method | Endpoint | Request | Response | Description |
|--------|----------|---------|----------|-------------|
| `GET` | `/goals` | — | `[{id, title, category, progress, created_at, projected_end_date}]` | List all goals with calculated end dates |
| `POST` | `/goals` | `GoalCreate {title, category}` | `{message, data: {id, title, total_days}}` | Create goal + generate AI roadmap (sync) |
| `GET` | `/goals/{goal_id}/roadmap` | — | `{title, category, days: [{day_number, topic, is_locked, is_completed, sub_tasks, sub_task_states}]}` | Full roadmap with all days sorted |
| `POST` | `/goals/{goal_id}/days/{day_number}/complete` | — | `{status, new_progress}` | Complete day, unlock next, trigger background gen for Day N+2 |
| `PUT` | `/goals/{goal_id}/days/{day_number}/subtasks` | `{states: [true, false, true]}` | `{status, states}` | Save subtask checkbox states |
| `GET` | `/goals/{goal_id}/resources` | `?refresh=bool` | `{resources: [...], source: "cached"/"generated"}` | Get/generate learning resources |

### Tasks

| Method | Endpoint | Request | Response | Description |
|--------|----------|---------|----------|-------------|
| `GET` | `/tasks/today` | `?date=YYYY-MM-DD` | `[{id, title, type, goal_title, goal_id, is_completed, day_number}]` | Tasks for date (Main Quests + Side Quests + History) |
| `POST` | `/tasks` | `{title, date}` | `{status}` | Create a side quest |
| `POST` | `/tasks/{task_id}/complete` | — | `{status}` | Complete a side quest |

### AI Agent & Chat

| Method | Endpoint | Request | Response | Description |
|--------|----------|---------|----------|-------------|
| `POST` | `/agent/chat` | `{message, is_voice_mode, session_id}` | `{response, mode, session_id}` | Send message to LangGraph agent. Triggers background Dark Matter extraction. |
| `POST` | `/chat/sessions/today` | — | `{session_id, title, date}` | Get/create today's session (3:30 AM virtual day) |
| `GET` | `/chat/sessions/{session_id}/history` | — | `[{text, isUser, timestamp}]` | Get chat messages (cleaned, system messages filtered) |
| `GET` | `/chat/sessions` | — | `[{id, title, date}]` | List all past sessions (sidebar) |

### Memory & Profile

| Method | Endpoint | Request | Response | Description |
|--------|----------|---------|----------|-------------|
| `POST` | `/memory/` | `{text}` | `{status, summary, category, tags}` | Save a memory (LLM-summarized, embedded, stored in Qdrant) |
| `GET` | `/memory/search` | `?query=...&category=...` | `{matches: [{summary, category, tags, score}]}` | Semantic search over user memories |
| `GET` | `/memory/profile` | — | `{user_id, profile: {preference: [...], tech_stack: [...], ...}}` | Full Neural Blueprint grouped by category |
| `GET` | `/user/profile` | — | `{stats: {level, xp, streak, ...}, heatmap, skill_matrix, badges}` | Gamification stats for profile screen |

**Auth**: All endpoints read `x-user-id` from the HTTP header (injected by Android's `AuthInterceptor` with Firebase UID). Falls back to `"test_user_123"` for browser testing.

---

## Project Structure

### Backend (`backend/`)

```
backend/
├── Dockerfile                         # Python 3.11-slim, port 8080
├── requirements.txt                   # 11 dependencies
├── .env                               # AWS, Neo4j, Qdrant credentials
├── tests/
│   ├── test_learning_resources.py     # 4 test classes (LLM gen, CRUD, API, agent tool)
│   └── test_neural_blueprint.py       # 10 test classes (save, search, profile, dark matter, endpoints)
└── app/
    ├── __init__.py
    ├── main.py                        # FastAPI app: 18 endpoints, lifespan, auth dependency
    ├── core/
    │   ├── __init__.py
    │   ├── config.py                  # Pydantic BaseSettings (reads .env)
    │   └── gemini_key.py              # Legacy: Gemini key rotation (unused, replaced by Bedrock)
    ├── agent/
    │   ├── graph.py                   # LangGraph: StateGraph + MemorySaver + conditional edges
    │   ├── nodes.py                   # agent_node (LLM + system prompt) + tool_node (executor)
    │   ├── state.py                   # AgentState TypedDict (messages, user_id, flags)
    │   └── tools.py                   # 10 @tool functions + ALL_TOOLS registry
    ├── services/
    │   ├── llm_service.py             # LLMService: Bedrock Converse, embeddings, roadmap gen,
    │   │                              #   learning resources, Dark Matter extraction, bin-packing
    │   ├── neo4j_service.py           # Neo4jService: driver singleton, connect/close/get_session
    │   ├── graph_crud.py              # 18 CRUD functions: goals, days, tasks, resources, profiles
    │   ├── memory_service.py          # MemoryService: Qdrant save/search/profile/dark-matter
    │   └── chat_session_service.py    # Virtual day logic, session CRUD, message history
    └── schemas/
        └── graph_models.py            # Pydantic: GoalCreate, GoalBase, TaskBase, DayModuleBase,
                                       #   TaskResponse, DayResponse, GoalResponse
```

### Android App (`app/src/main/`)

```
java/com/example/skillmorph/
├── MainActivity.kt                    # @AndroidEntryPoint, edge-to-edge, DailyBriefingWorker scheduler
├── SkillMorphApp.kt                   # @HiltAndroidApp base class
├── HomeScreen.kt                      # Voice/Type toggle, AgentRing, AgentChat composables
│
├── di/                                # Hilt Dependency Injection
│   ├── AuthModule.kt                  # Provides FirebaseAuth, binds AuthRepository
│   ├── NetworkModule.kt               # Provides OkHttpClient (60s timeout), Retrofit, SkillMorphApi
│   ├── DatabaseModule.kt              # Provides Room DB (v2), ChatDao, UserProfileDao
│   ├── FirestoreModule.kt             # Provides FirebaseFirestore
│   ├── AuthInterceptor.kt             # OkHttp interceptor: injects x-user-id header
│   └── SharedPreferences.kt           # Provides SharedPreferences (daily briefing cache)
│
├── data/
│   ├── local/entities/
│   │   ├── ChatMessageEntity.kt       # Room entity: chat_messages table (id, text, isUser, sessionId)
│   │   ├── UserProfileEntity.kt       # Room entity: user_profile table (singleton, JSON TypeConverter)
│   │   ├── ChatDao.kt                 # Flow<List<ChatMessage>>, insert, bulk insert, lastSessionId
│   │   ├── UserProfileDao.kt          # Flow<UserProfileEntity?>, upsert with REPLACE
│   │   ├── GoalEntity.kt              # Data class (not Room entity): goal model
│   │   └── TaskEntity.kt              # Data class (not Room entity): task model
│   ├── remote/
│   │   └── SkillMorphApi.kt           # Retrofit interface: 13 endpoint methods
│   └── repository/
│       └── AuthRepositoryImpl.kt      # Firebase Auth wrapper with callbackFlow
│
├── domain/repository/
│   ├── AuthRepository.kt              # Interface: signIn, signOut, currentUserFlow
│   └── TasksRepository.kt             # Interface (declared)
│
└── presentation/
    ├── auth/
    │   ├── AuthScreen.kt              # Google Sign-In UI (Credential Manager)
    │   └── AuthViewModel.kt           # Auth state management
    ├── main/
    │   ├── MainScreen.kt              # Scaffold, BottomNav (5 tabs), Drawer (sessions)
    │   ├── DailyBrieferWorker.kt      # CoroutineWorker: 6 AM background briefing
    │   └── viewModel/
    │       └── AgentViewModel.kt      # Chat state, TTS, session mgmt, daily briefing
    ├── goals/
    │   ├── GoalsScreen.kt             # Goal list with animated progress cards
    │   ├── GoalsViewModel.kt          # Fetch goals from API
    │   └── components/
    │       └── GoalCard.kt            # Circular progress ring, date columns, neon glow
    ├── goaldetail/
    │   ├── MetroMapScreen.kt          # Subway-line timeline with day nodes
    │   ├── MetroMapViewModel.kt       # Roadmap fetch, subtask toggle, day completion
    │   └── models/
    │       └── MetroMapModels.kt      # DayPlan, TimelineStatus enum
    ├── tasks/
    │   ├── TasksScreen.kt             # Week calendar, Main/Side quest cards, AddTaskDialog
    │   └── TasksViewModel.kt          # Date selection, optimistic task completion
    ├── Profile/
    │   ├── ProfileScreen.kt           # XP bar, heatmap, radar chart, badges
    │   └── ProfileViewModel.kt        # Room Flow → ProfileState transformation
    ├── resources/
    │   └── LearningResourcesScreen.kt # Filter chips, resource cards, open-in-browser
    ├── settings/
    │   ├── SettingsScreen.kt          # Account, preferences, danger zone
    │   └── SettingsViewModel.kt       # Firestore profile CRUD, avatar, logout
    └── navigation/
        ├── AppNavigation.kt           # NavHost: auth→main→metro_map→resources
        └── Screen.kt                  # Sealed class: 5 bottom nav routes
```

---

## Data Models

### Backend (Pydantic)

```python
class GoalBase(BaseModel):
    title: str          # "Master Android Dev"
    category: str       # "Career"

class GoalCreate(GoalBase):
    pass

class DayModuleBase(BaseModel):
    day_number: int     # 1-N
    topic: str          # "Basics of Variables"
    is_locked: bool     # True for Day 2+

class TaskBase(BaseModel):
    title: str          # "Watch Kotlin Tutorial"
    description: str?
    is_complete: bool

class ChatRequest(BaseModel):
    message: str
    is_voice_mode: bool = False
    session_id: str = "default_session"

class SubtaskUpdate(BaseModel):
    states: list[bool]  # [true, false, true, true]
```

### Android (Kotlin DTOs)

```kotlin
data class GoalDto(id, title, category, progress: Int, createdAt: String?, endDate: String?)
data class MetroMapDto(days: List<DayNodeDto>)
data class DayNodeDto(dayNumber, topic, isLocked, isCompleted, subTasks: List<String>, subTaskStates: List<Boolean>)
data class TaskDto(id, title, type: String, goalTitle, goalId?, dayNumber?, isCompleted)
data class UserProfileDto(stats: UserStatsDto, heatmap: Map<String, Int>, skillMatrix: Map<String, Int>, badges: List<String>)
data class UserStatsDto(level, currentXp, nextLevelXp, currentStreak, maxStreak, activeDays, mainTag)
data class ChatResponse(response: String, mode: String)
data class SessionResponse(sessionId, title, date)
data class HistoryMessage(text, isUser: Boolean, timestamp)
```

---

## Setup & Installation

### Prerequisites

| Dependency | Required | Notes |
|-----------|----------|-------|
| Python 3.11+ | Yes | Backend runtime |
| Neo4j | Yes | Graph database (local or Aura cloud) |
| Qdrant | Optional | Memory features degrade gracefully without it |
| AWS Account | Yes | Bedrock access for Claude Haiku 4.5 + Titan Embeddings |
| Android Studio | Yes | For building the mobile app |
| Firebase Project | Yes | Google Sign-In + Firestore user profiles |

### Backend Setup

```bash
cd backend

# 1. Install dependencies
pip install -r requirements.txt

# 2. Create .env (see Configuration section below)

# 3. Start Neo4j (local)
# Download from https://neo4j.com/download/ and start the service

# 4. (Optional) Start Qdrant
# docker run -p 6333:6333 qdrant/qdrant

# 5. Run the server
uvicorn app.main:app --reload --port 8000
```

### Docker Deployment

```bash
cd backend
docker build -t skillmorph-backend .
docker run -p 8080:8080 --env-file .env skillmorph-backend
```

### Android Setup

1. Open the root project in **Android Studio**
2. Place your `google-services.json` in `app/`
3. Update the base URL in `NetworkModule.kt` or `SkillMorphApi.kt` to your backend address
4. Build and run on a device/emulator (min SDK 26, target SDK 34)

**Shared debug keystore** is included at `app/keystore/debug.keystore` so all team members get the same signing config (required for Firebase Auth to work with Google Sign-In).

---

## Configuration

### Environment Variables (`backend/.env`)

```env
# === AWS Bedrock ===
AWS_ACCESS_KEY_ID=your-aws-access-key
AWS_SECRET_ACCESS_KEY=your-aws-secret-key
AWS_REGION=ap-south-1
BEDROCK_MODEL_ID=anthropic.claude-haiku-4-5-20251001-v1:0
BEDROCK_EMBED_MODEL_ID=amazon.titan-embed-text-v2:0

# === Neo4j ===
NEO4J_URI=bolt://localhost:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=your-neo4j-password

# === Qdrant (Optional) ===
QDRANT_URL=http://localhost:6333
QDRANT_API_KEY=your-qdrant-api-key
```

### LLM Configuration

| Parameter | Value | Location |
|-----------|-------|----------|
| Model | `anthropic.claude-haiku-4-5-20251001-v1:0` | `.env` → `BEDROCK_MODEL_ID` |
| Temperature | 0.3 | `llm_service.py` + `nodes.py` |
| Max Tokens | 4096 | `llm_service.py` + `nodes.py` |
| Embedding Model | `amazon.titan-embed-text-v2:0` | `.env` → `BEDROCK_EMBED_MODEL_ID` |
| Embedding Dimensions | 768 | `memory_service.py` (Qdrant collection) |
| Retry Strategy | Exponential backoff, 4 attempts | `llm_service.py` + `nodes.py` |

### Android Configuration

| Setting | Value | File |
|---------|-------|------|
| Base URL | `https://skillmorph-api.onrender.com/` | `SkillMorphApi.kt` |
| HTTP Timeout | 60 seconds (connect, read, write) | `NetworkModule.kt` |
| Room DB | `skillmorph_db` v2 | `DatabaseModule.kt` |
| Daily Briefing | 6:00 AM IST, repeats every 24h | `MainActivity.kt` |
| Virtual Day Cutoff | 3:30 AM IST | `AgentViewModel.kt` + `chat_session_service.py` |

---

## Testing

The backend includes two test files with mocked dependencies (no real API/DB calls):

### `tests/test_learning_resources.py`
- LLM service resource generation (valid JSON, empty fallback, invalid types)
- Graph CRUD save/retrieve (Neo4j mocked)
- API endpoint `GET /goals/{id}/resources` (cached vs generated)
- Agent tool `suggest_learning_resources`

### `tests/test_neural_blueprint.py`
- Memory save with user_id, category, tags
- Invalid category fallback to "general"
- User-scoped search with Qdrant filters
- Profile retrieval grouped by category
- Dark Matter Extractor (implicit signal extraction)
- LLM profile signal parsing
- Agent tools: `save_memory_note`, `search_user_context`
- FastAPI endpoints: `/memory/`, `/memory/search`, `/memory/profile`
- Background sync trigger on `/agent/chat`
- Tool registration in `ALL_TOOLS`

```bash
cd backend
pytest tests/ -v
```

---

## Key Dependencies

### Backend (`requirements.txt`)

| Package | Version | Purpose |
|---------|---------|---------|
| FastAPI | 0.128.0 | REST API framework with async support |
| Uvicorn | 0.40.0 | ASGI server |
| LangGraph | 1.0.6 | Agent state machine with tool loops |
| LangChain Core | 1.2.7 | Tool definitions, message types, base classes |
| langchain-aws | >=0.2.0 | `ChatBedrockConverse` LLM wrapper |
| boto3 | >=1.34.0 | AWS SDK for Bedrock runtime (Converse + InvokeModel) |
| Neo4j | 6.1.0 | Graph database driver |
| Qdrant Client | 1.16.2 | Vector database client |
| Pydantic | 2.12.5 | Data validation, serialization |
| pydantic-settings | 2.12.0 | Environment variable management |
| python-multipart | — | Form data support for FastAPI |
| protobuf | 5.29.5 | Serialization (LangGraph dependency) |

### Android (`gradle/libs.versions.toml`)

| Library | Version | Purpose |
|---------|---------|---------|
| Android Gradle Plugin | 8.4.1 | Build system |
| Kotlin | 1.9.23 | Language |
| KSP | 1.9.23-1.0.19 | Kotlin Symbol Processing (Hilt, Room) |
| Jetpack Compose BOM | 2024.05.00 | UI toolkit |
| Hilt | 2.51.1 | Dependency injection (compile-time) |
| Retrofit | 2.9.0 | HTTP client |
| OkHttp | 4.12.0 | HTTP engine + logging interceptor |
| Room | 2.6.1 | Local SQLite database |
| Firebase BOM | 33.0.0 | Auth + Firestore |
| Navigation Compose | 2.7.7 | Screen navigation |
| Lifecycle | 2.8.0 | ViewModel, LiveData, Lifecycle-aware components |
| WorkManager | — | Background scheduling (Daily Briefer) |

---

## Design Decisions

| Decision | Why |
|----------|-----|
| **Neo4j over SQL** | Goal → Day → Task chain is inherently graph-shaped; Metro Map visualization maps to graph traversal. `[:UNLOCKS]` relationships model sequential progression naturally. |
| **Claude via Bedrock over Gemini** | AWS Bedrock provides IAM-based auth (no API key rotation needed), regional deployment, and pay-per-use pricing. Previously used Gemini with multi-key rotation. |
| **Claude Haiku 4.5 over Sonnet** | Faster and cheaper for the volume of calls (roadmap gen, resource gen, memory summarization, dark matter extraction). Acceptable quality for structured JSON output. |
| **LangGraph over raw LangChain** | State machine control over the agent loop. Tool calls loop back to the agent automatically. `MemorySaver` provides conversation checkpointing per session. |
| **Qdrant over Pinecone/Chroma** | Self-hostable (cost-conscious), supports payload filtering (user_id scoping), and has a simple Python client. |
| **Bin-packing for scheduling** | AI generates topics + time estimates → Python packs into days. Decouples "what to learn" (AI) from "when to learn it" (algorithm). More predictable than asking LLM to plan N days directly. |
| **3:30 AM virtual day cutoff** | Aligns with IST sleep cycles. Users studying past midnight shouldn't see their session split into two days. |
| **Hilt over Koin** | Compile-time DI verification catches errors at build time, not runtime. Standard for production Android apps. |
| **Room for local cache** | Chat messages and profile data cached locally for instant UI rendering. API syncs in background. Prevents empty screens on slow networks. |
| **Optimistic UI** | Task checkboxes and subtasks update instantly in the UI, then sync to backend. Reverts on failure. Better UX than waiting for API round-trip. |
| **Background Dark Matter Extraction** | Runs as a `BackgroundTask` after every chat response. User never needs to explicitly "save" preferences. The system learns passively. |
| **Firebase Auth + x-user-id header** | Firebase handles Google Sign-In complexity. The UID is passed as a header on every request so the backend is stateless and doesn't need to verify Firebase tokens (simplified for MVP). |
.env
.gitignore
```
