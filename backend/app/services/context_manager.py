"""
Context Manager for Bedrock Conversations

Manages the 200K token context window efficiently for Claude.
Uses character-based estimation (~4 chars per token for English text).
"""

from typing import List, Dict, Optional


class ConversationContextManager:
    """Manages conversation context within Bedrock's token limits."""

    def __init__(self, max_context_tokens: int = 190000):
        self.max_context_tokens = max_context_tokens
        # Reserve tokens for system prompt + current message + response
        self.reserved_tokens = 10000

    def prepare_context(
        self,
        current_message: str,
        system_prompt: str,
        conversation_history: Optional[List[Dict[str, str]]] = None,
        memories: Optional[List[str]] = None,
    ) -> tuple:
        """
        Prepares system prompt and conversation history to fit within token limits.
        Returns (system_prompt, trimmed_history).
        """
        conversation_history = conversation_history or []
        memories = memories or []

        # Build system prompt with memory context
        full_system = system_prompt
        if memories:
            memory_block = "\n".join(f"- {m}" for m in memories[:10])
            full_system += f"\n\nRelevant context from past conversations:\n{memory_block}"

        # Calculate remaining budget
        system_tokens = self._estimate_tokens(full_system)
        message_tokens = self._estimate_tokens(current_message)
        available = self.max_context_tokens - system_tokens - message_tokens - self.reserved_tokens

        # Trim history to fit
        trimmed = self._trim_history(conversation_history, max_tokens=max(available, 0))

        return full_system, trimmed

    def _estimate_tokens(self, text: str) -> int:
        """Estimate token count (~4 chars per token for English)."""
        return len(text) // 4

    def _trim_history(
        self,
        history: List[Dict[str, str]],
        max_tokens: int,
    ) -> List[Dict[str, str]]:
        """
        Trims conversation history to fit within token budget.
        Keeps the first message (for context) and as many recent messages as possible.
        """
        if not history:
            return []

        total_tokens = sum(self._estimate_tokens(m.get("content", "")) for m in history)
        if total_tokens <= max_tokens:
            return history

        # Keep first message for context, trim from the middle
        first_message = history[0]
        remaining = history[1:]
        first_tokens = self._estimate_tokens(first_message.get("content", ""))
        budget = max_tokens - first_tokens

        # Add messages from the end (most recent) until budget exhausted
        trimmed = []
        for msg in reversed(remaining):
            msg_tokens = self._estimate_tokens(msg.get("content", ""))
            if budget - msg_tokens < 0:
                break
            trimmed.insert(0, msg)
            budget -= msg_tokens

        return [first_message] + trimmed if trimmed else [first_message]


context_manager = ConversationContextManager()
