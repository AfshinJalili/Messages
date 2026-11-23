#!/usr/bin/env python3
"""Generated logo assets must match tools/export_icon.py from the foreground vector."""
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
result = subprocess.run(
    [sys.executable, str(root / 'tools/export_icon.py'), '--check'],
    cwd=root,
)
sys.exit(result.returncode)
