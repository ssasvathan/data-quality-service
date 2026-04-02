from fastapi import FastAPI
from sqlalchemy import text
from .db import get_engine

app = FastAPI(title="DQS Serving API")
engine = get_engine()

@app.get("/api/datasets")
def get_datasets():
    with engine.connect() as conn:
        result = conn.execute(text("SELECT dataset_id, src_sys_nm FROM dataset")).mappings().all()
        return [dict(row) for row in result]
