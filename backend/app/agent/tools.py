from langchain_core.tools import tool
from app.services.graph_crud import (
    create_goal_in_db, generate_timeline, get_tasks_for_date, 
    mark_day_complete, update_day_content, create_side_quest,
    delete_task_from_db, delete_goal_from_db, get_all_goals,
    get_goal_roadmap, mark_side_quest_complete,
    save_learning_resources, get_learning_resources, get_goal_title_and_category
)
from app.services.memory_service import memory_service  # Ensure this service exists
from app.schemas.graph_models import GoalCreate
from app.services.llm_service import llm_service
from datetime import date
import threading
import json

# --- EXISTING TOOLS (Keep create_new_goal & get_todays_tasks) ---

@tool
def create_new_goal(user_id: str, title: str, category: str, context: str = "", days: int = 0):
    """
    Creates a new learning goal or project roadmap.
    Use this when the user wants to learn something, prepare for a test, or start a habit.
    
    Args:
        title: The name of the skill (e.g., "Python", "Public Speaking").
        category: The domain (e.g., "Coding", "Soft Skills").
        context: Optional details about why they want to learn it.
        days: Optional number of days the user wants. 
              Pass the exact number if user mentions a duration (e.g., "7 days", "2 weeks" → 14).
              Leave as 0 if unspecified — AI will decide a reasonable course length.
    """
    print(f"🛠️ TOOL: Creating Goal '{title}' for {days or 'AI-decided'} days...")

    # Step 1: Determine number of days.
    # If user specified a duration, use it. Otherwise let the LLM decide via a quick prompt.
    if days and days > 0:
        total_days = days
    else:
        # Ask the LLM for a sensible course length (quick, single call)
        duration_prompt = (
            f"You are a curriculum designer. How many days would a complete beginner need "
            f"to learn '{title}' at a comfortable pace of ~60 minutes per day? "
            f"Reply with ONLY a single integer (e.g., 21). No explanation."
        )
        try:
            raw = llm_service._generate_text_safe(duration_prompt, default="21").strip()
            total_days = int(''.join(filter(str.isdigit, raw)) or "21")
            # Clamp to a sane range
            total_days = max(5, min(total_days, 60))
        except Exception:
            total_days = 21

    # Step 2: Create the Goal node in DB (instant, no LLM needed)
    new_goal = GoalCreate(title=title, category=category)
    result = create_goal_in_db(user_id, new_goal, total_planned_days=total_days)
    
    if not result:
        return "Error: Could not create goal in database."

    goal_id = result["id"]

    # Step 3: Create ALL skeleton Day nodes immediately (fast DB write, no LLM)
    # Metro Map can show the full N-day roadmap right away. Days show "Pending Generation".
    generate_timeline(goal_id, date.today(), total_days)

    # Step 4: Generate Day 1 + Day 2 real content in a BACKGROUND THREAD
    # This means we return immediately to the user — no waiting for AI generation.
    def run_bg_gen():
        print(f"🔴 Thread: Generating Day 1 + Day 2 for '{title}'...")
        
        # Generate Day 1
        try:
            content_d1 = llm_service.generate_day_topic(title, 1, context=context)
            update_day_content(goal_id, 1, content_d1["topic"], content_d1["sub_tasks"])
            print("✅ Day 1 content saved.")
        except Exception as e:
            print(f"❌ Day 1 generation failed: {e}")
            return  # Stop if Day 1 fails

        # Generate Day 2 (brief delay to be kind to API rate limits)
        import time
        time.sleep(1)
        try:
            content_d2 = llm_service.generate_day_topic(title, 2, context=context)
            update_day_content(goal_id, 2, content_d2["topic"], content_d2["sub_tasks"])
            print("✅ Day 2 content saved.")
        except Exception as e:
            print(f"❌ Day 2 generation failed: {e}")

    threading.Thread(target=run_bg_gen, daemon=True).start()

    duration_msg = f"{total_days} days" if (days and days > 0) else f"~{total_days} days"
    return (
        f"Goal '{title}' created successfully! "
        f"I've set up a {duration_msg} roadmap. "
        f"I'm generating your first tasks in the background — they'll be ready shortly. "
        f"Check your Metro Map!"
    )


