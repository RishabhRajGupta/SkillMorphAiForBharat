import uuid
from typing import Optional
from app.services.neo4j_service import graph_db
from app.schemas.graph_models import GoalCreate, TaskCreate
from datetime import date, timedelta, datetime

def create_goal_in_db(user_id: str, goal_data: GoalCreate, total_planned_days: Optional[int] = None):
    """
    Creates the Goal Node and links it to the User.
    Optionally stores total_planned_days so the frontend knows the full course length.
    """
    goal_id = str(uuid.uuid4())
    query = """
    MERGE (u:User {id: $user_id})
    CREATE (g:Goal {
        id: $goal_id,
        title: $title,
        category: $category,
        created_at: datetime(),
        total_tasks: 0,
        completed_tasks: 0,
        progress_percentage: 0,
        total_planned_days: $total_planned_days
    })
    MERGE (u)-[:HAS_GOAL]->(g)
    RETURN g.id as id, g.title as title
    """
    with graph_db.get_session() as session:
        result = session.run(query, user_id=user_id, goal_id=goal_id, title=goal_data.title, category=goal_data.category, total_planned_days=total_planned_days)
        return result.single()


def generate_timeline(goal_id: str, start_date: date, duration_days: int):
    """
    STEP 1 of goal creation: instantly creates all N Day skeleton nodes.
    Topic is 'Pending Generation' — a background thread fills in real content.
    Only Day 1 gets a scheduled date; all others are floating until unlocked.
    """
    days_data = []
    for i in range(1, duration_days + 1):
        scheduled = start_date.isoformat() if i == 1 else None
        days_data.append({
            "day_number": i,
            "scheduled_date": scheduled,
            "topic": "Pending Generation",
            "is_locked": i > 1
        })

    query = """
    MATCH (g:Goal {id: $goal_id})
    UNWIND $days_data as day_item

    CREATE (d:Day {
        id: randomUUID(),
        day_number: day_item.day_number,
        topic: day_item.topic,
        scheduled_date: day_item.scheduled_date,
        is_locked: day_item.is_locked,
        is_completed: false,
        sub_tasks: []
    })
    MERGE (g)-[:HAS_DAY]->(d)

    WITH d
    ORDER BY d.day_number
    WITH collect(d) as days

    FOREACH (i in range(0, size(days)-2) |
        FOREACH (d1 in [days[i]] |
            FOREACH (d2 in [days[i+1]] |
                MERGE (d1)-[:UNLOCKS]->(d2)
            ))
    )
    RETURN size(days) as days_created
    """
    with graph_db.get_session() as session:
        result = session.run(query, goal_id=goal_id, days_data=days_data)
        record = result.single()
        return record["days_created"] if record else 0


def create_initial_days(goal_id: str, day1: dict, day2: dict, start_date: date):
    """
    Seeds ONLY Day 1 and Day 2 for a new goal.
    Day 1 is unlocked and scheduled for today.
    Day 2 is locked and floating (no date yet).
    Remaining days are generated progressively via N+2 rolling generation
    each time the user completes a day.

    Args:
        day1: dict with 'topic' (str) and 'sub_tasks' (list[str])
        day2: dict with 'topic' (str) and 'sub_tasks' (list[str])
        start_date: today's date (used to anchor Day 1)
    """
    query = """
    MATCH (g:Goal {id: $goal_id})

    // Create Day 1 — unlocked, anchored to today
    CREATE (d1:Day {
        id: randomUUID(),
        day_number: 1,
        topic: $day1_topic,
        sub_tasks: $day1_sub_tasks,
        scheduled_date: $start_date,
        is_locked: false,
        is_completed: false
    })
    MERGE (g)-[:HAS_DAY]->(d1)

    // Create Day 2 — locked, no date yet
    CREATE (d2:Day {
        id: randomUUID(),
        day_number: 2,
        topic: $day2_topic,
        sub_tasks: $day2_sub_tasks,
        scheduled_date: null,
        is_locked: true,
        is_completed: false
    })
    MERGE (g)-[:HAS_DAY]->(d2)

    // Link them
    MERGE (d1)-[:UNLOCKS]->(d2)

    RETURN d1.id as day1_id, d2.id as day2_id
    """
    with graph_db.get_session() as session:
        result = session.run(
            query,
            goal_id=goal_id,
            day1_topic=day1["topic"],
            day1_sub_tasks=day1["sub_tasks"],
            day2_topic=day2["topic"],
            day2_sub_tasks=day2["sub_tasks"],
            start_date=start_date.isoformat()
        )
        record = result.single()
        return {"day1_id": record["day1_id"], "day2_id": record["day2_id"]} if record else None
          

