from datetime import date
import datetime
import json
import time
from langchain_aws import ChatBedrockConverse
from langchain_core.messages import SystemMessage, ToolMessage, AIMessage
from app.agent.tools import ALL_TOOLS
from app.agent.state import AgentState
from app.core.config import settings

# --- HELPER: BEDROCK LLM WITH EXPONENTIAL BACKOFF ---
def get_llm_response_with_retry(messages, tools):
    """
    Invokes Amazon Bedrock via ChatBedrockConverse with tool binding.
    Implements exponential backoff for throttling errors.
    """
    llm = ChatBedrockConverse(
        model=settings.BEDROCK_MODEL_ID,
        region_name=settings.AWS_REGION,
        aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
        aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
        temperature=0.3,
        max_tokens=4096,
    )
    
    llm_with_tools = llm.bind_tools(tools)
    
    max_retries = 4
    last_error = None
    
    for attempt in range(max_retries):
        try:
            response = llm_with_tools.invoke(messages)
            return response
        except Exception as e:
            error_str = str(e).lower()
            if "throttl" in error_str or "too many" in error_str or "429" in error_str:
                wait = 2 ** attempt
                print(f"⚠️ Bedrock throttled (attempt {attempt + 1}/{max_retries}). Retrying in {wait}s...")
                last_error = e
                time.sleep(wait)
                continue
            else:
                print(f"❌ LLM Error: {e}")
                raise e
    
    raise Exception(f"Bedrock rate limit exhausted after {max_retries} retries. Last error: {last_error}")

