import os
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    PROJECT_NAME: str = "SkillMorph"
    VERSION: str = "0.1.0"
    
    # AWS Bedrock Configuration
    AWS_ACCESS_KEY_ID: str
    AWS_SECRET_ACCESS_KEY: str
    AWS_REGION: str = "us-east-1"
    BEDROCK_MODEL_ID: str = "global.anthropic.claude-haiku-4-5-20251001-v1:0"
    BEDROCK_EMBED_MODEL_ID: str = "amazon.titan-embed-text-v2:0"
    EMBEDDING_DIMENSIONS: int = 1024
    
    # DB keys
    NEO4J_URI: str 
    NEO4J_USER: str
    NEO4J_PASSWORD: str
    
    QDRANT_URL: str
    QDRANT_API_KEY: str

    model_config = SettingsConfigDict(env_file=".env", case_sensitive=True, extra="ignore")

    @property
    def api_key_list(self) -> List[str]:
        """Returns a list of Gemini API keys split by commas."""
        if not self.GEMINI_API_KEYS:
            return []
        return [key.strip() for key in self.GEMINI_API_KEYS.split(",") if key.strip()]

    # UNUSED — get_random_key
    # Key selection is done inline in LLMService._generate_text_safe()
    # and get_llm_response_with_retry() in nodes.py using settings.api_key_list.
    # Safe to delete.
    # def get_random_key(self) -> str:
    #     keys = self.api_key_list
    #     if not keys:
    #         raise ValueError("No Gemini API keys available in .env file!.")
    #     return random.choice(keys)
    
settings = Settings()