def get_goal_roadmap(goal_id: str):
    """
    Fetches the Goal metadata + All Day nodes sorted by day_number.
    """
    query = """
    MATCH (g:Goal {id: $goal_id})
    OPTIONAL MATCH (g)-[:HAS_DAY]->(d:Day)
    RETURN 
        g.title as title, 
        g.category as category,
        collect({
            day_number: d.day_number,
            topic: d.topic,
            is_locked: d.is_locked,
            is_completed: coalesce(d.is_completed, false),
            sub_tasks: coalesce(d.sub_tasks, []),  
            sub_task_states: coalesce(d.sub_task_states, [])
        }) as days
    """
    with graph_db.get_session() as session:
        result = session.run(query, goal_id=goal_id)
        record = result.single()
        
        if not record:
            return None
            
        # Neo4j 'collect' does not guarantee order, so we sort in Python
        data = record.data()
        # Sort days 1..30
        data["days"] = sorted(data["days"], key=lambda x: x["day_number"])
        return data

def update_subtask_states(goal_id: str, day_number: int, states: list[bool]):
    """
    Saves the checkbox states. Includes Debugging Logs.
    """
    query = """
    MATCH (g:Goal {id: $goal_id})-[:HAS_DAY]->(d:Day {day_number: $day_number})
    SET d.sub_task_states = $states
    RETURN d.id as id, d.sub_task_states as saved_states
    """
    with graph_db.get_session() as session:
        result = session.run(query, goal_id=goal_id, day_number=day_number, states=states)
        record = result.single()
        
        if record:
            print(f"✅ DB SUCCESS: Day {day_number} updated to {record['saved_states']}")
        else:
            print(f"❌ DB ERROR: Could not find Day {day_number} for Goal {goal_id}")

            
def get_all_goals(user_id: str):
    """
    Returns goals with:
    - calculated 'projected_end_date' (Today + Remaining Days)
    - correct progress percentage
    """
    query = """
    MATCH (u:User {id: $user_id})-[r:HAS_GOAL]->(g:Goal)
    OPTIONAL MATCH (g)-[:HAS_DAY]->(d:Day)
    
    WITH g, 
         count(d) as total, 
         sum(CASE WHEN d.is_completed THEN 1 ELSE 0 END) as done
         
    // Calculate Remaining Days
    WITH g, total, done, (total - done) as remaining
    
    RETURN 
        g.id as id, 
        g.title as title, 
        g.category as category, 
        g.progress_percentage as progress,
        toString(g.created_at) as created_at,
        // Dynamic End Date: Today + Remaining Days
        toString(date() + duration({days: remaining})) as projected_end_date
    """
    with graph_db.get_session() as session:
        result = session.run(query, user_id=user_id)
        return [record.data() for record in result]

def mark_day_complete(goal_id: str, day_number: int):
    """
    Marks the day as complete and stamps it with TODAY'S date.
    This ensures it stays visible in 'History' for today.
    """
    query = """
    MATCH (g:Goal {id: $goal_id})-[:HAS_DAY]->(current:Day {day_number: $day_number})
    
    // 1. Mark Done & Save Date (YYYY-MM-DD string format)
    SET current.is_completed = true, 
        current.completed_date = toString(date())
    
    WITH g, current
    
    // 2. Unlock Next Day (But don't schedule it yet, the reader query handles that)
    OPTIONAL MATCH (current)-[:UNLOCKS]->(next_day:Day)
    SET next_day.is_locked = false
    
    // 3. Recalculate Progress
    WITH g
    MATCH (g)-[:HAS_DAY]->(d:Day)
    WITH g, count(d) as total, sum(CASE WHEN d.is_completed THEN 1 ELSE 0 END) as done
    
    SET g.completed_tasks = done, 
        g.progress_percentage = toInteger((done / toFloat(total)) * 100)
    
    RETURN g.progress_percentage as progress
    """
    with graph_db.get_session() as session:
        result = session.run(query, goal_id=goal_id, day_number=day_number)
        record = result.single()
        return record["progress"] if record else 0
    
def mark_side_quest_complete(task_id: str):
    """
    Marks a standalone task as complete.
    """
    query = """
    MATCH (t:Task {id: $task_id})
    SET t.is_completed = true
    RETURN t.title
    """
    with graph_db.get_session() as session:
        session.run(query, task_id=task_id)
        print(f"✅ Side Quest {task_id} marked complete.")
    