# --- 1. THE AGENT NODE ---
def agent_node(state: AgentState):
    print("🤖 Agent Node Thinking...")
    messages = state["messages"]
    user_id = state.get("user_id", "test_user_123")
    is_voice_mode = state.get("is_voice_mode", False)
    
    system_prompt = f"""
    You are SkillMorph, an intelligent productivity agent companion and expert tutor.
    Current User ID: {user_id}
    Current Date: {date.today().isoformat()}  <-- GIVE IT TODAY'S DATE
    
    CAPABILITIES & PROTOCOLS:
    
    1. **MANAGING TASKS (COMPLETION):**
        Standard:
           i. On Completing a task
           - If the user says "I bought milk" or "Finished the python video":
           - FIRST, call `get_task_list_json` to see what is on the list.
           - SECOND, analyze the list to find the best semantic match (e.g., matching "bought milk" to "Buy Milk").
           - THIRD, call `confirm_task_completion` with the ID from the list.
           ii. Deleting
            - "Delete the milk task" -> `get_task_list_json` -> `delete_specific_task`
        Future: "I finished tomorrow's game dev task" -> 
         1. Calculate date: Tomorrow is { (date.today() + datetime.timedelta(days=1)).isoformat() }.
         2. Call `get_task_list_json(target_date='YYYY-MM-DD')`.
         3. The list will contain the 'day_number' and 'goal_id'.
         4. Call `confirm_task_completion` using those specific values from the list.
       
    2. **CREATING TASKS:**
       - "I want to learn X" -> `create_new_goal(title="X", category="...", days=0)`
       - "I want to do LeetCode for 7 days" -> `create_new_goal(title="LeetCode", category="Coding", days=7)`
       - "Learn Python in 2 weeks" -> `create_new_goal(title="Python", category="Coding", days=14)`
       - If the user specifies a duration (days, weeks), ALWAYS convert it to a number of days and pass it as `days`.
       - If no duration is mentioned, pass `days=0` (AI decides the natural course length).
       - "Remind me to buy milk" -> `create_todo_item`
       - "Remind me to buy milk on Jan 30th" -> `create_todo_item(title="Buy milk", due_date="2026-01-30")`
       - ALWAYS convert relative dates (tomorrow, next week) to YYYY-MM-DD.
       
    3. **MANAGING GOALS:**
       - "Create a goal to learn Python" -> `create_new_goal`
       - "Delete my Python goal" -> 
           1. Call `get_active_goals_json` to find the ID for "Python".
           2. Call `delete_specific_goal` with that ID.

    4. **MEMORY — NEURAL BLUEPRINT PROTOCOL:**
       You maintain a "Dynamic Neural Blueprint" of the user's identity and preferences.
       
       A. **PROACTIVE RETRIEVAL (Before Answering):**
          - When the user asks about a technical topic, FIRST call `search_user_context` to check 
            for their known preferences, stack, and past decisions on that topic.
          - Search not just for keywords, but for the USER'S CONTEXT.
            BAD:  search for "How to fix Docker GPU error"
            GOOD: search for "user environment, GPU setup, Docker constraints"
       
       B. **AUTO-SAVE TRIGGERS (While Responding):**
          Call `save_memory_note` with the appropriate category when you detect:
          
          | Trigger | Category | Example |
          |---------|----------|---------|
          | User states a tool/library preference | preference | "I prefer Vim over VS Code" |
          | User mentions tech they use | tech_stack | "I'm using FastAPI with Python 3.11" |
          | A design choice is made in conversation | project_decision | "Let's use Redis instead of Memcached" |
          | User struggled with a specific error | pain_point | "The CORS issue with Firebase keeps breaking" |
          | User shares personal/career info | personal | "I'm preparing for my thesis defense" |
          | User reveals how they like to learn | learning_style | "Show me the math, not the API" |
          | User describes their hardware/OS/env | environment | "Running on WSL2 with 16GB RAM" |
          
       C. **DISCARD THRESHOLD — Do NOT save:**
          - One-off typos or trivial syntax questions
          - Generic greetings or small talk
          - Information the AI generated (only save USER signals)
          - Transient errors that won't recur
       
       D. **CONTEXTUAL LINKING (tags parameter):**
          Always include relevant tags to link memories. If a user discusses a Neo4j schema change 
          for SkillMorph, tags should be: "SkillMorph,Neo4j,backend,schema"
    
    5. **LEARNING RESOURCES:**
       - If the user asks for study materials, courses, tutorials, or practice exercises:
         1. Call `get_active_goals_json` to find the matching goal and its ID.
         2. Call `suggest_learning_resources(user_id, goal_title, goal_id)`.
         3. Format the response nicely with resource names, platforms, and descriptions.
       
    6. **GENERAL:**
       - Be concise.
       - ALWAYS pass the 'user_id' provided above.

    IMPORTANT:
    - Never guess IDs. Always fetch the list first.
    - ALWAYS pass the 'user_id'.
    - Dates must ALWAYS be YYYY-MM-DD.
    """
    
    # Voice mode: optimize responses for text-to-speech output
    if is_voice_mode:
        system_prompt += """
    
    VOICE MODE ACTIVE:
    - Keep responses under 3 sentences maximum.
    - Do NOT use markdown formatting (no **, no ##, no ```, no bullet lists).
    - Do NOT use code blocks or technical syntax in your response text.
    - Use natural, conversational speech patterns.
    - Spell out abbreviations (say "application" not "app", "database" not "DB").
    - Use simple sentence structures optimized for spoken delivery.
    """

    # Prepend System Message to history
    # We create a temporary list so we don't mess up the actual state history permanently
    full_context = [SystemMessage(content=system_prompt)] + messages

    # 2. Call LLM with Rotation Logic
    try:
        response = get_llm_response_with_retry(full_context, ALL_TOOLS)
        return {"messages": [response]}
    except Exception as e:
        return {"messages": [AIMessage(content="I'm sorry, my brain is overloaded right now (Rate Limit). Please try again in a moment.")]}


# --- 2. THE TOOL NODE ---
def tool_node(state: AgentState):
    """
    Executes the tool calls requested by the Agent.
    """
    print("🔧 Tool Node Executing...")
    messages = state["messages"]
    last_message = messages[-1]

    # If no tool calls, this node shouldn't have been hit, but safe check:
    if not last_message.tool_calls:
        return {"messages": []}

    results = []
    
    # Iterate over all tool calls (Agent might want to do 2 things at once)
    for tool_call in last_message.tool_calls:
        action_name = tool_call["name"]
        tool_args = tool_call["args"]
        call_id = tool_call["id"]
        
        print(f"  👉 Calling: {action_name} with {tool_args}")

        # Find the matching tool object
        selected_tool = next((t for t in ALL_TOOLS if t.name == action_name), None)
        
        if selected_tool:
            try:
                # Execute the tool
                # Note: We rely on the Agent passing the user_id in 'tool_args'
                output = selected_tool.invoke(tool_args)
            except Exception as e:
                output = f"Error executing tool: {str(e)}"
        else:
            output = f"Error: Tool '{action_name}' not found."

        # Create the ToolMessage result
        results.append(ToolMessage(
            tool_call_id=call_id,
            name=action_name,
            content=str(output)
        ))

    return {"messages": results}