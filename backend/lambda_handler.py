"""
AWS Lambda Handler for SkillMorph FastAPI Application

This module wraps the FastAPI app with Mangum, which converts AWS Lambda events
(from API Gateway, ALB, or Function URL) into ASGI format that FastAPI understands.
"""

from mangum import Mangum
from app.main import app

# Create Mangum handler
# lifespan="off" is important for Lambda - we don't want startup/shutdown events
# to run on every invocation (Lambda manages the lifecycle)
handler = Mangum(app, lifespan="off")

def lambda_handler(event, context):
    """
    AWS Lambda entry point
    
    Args:
        event: Lambda event (API Gateway, ALB, or Function URL format)
        context: Lambda context object
        
    Returns:
        Lambda response in the format expected by the trigger source
    """
    return handler(event, context)
