from sqlalchemy import create_engine
import os

def get_engine(db_url: str = None):
    url = db_url or os.getenv("DATABASE_URL", "postgresql+pg8000://postgres:localdev@localhost:5433/postgres")
    return create_engine(url)
