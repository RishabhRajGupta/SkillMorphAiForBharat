from neo4j import GraphDatabase
from app.core.config import settings

class Neo4jService:
    def __init__(self):
        # We hold the driver here
        self.driver = None

    def connect(self):
        """Establishes the connection pool."""
        try:
            self.driver = GraphDatabase.driver(
                settings.NEO4J_URI,
                auth=(settings.NEO4J_USER, settings.NEO4J_PASSWORD)
            )
            # Verify connectivity
            self.driver.verify_connectivity()
            print("✅ Neo4j: Connected successfully!")
        except Exception as e:
            print(f"❌ Neo4j Connection Failed: {e}")
            raise e

    def close(self):
        """Closes the connection pool."""
        if self.driver:
            self.driver.close()
            print("🛑 Neo4j: Connection closed.")

    def get_session(self):
        """Returns a session to run queries."""
        if not self.driver:
            self.connect()
        return self.driver.session()

# Create a single instance to be used everywhere
graph_db = Neo4jService()