def update_day_content(goal_id: str, day_number: int, topic: str, sub_tasks: list):
    """
    Overwrites the "Pending Generation" placeholder with real AI content.
    """
    query = """
    MATCH (g:Goal {id: $goal_id})-[:HAS_DAY]->(d:Day {day_number: $day_number})
    SET d.topic = $topic, 
        d.sub_tasks = $sub_tasks
    RETURN d.topic
    """
    with graph_db.get_session() as session:
        session.run(query, goal_id=goal_id, day_number=day_number, topic=topic, sub_tasks=sub_tasks)
        print(f"✅ Updated Day {day_number} with: {topic}")

def get_tasks_for_date(user_id: str, target_date: str, today_date: str):
    """
    Fetches tasks for a specific date with advanced Pacing Logic.
    
    ARGS:
      - user_id: The user's UUID.
      - target_date: The calendar date the user is looking at (e.g., "2026-01-25").
      - today_date: The user's ACTUAL local date (e.g., "2026-01-21"). 
        (Crucial for calculating the correct "Tomorrow" regardless of Server Time).

    RETURNS:
      - A mixed list of Side Quests (one-offs) and Main Quests (Goals).
    """
    query = """
    MATCH (u:User {id: $user_id})
    
    // ==========================================================
    // 1. FETCH SIDE QUESTS
    // ==========================================================
    // Side quests are simple. They are locked to a specific calendar date.
    // We use OPTIONAL MATCH so the query doesn't fail if there are no side quests.
    OPTIONAL MATCH (u)-[:HAS_TASK]->(t:Task)
    WHERE t.scheduled_date = $target_date
    
    // Collect them into a list. If null, we create an empty list.
    WITH u, collect(CASE WHEN t IS NULL THEN null ELSE {
        id: t.id,
        title: t.title,
        type: "SIDE_QUEST",       // Frontend uses this to pick the color (e.g., Yellow)
        goal_title: "Side Quest",
        is_completed: coalesce(t.is_completed, false)
    } END) as side_quests
    
    // ==========================================================
    // 2. FETCH HISTORY (Completed Main Quests)
    // ==========================================================
    // If the user is looking at a past date, show what they actually finished that day.
    // We look for Days connected to Goals that have a 'completed_date' matching the target.
    OPTIONAL MATCH (u)-[:HAS_GOAL]->(g_hist:Goal)-[:HAS_DAY]->(d_hist:Day)
    WHERE d_hist.completed_date = $target_date
    
    WITH u, side_quests, collect(CASE WHEN d_hist IS NULL THEN null ELSE {
        id: d_hist.id,             // CRITICAL FIX: Use UUID, not day_number, to prevent duplicate key crashes
        day_number: d_hist.day_number,
        title: d_hist.topic,
        type: "GOAL",              // Frontend uses this to pick the color (e.g., Blue/Red)
        goal_title: g_hist.title,
        goal_id: g_hist.id,
        is_completed: true
    } END) as history_tasks
    
    // ==========================================================
    // 3. CALCULATE ACTIVE TASKS (The Pacing Logic)
    // ==========================================================
    // This is the hard part. We need to figure out "What step is next?" 
    // based on where they are right now.
    
    // Step A: Find the user's current position (The unfinished day)
    OPTIONAL MATCH (u)-[:HAS_GOAL]->(g:Goal)-[:HAS_DAY]->(current:Day)
    WHERE coalesce(current.is_completed, false) = false 
      AND coalesce(current.is_locked, false) = false

    // Step B: Check the PREVIOUS day.
    // Why? If they finished Day 3 TODAY, then Day 4 should usually appear TOMORROW, not today.
    OPTIONAL MATCH (g)-[:HAS_DAY]->(prev:Day)
    WHERE prev.day_number = (current.day_number - 1)
    
    // Step C: Calculate the Time Gap
    // We compare the User's "Today" vs the "Target Date" they are viewing.
    // Note: We use date($today_date) to respect the User's Timezone, not Server Time.
    WITH u, side_quests, history_tasks, g, current, prev,
         duration.between(date($today_date), date($target_date)).days as days_gap
    
    // Step D: Calculate the "Pacing Shift"
    // If the previous task was finished TODAY, we shift the schedule by +1 day.
    // This creates the "cooldown" effect so they don't burn out.
    WITH u, side_quests, history_tasks, g, current, days_gap,
         CASE WHEN prev.completed_date = $today_date THEN 1 ELSE 0 END as shift
         
    // Step E: Determine the Target Day Number
    // If viewing Today (gap=0) and Shift=1, effective_gap becomes -1 (No task shown).
    // If viewing Tomorrow (gap=1) and Shift=1, effective_gap becomes 0 (Show current task).
    WITH u, side_quests, history_tasks, (days_gap - shift) as effective_gap, g, current
    
    // Step F: Fetch the actual Day Node for that calculated number
    OPTIONAL MATCH (g)-[:HAS_DAY]->(target_day:Day)
    WHERE target_day.day_number = (current.day_number + effective_gap)
      AND target_day.is_completed = false // Only show if it's not already done
      AND effective_gap >= 0              // Don't show tasks in the past (negative gap)
    
    WITH side_quests, history_tasks, collect(CASE 
        WHEN target_day IS NOT NULL THEN {
            id: target_day.id,             // STABILITY FIX: UUID prevents React List crashes
            day_number: target_day.day_number,
            title: target_day.topic,
            type: "GOAL",
            goal_title: g.title,
            goal_id: g.id,
            is_completed: false
        } ELSE null 
    END) as active_tasks
    
    // ==========================================================
    // 4. MERGE & RETURN
    // ==========================================================
    // Combine all three lists into one big array for the frontend.
    RETURN side_quests + history_tasks + active_tasks as all_tasks
    """
    
    with graph_db.get_session() as session:
        # We pass 'today_date' explicitly to ensure the pacing math works for the USER'S time, not the server's.
        result = session.run(query, user_id=user_id, target_date=target_date, today_date=today_date)
        record = result.single()
        
        if not record: return []
        
        # Python-side cleanup to remove any potential None values from the list
        return [x for x in record["all_tasks"] if x is not None]
    
            
