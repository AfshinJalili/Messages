#!/usr/bin/env bash
# Read-only Codex review of the current branch against a base, with gpt-6.1-sol.
# Usage: tools/review.sh [base]   (default: main). Codex reads AGENTS.md for the review rules.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
base=${1:-main}
git fetch -q github "$base"
branch=$(git rev-parse --abbrev-ref HEAD)
out=.scratch/review/${branch//\//-}.md
mkdir -p "$(dirname "$out")"
codex review --base "github/$base" -c model='"gpt-6.1-sol"' -c model_reasoning_effort='"high"' < /dev/null | tee "$out"
echo "Saved to $out"
