from typing import TypedDict, Annotated, List, Union
from langchain_core.messages import BaseMessage
import operator

class AgentState(TypedDict):
    # The history of the chat (User + AI messages)
    # Annotated[..., operator.add] means "Append new messages to the list"
    messages: Annotated[List[BaseMessage], operator.add]
    
    # Context flags
    is_voice_mode: bool = False
    user_id: str
    user_mood: str = "neutral"
    
    # Data slots (Filled by the extractors)
    current_goal: dict = None
    next_step: str = "router" # Controls where the graph goes next