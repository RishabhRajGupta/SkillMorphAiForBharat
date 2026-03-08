from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver
from app.agent.state import AgentState
from app.agent.nodes import agent_node, tool_node

# 1. Initialize Graph & Memory
workflow = StateGraph(AgentState)
memory = MemorySaver()

# 2. Add Nodes
# 'agent': The LLM that decides what to do.
# 'tools': The function executor that talks to your DB.
workflow.add_node("agent", agent_node)
workflow.add_node("tools", tool_node)

# 3. Define Entry Point
# We always start by letting the Agent think.
workflow.set_entry_point("agent")

# 4. Define Conditional Logic
def should_continue(state):
    """
    Decides the next step:
    - If the Agent generated a 'tool_call', go to 'tools'.
    - If the Agent just replied with text, we are done -> END.
    """
    messages = state["messages"]
    last_message = messages[-1]
    
    # Check if the last message has tool calls
    if hasattr(last_message, "tool_calls") and last_message.tool_calls:
        return "tools"
    
    return END

workflow.add_conditional_edges(
    "agent",
    should_continue,
    {
        "tools": "tools",
        END: END
    }
)

# 5. Define Tool Loop Back
# After the tool runs, we ALWAYS go back to the agent so it can read the result 
# and formulate a final answer for the user.
workflow.add_edge("tools", "agent")

# 6. Compile
agent_app = workflow.compile(checkpointer=memory)