@tool
def create_todo_item(user_id: str, title: str, due_date: str = None):
    """
    Creates a single one-off task (Side Quest).
    Use this for simple chores like 'Buy milk', 'Call mom', 'Email boss'.
    
    IMPORTANT: 'due_date' MUST be in 'YYYY-MM-DD' format (e.g., '2026-01-30').
    If the user says "next Friday" or "Jan 30", YOU must calculate the date.
    If no date is specified, use today's date.
    """
    if not due_date:
        due_date = date.today().isoformat()
        
    # Optional: Safety check to prevent "January 30th" from slipping through
    try:
        # This checks if it's valid ISO format
        date.fromisoformat(due_date)
    except ValueError:
        # Fallback: If LLM messed up, default to today or log error
        print(f"⚠️ Tool Error: Invalid date format '{due_date}'. Defaulting to today.")
        due_date = date.today().isoformat()
        
    create_side_quest(user_id, title, due_date)
    return f"Added task '{title}' to your list for {due_date}."

# --- NEW: SMARTER TOOLS ---

@tool
def get_task_list_json(user_id: str, target_date: str = None):
    """
    INTERNAL USE. Returns the raw JSON list of tasks for a SPECIFIC DATE.
    
    ARGS:
    - target_date: (Optional) YYYY-MM-DD. 
      - If user says "delete task on Jan 30th", pass '2026-01-30'.
      - If user says "delete my task" (implied today), pass None (defaults to today).
    """
    if not target_date:
        today_str = date.today().isoformat()
        target_date = today_str # Default to today
    else:
        # Pass today's date as the 3rd arg so Pacing Logic (shifting) still works correctly
        today_str = date.today().isoformat()

    # We use the same 'get_tasks_for_date' function, but now we can look at the future
    tasks = get_tasks_for_date(user_id, target_date, today_str)
    
    if not tasks:
        return json.dumps([])

    # Return structured list so Agent can find the ID
    return json.dumps([{
        "id": t['id'], 
        "title": t['title'], 
        "goal": t.get('goal_title', 'Side Quest'),
        "type": t['type'],
        "goal_id": t.get('goal_id'),       
        "day_number": t.get('day_number'), 
        "scheduled_date": target_date 
    } for t in tasks])

@tool
def confirm_task_completion(user_id: str, task_id: str, task_type: str, goal_id: str = None, day_number: int = None):
    """
    Marks a specific task as complete. 
    """
    if task_type == 'GOAL':
        if not goal_id or not day_number:
            return "Error: Goal ID and Day Number required for Goal Tasks."
            
        # 1. Mark Database as Complete
        mark_day_complete(goal_id, day_number)
        
        # 2. 🔴 NEW: Trigger Rolling Generation (Background Thread)
        # Just like the API endpoint, we generate content for (Current Day + 2)
        def run_rolling_gen():
            next_day_target = day_number + 2
            print(f"🧵 Tool: Triggering generation for Day {next_day_target}...")
            
            try:
                # We need the goal title to generate context
                goal_data = get_goal_roadmap(goal_id)
                if not goal_data: 
                    return

                title = goal_data["title"]
                
                # Generate content using LLM
                content = llm_service.generate_day_topic(title, next_day_target)
                
                # Save to DB
                update_day_content(goal_id, next_day_target, content["topic"], content["sub_tasks"])
                print(f"✅ Tool: Day {next_day_target} content ready.")
            except Exception as e:
                print(f"❌ Tool Gen Error: {e}")

        # Start non-blocking thread
        threading.Thread(target=run_rolling_gen).start()

        return "Goal task marked complete! I've also started preparing the content for upcoming days."
        
    elif task_type == 'SIDE_QUEST':
        mark_side_quest_complete(task_id) 
        return "Side quest marked complete."
        
    return "Error: Unknown task type."

