from fastapi import FastAPI

app = FastAPI(title="Data Quality Service")


@app.get("/health")
def health_check() -> dict:
    return {"status": "ok"}
