"""Root conftest.py for dqs-serve — adds src/ to sys.path for pytest discovery."""
import sys
from pathlib import Path

# Allow `import serve.*` without installing the package in editable mode
sys.path.insert(0, str(Path(__file__).parent / "src"))