def create_side_quest(user_id: str, title: str, scheduled_date: str):
    """
    Creates a Side Quest linked to a specific date.
    """
    query = """
    MATCH (u:User {id: $user_id})
    CREATE (t:Task {
        id: randomUUID(),
        title: $title,
        is_completed: false,
        scheduled_date: $date, 
        created_at: datetime()
    })
    MERGE (u)-[:HAS_TASK]->(t)
    RETURN t.id
    """
    with graph_db.get_session() as session:
        session.run(query, user_id=user_id, title=title, date=scheduled_date)
        return True
    
def delete_goal_from_db(user_id: str, goal_id: str):
    """
    Deletes a goal and ALL its associated Day nodes (Cascade Delete).
    """
    query = """
    MATCH (u:User {id: $user_id})-[:HAS_GOAL]->(g:Goal {id: $goal_id})
    OPTIONAL MATCH (g)-[:HAS_DAY]->(d:Day)
    DETACH DELETE g, d
    """
    with graph_db.get_session() as session:
        session.run(query, user_id=user_id, goal_id=goal_id)
        return True

def delete_task_from_db(user_id: str, task_id: str):
    """
    Deletes a single Side Quest task.
    """
    query = """
    MATCH (u:User {id: $user_id})-[:HAS_TASK]->(t:Task {id: $task_id})
    DETACH DELETE t
    """
    with graph_db.get_session() as session:
        session.run(query, user_id=user_id, task_id=task_id)
        return True
    