@tool
def save_memory_note(user_id: str, content: str, category: str = "general", tags: str = ""):
    """
    Saves important user details to long-term memory with categorization.
    
    WHEN TO USE (Auto-detect these triggers):
    - User shares a PREFERENCE: "I prefer TypeScript over JavaScript" -> category="preference"
    - User mentions their TECH STACK: "I use Fedora with WSL2" -> category="tech_stack"
    - User makes a DESIGN DECISION: "Let's use Redis for caching" -> category="project_decision"
    - User hits a PAIN POINT: "Docker GPU keeps failing" -> category="pain_point"
    - User shares PERSONAL facts: "I'm preparing for a hackathon" -> category="personal"
    - User reveals LEARNING STYLE: "I want to understand the math behind this" -> category="learning_style"
    - User describes their ENVIRONMENT: "I'm on Windows with 8GB RAM" -> category="environment"
    
    WHEN NOT TO USE:
    - One-off typos or trivial syntax fixes
    - Generic greetings or small talk
    - Information that changes every conversation
    
    Args:
        content: The factual statement to save (e.g., "User prefers manual backprop over high-level wrappers").
        category: One of: preference, tech_stack, project_decision, pain_point, personal, learning_style, environment, general
        tags: Comma-separated related concepts (e.g., "SkillMorph,Neo4j,backend"). Links this memory to other topics.
    """
    tag_list = [t.strip() for t in tags.split(",") if t.strip()] if tags else []
    result = memory_service.save_memory(content, user_id=user_id, category=category, tags=tag_list)
    return f"Memory saved [{category}]: {result.get('summary', content)}"


@tool
def search_user_context(user_id: str, query: str, category: str = ""):
    """
    INTERNAL USE. Searches the user's long-term memory for relevant context BEFORE answering.
    
    Use this PROACTIVELY when:
    - User asks about a topic they may have discussed before
    - You need to know their environment, preferences, or past decisions
    - User references a project name or technology
    
    This enables "Senior RAG" — searching for the user's profile, not just keywords.
    
    Args:
        query: What to search for (e.g., "GPU setup" or "Python preferences").
        category: Optional filter (preference, tech_stack, pain_point, etc.)
    """
    results = memory_service.search_memory(query, user_id=user_id, category=category or None, limit=5)
    if not results:
        return json.dumps({"matches": [], "note": "No prior context found for this user."})
    return json.dumps({"matches": results})


@tool
def get_active_goals_json(user_id: str):
    """
    INTERNAL USE. Returns a JSON list of all active goals with their IDs.
    Use this to find the 'goal_id' when the user wants to delete or modify a goal.
    """
    goals = get_all_goals(user_id)
    # Simplify for the LLM (save tokens)
    simple_list = [{"title": g["title"], "id": g["id"]} for g in goals]
    return json.dumps(simple_list)

@tool
def delete_specific_goal(user_id: str, goal_id: str):
    """
    Permanently deletes a Goal and all its history.
    IMPORTANT: You must first call 'get_active_goals_json' to find the correct goal_id.
    """
    delete_goal_from_db(user_id, goal_id)
    return "Goal deleted successfully."

@tool
def delete_specific_task(user_id: str, task_id: str):
    """
    Permanently deletes a Side Quest (To-Do item).
    IMPORTANT: You must first call 'get_task_list_json' to find the correct task_id.
    """
    delete_task_from_db(user_id, task_id)
    return "Task deleted successfully."

@tool
def suggest_learning_resources(user_id: str, goal_title: str, goal_id: str = None):
    """
    Suggests learning resources (courses, articles, videos, practice exercises) for a goal.
    Use this when the user asks for recommendations, study materials, or resources for a skill they're learning.
    Examples:
    - "What resources can help me learn Python?"
    - "Suggest some courses for my React goal"
    - "I need practice exercises for data structures"
    
    IMPORTANT: First call `get_active_goals_json` to get the goal_id if not provided.
    """
    # If we have a goal_id, try to get cached resources first
    if goal_id:
        cached = get_learning_resources(goal_id)
        if cached:
            return json.dumps(cached, indent=2)
    
    # Generate fresh resources
    resources = llm_service.generate_learning_resources(
        goal_title=goal_title
    )
    
    if not resources:
        return "Sorry, I couldn't find resources for this topic right now."
    
    # Cache if we have a goal_id
    if goal_id:
        save_learning_resources(goal_id, resources)
    
    # Format a nice response for the agent
    return json.dumps(resources, indent=2)


# Updated Tool List
ALL_TOOLS = [
    create_new_goal, 
    create_todo_item, 
    get_task_list_json,
    confirm_task_completion,
    save_memory_note,
    search_user_context,
    get_active_goals_json,
    delete_specific_goal,
    delete_specific_task,
    suggest_learning_resources
]