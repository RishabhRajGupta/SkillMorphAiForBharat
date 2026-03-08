# ============================================================
# UNUSED FILE — gemini_key.py
#
# get_gemini_response() was an early standalone helper for
# calling Gemini directly.  It is never imported anywhere in
# the project.  The same logic now lives inside
# LLMService._generate_text_safe() in services/llm_service.py,
# which rotates keys with proper retry/fallback.
#
# This file is safe to delete.
# ============================================================

# import google.generativeai as genai
# from config import settings
# import time
# import random
#
# def get_gemini_response(prompt: str) -> str:
#     """
#     Tries to get a response using random keys.
#     If one fails due to Rate Limit, it immediately tries another on 429 errors.
#     """
#     keys = settings.api_key_list.copy()
#     random.shuffle(keys)
#
#     for api_key in keys:
#         try:
#             genai.configure(api_key=api_key)
#             model = genai.GenerativeModel('gemini-2.5-flash')
#             response = model.generate_content(prompt)
#             return response.text
#         except Exception as e:
#             error_msg = str(e)
#             if "429" in error_msg or "ResourceExhausted" in error_msg:
#                 print(f"Key {api_key[:5]}... hit limit. Switching keys...")
#                 continue
#             else:
#                 raise e
#
#     raise Exception("All API keys are currently rate-limited. Please try again later.")