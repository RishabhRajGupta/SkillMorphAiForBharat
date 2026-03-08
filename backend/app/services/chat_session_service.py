from datetime import datetime, timedelta
from app.services.graph_crud import graph_db

def get_virtual_date() -> str:
    """
    Returns the date string (YYYY-MM-DD).
    Logic: A new day starts at 3:30 AM.
    If it is 2:00 AM on Jan 24, it counts as Jan 23.
    """
    now = datetime.now()
    cutoff_time = now.replace(hour=3, minute=30, second=0, microsecond=0)
    
    if now < cutoff_time:
        # It's technically today, but logically yesterday
        return (now - timedelta(days=1)).date().isoformat()
    
    # It's past 3:30 AM, so it is today
    return now.date().isoformat()

def get_or_create_daily_session(user_id: str):
    """
    Finds the session node for the 'Virtual Today'.
    If it doesn't exist, creates it.
    """
    virtual_date = get_virtual_date()
    
    query = """
    MERGE (u:User {id: $user_id})
    
    // Try to find existing session for this specific date
    MERGE (s:Session {date: $virtual_date, user_id: $user_id})
    ON CREATE SET 
        s.id = randomUUID(),
        s.created_at = datetime(),
        s.title = "Chat for " + $virtual_date
    
    // Ensure relationship exists
    MERGE (u)-[:HAS_SESSION]->(s)
    
    RETURN s.id as session_id, s.title as title, s.date as date
    """
    
    with graph_db.get_session() as session:
        result = session.run(query, user_id=user_id, virtual_date=virtual_date)
        record = result.single()
    
        if record:
            return dict(record)
        return None

def save_message_to_session(session_id: str, sender: str, text: str):
    """
    Saves a message node linked to the session.
    sender should be 'user' or 'ai'.
    """
    query = """
    MATCH (s:Session {id: $session_id})
    CREATE (m:Message {
        id: randomUUID(),
        text: $text,
        sender: $sender,
        timestamp: datetime()
    })
    CREATE (s)-[:HAS_MESSAGE]->(m)
    """
    with graph_db.get_session() as session:
        session.run(query, session_id=session_id, sender=sender, text=text)

import json # <--- Make sure this is imported at the top

def get_session_history(session_id: str):
    """
    Fetches history, removes system prompts, and cleans AI JSON.
    """
    query = """
    MATCH (s:Session {id: $session_id})-[:HAS_MESSAGE]->(m)
    WHERE m.sender <> 'system'  // <--- 1. BLOCK SYSTEM MESSAGES
    RETURN m.text as text, m.sender as sender, m.timestamp as timestamp
    ORDER BY m.timestamp ASC
    """
    
    with graph_db.get_session() as session:
        result = session.run(query, session_id=session_id)
        
        clean_history = []
        
        for record in result:
            raw_text = record["text"]
            sender = record["sender"]
            
            final_text = raw_text

            # 2. CLEAN AI MESSAGES (Parse JSON)
            if sender == "ai":
                try:
                    # Attempt to parse the stored JSON
                    # We look for the "display_text" field
                    data = json.loads(raw_text)
                    if isinstance(data, dict) and "display_text" in data:
                        final_text = data["display_text"]
                    elif isinstance(data, dict) and "response" in data:
                         # Fallback if you used older schema
                        final_text = data["response"]
                except:
                    # If it's not JSON, just use the text as is
                    pass

            # 3. FIX NEWLINES (The \n bug)
            # If the DB stored literal "\n" characters, we convert them to real newlines
            if final_text:
                final_text = final_text.replace("\\n", "\n")

            clean_history.append({
                "text": final_text,
                "isUser": (sender == "user"), # <--- 4. ENSURE CORRECT SIDE
                "timestamp": str(record["timestamp"])
            })
            
        return clean_history
    
    
def get_user_sessions(user_id: str):
    """
    Returns a list of all chat sessions for the sidebar history.
    """
    query = """
    MATCH (u:User {id: $user_id})-[:HAS_SESSION]->(s:Session)
    RETURN s.id as id, s.title as title, s.date as date
    ORDER BY s.date DESC
    """
    with graph_db.get_session() as session:
        result = session.run(query, user_id=user_id)
        return [
            # 🔴 WAS: "id": r["id"]
            # 🟢 FIX: "session_id": r["id"] (Matches Android @SerializedName("session_id"))
            {"session_id": r["id"], "title": r["title"], "date": r["date"]} 
            for r in result
        ]