def get_user_profile_stats(user_id: str):
    """
    Aggregates user data.
    FIXED: Converts all Neo4j Date objects to Strings before sorting to prevent TypeError.
    """
    query = """
    MATCH (u:User {id: $user_id})
    
    // 1. Goal Days
    OPTIONAL MATCH (u)-[:HAS_GOAL]->(g:Goal)-[:HAS_DAY]->(d:Day)
    WHERE d.is_completed = true
    // Fallback logic: If completed_date is missing, use scheduled date or today
    WITH u, collect({
        date: COALESCE(d.completed_date, d.date, date()), 
        category: g.category
    }) as goal_tasks
    
    // 2. Side Quests
    OPTIONAL MATCH (u)-[:HAS_TASK]->(t:Task)
    WHERE t.is_completed = true
    WITH u, goal_tasks, collect({
        date: COALESCE(t.completed_date, t.scheduled_date, date()), 
        category: "Side Quest"
    }) as side_quests
    
    WITH goal_tasks + side_quests as all_completed
    RETURN all_completed
    """
    
    with graph_db.get_session() as session:
        result = session.run(query, user_id=user_id)
        record = result.single()
        
        if not record:
            return default_profile_stats()
            
        all_completed = record["all_completed"]
        
        # --- PYTHON PROCESSING ---
        
        # 1. Total XP (10 XP per task)
        total_tasks = len(all_completed)
        total_xp = total_tasks * 10
        
        # 2. Level Calculation (Simple: 1 Level = 500 XP)
        current_level = int(total_xp / 500) + 1
        xp_next_level = 500 * current_level
        xp_current_level_start = 500 * (current_level - 1)
        
        # 3. Heatmap & Streaks
        # 🔴 THE FIX IS HERE: str(x['date']) forces everything to be a String
        dates_list = [str(x['date']) for x in all_completed if x['date']]
        dates_set = sorted(list(set(dates_list)))
        
        current_streak = 0
        max_streak = 0
        
        if dates_set:
            # Calculate Streaks
            date_objs = [datetime.strptime(d, "%Y-%m-%d").date() for d in dates_set]
            
            # Max Streak Logic
            temp_streak = 1
            max_streak = 1
            for i in range(1, len(date_objs)):
                delta = (date_objs[i] - date_objs[i-1]).days
                if delta == 1:
                    temp_streak += 1
                elif delta > 1:
                    temp_streak = 1
                max_streak = max(max_streak, temp_streak)
            
            # Current Streak Logic
            today = datetime.now().date()
            if date_objs[-1] == today:
                current_streak = temp_streak
            elif date_objs[-1] == (today - timedelta(days=1)):
                 current_streak = temp_streak
            else:
                current_streak = 0
        
        # Heatmap Data (Dictionary: Date -> Count)
        heatmap_data = {}
        for d_str in dates_list: # We can reuse our string list here
            heatmap_data[d_str] = heatmap_data.get(d_str, 0) + 1
            
        # 4. Skill Matrix (Categories)
        category_counts = {}
        for item in all_completed:
            cat = item['category'] or "General"
            category_counts[cat] = category_counts.get(cat, 0) + 1
            
        # Determine Title
        if not category_counts:
            main_tag = "Novice"
        else:
            top_category = max(category_counts, key=category_counts.get)
            main_tag = get_title_for_category(top_category, current_level)

        # 5. Badges
        badges = []
        if max_streak >= 5: badges.append("Streak Starter")
        if max_streak >= 50: badges.append("Streak Master")
        if max_streak >= 100: badges.append("Century Club")

        return {
            "stats": {
                "level": current_level,
                "current_xp": total_xp,
                "next_level_xp": xp_next_level,
                "prev_level_xp": xp_current_level_start,
                "current_streak": current_streak,
                "max_streak": max_streak,
                "active_days": len(dates_set),
                "main_tag": main_tag
            },
            "heatmap": heatmap_data,
            "skill_matrix": category_counts,
            "badges": badges
        }

def get_title_for_category(category, level):
    """Returns a cool title based on what the user studies most."""
    titles = {
        "Coding": ["Script Kiddie", "Programmer", "Code Ninja", "System Architect"],
        "Health": ["Walker", "Runner", "Athlete", "Titan"],
        "Finance": ["Saver", "Investor", "Banker", "Tycoon"],
        "General": ["Starter", "Learner", "Achiever", "Master"]
    }
    
    # Clamp level 1-4
    idx = min(level, 4) - 1
    if idx < 0: idx = 0
    
    options = titles.get(category, titles["General"])
    return options[idx]

def default_profile_stats():
    return {
        "stats": {
            "level": 1, "current_xp": 0, "next_level_xp": 500, "prev_level_xp": 0,
            "current_streak": 0, "max_streak": 0, "active_days": 0, "main_tag": "Novice"
        },
        "heatmap": {}, "skill_matrix": {}, "badges": []
    }


# --- LEARNING RESOURCES ---

def save_learning_resources(goal_id: str, resources: list):
    """
    Saves a list of learning resource nodes linked to a Goal.
    Each resource has: title, type, platform, description, url.
    """
    query = """
    MATCH (g:Goal {id: $goal_id})
    UNWIND $resources AS res
    CREATE (r:Resource {
        id: randomUUID(),
        title: res.title,
        type: res.type,
        platform: res.platform,
        description: res.description,
        url: res.url,
        created_at: datetime()
    })
    MERGE (g)-[:HAS_RESOURCE]->(r)
    """
    with graph_db.get_session() as session:
        session.run(query, goal_id=goal_id, resources=resources)


