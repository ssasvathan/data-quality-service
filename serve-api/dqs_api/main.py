from fastapi import FastAPI
from sqlalchemy import text
from .db import get_engine
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
import os

app = FastAPI(title="DQS Serving API")
engine = get_engine()

# Mount static files
static_dir = os.path.join(os.path.dirname(__file__), "static")
os.makedirs(static_dir, exist_ok=True)
app.mount("/static", StaticFiles(directory=static_dir), name="static")

@app.get("/")
def serve_index():
    return FileResponse(os.path.join(static_dir, "index.html"))

@app.get("/api/datasets")
def get_datasets():
    with engine.connect() as conn:
        result = conn.execute(text("SELECT dataset_id, src_sys_nm FROM dataset")).mappings().all()
        return [dict(row) for row in result]
