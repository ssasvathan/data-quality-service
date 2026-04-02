from fastapi.testclient import TestClient
from dqs_api.main import app

client = TestClient(app)

def test_read_datasets():
    response = client.get("/api/datasets")
    assert response.status_code == 200
    assert isinstance(response.json(), list)

def test_static_ui_served():
    response = client.get("/")
    assert response.status_code == 200
    assert "text/html" in response.headers["content-type"]
