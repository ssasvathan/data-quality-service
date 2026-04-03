FROM python:3.12-slim

WORKDIR /app

# Install uv
RUN pip install uv --no-cache-dir

# Copy project files
COPY pyproject.toml uv.lock* ./
COPY src/ src/
COPY config/ config/

# Install dependencies
RUN uv sync --no-dev

EXPOSE 8000

CMD ["uv", "run", "uvicorn", "--app-dir", "src", "serve.main:app", "--host", "0.0.0.0", "--port", "8000"]