def get_learning_resources(goal_id: str) -> list:
    """
    Returns all learning resources linked to a Goal.
    """
    query = """
    MATCH (g:Goal {id: $goal_id})-[:HAS_RESOURCE]->(r:Resource)
    RETURN r.id as id, r.title as title, r.type as type,
           r.platform as platform, r.description as description, r.url as url
    ORDER BY r.created_at
    """
    with graph_db.get_session() as session:
        result = session.run(query, goal_id=goal_id)
        return [dict(record) for record in result]


def get_goal_title_and_category(goal_id: str) -> Optional[dict]:
    """
    Returns the title and category of a goal.
    """
    query = """
    MATCH (g:Goal {id: $goal_id})
    RETURN g.title as title, g.category as category
    """
    with graph_db.get_session() as session:
        result = session.run(query, goal_id=goal_id)
        record = result.single()
        if record:
            return {"title": record["title"], "category": record["category"]}
        return None



# ============================================================
# UNUSED — add_day_to_goal
# Superseded by create_initial_days (seeds Day 1+2) and the
# N+2 rolling generation via update_day_content.
# Kept here for reference; safe to delete.
# ============================================================
# def add_day_to_goal(goal_id: str, day_number: int, topic: str):
#     """
#     Adds a Day node to a Goal.
#     If it's Day 1, it unlocks immediately.
#     If it's Day 2+, it links to the previous day and locks.
#     """
#     query = """
#     MATCH (g:Goal {id: $goal_id})
#     CREATE (d:Day {
#         id: randomUUID(),
#         day_number: $day_number,
#         topic: $topic,
#         is_locked: $is_locked
#     })
#     MERGE (g)-[:HAS_DAY]->(d)
#
#     WITH d, g
#     MATCH (g)-[:HAS_DAY]->(prev_day:Day)
#     WHERE prev_day.day_number = d.day_number - 1
#     MERGE (prev_day)-[:UNLOCKS]->(d)
#
#     RETURN d.id as id
#     """
#     is_locked = (day_number > 1)
#     with graph_db.get_session() as session:
#         session.run(query, goal_id=goal_id, day_number=day_number, topic=topic, is_locked=is_locked)
#         return {"status": "Day Added", "day": day_number}


# ============================================================
# UNUSED — create_smart_timeline
# DEPRECATED: Replaced by create_initial_days (seeds only Day 1+2).
# The N+2 rolling generation in confirm_task_completion handles
# all subsequent days. This function pre-built the entire course
# upfront which caused the dual-creation bug.
# Kept here for reference; safe to delete.
# ============================================================
# def create_smart_timeline(goal_id: str, schedule: list, start_date: date):
#     """
#     DEPRECATED: Use create_initial_days instead.
#     Kept for backwards compatibility only.
#     Creates Day nodes based on the AI-generated schedule.
#     Populates 'topic' and 'sub_tasks' IMMEDIATELY.
#     """
#
#     # Prepare data for Neo4j UNWIND
#     neo4j_data = []
#
#     for day in schedule:
#         # Day 1 gets a date. Others are floating.
#         scheduled = start_date.isoformat() if day["day_number"] == 1 else None
#
#         # Combine multiple topics into one title (e.g., "State & Props")
#         combined_title = " & ".join(day["topics"])
#
#         neo4j_data.append({
#             "day_number": day["day_number"],
#             "topic": combined_title,
#             "sub_tasks": day["sub_tasks"],
#             "scheduled_date": scheduled,
#             "is_locked": day["day_number"] > 1
#         })
#
#     query = """
#     MATCH (g:Goal {id: $goal_id})
#     UNWIND $days_data as day_item
#
#     CREATE (d:Day {
#         id: randomUUID(),
#         day_number: day_item.day_number,
#         topic: day_item.topic,
#         sub_tasks: day_item.sub_tasks,
#         scheduled_date: day_item.scheduled_date,
#         is_locked: day_item.is_locked,
#         is_completed: false
#     })
#     MERGE (g)-[:HAS_DAY]->(d)
#
#     // Link the chain (Day 1 -> Day 2 -> Day 3)
#     WITH d
#     ORDER BY d.day_number
#     WITH collect(d) as days
#     FOREACH (i in range(0, size(days)-2) |
#         FOREACH (d1 in [days[i]] |
#             FOREACH (d2 in [days[i+1]] |
#                 MERGE (d1)-[:UNLOCKS]->(d2)
#             ))
#     )
#     RETURN size(days) as days_created
#     """
#
#     with graph_db.get_session() as session:
#         result = session.run(query, goal_id=goal_id, days_data=neo4j_data)
#         record = result.single()
#         return record["days_created"] if record else 0
