#!/usr/bin/env python3
"""Fail if macrobenchmark medians exceed Baseline × slack.

Reads androidx.benchmark JSON under a results directory (CI artifact) and compares:
  - timeToInitialDisplayMs median           vs  TTID_MEDIAN_MS × SLACK
  - gfxFrameTime90thPercentileMs median     vs  SCROLL_FRAME_P90_MS × SLACK

Baseline constants live in macrobenchmark/.../Baseline.kt; this script parses them
so the workflow and the Kotlin source stay one source of truth.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASELINE_KT = ROOT / "macrobenchmark/src/main/kotlin/com/afsh/messages/benchmark/Baseline.kt"


def parse_baseline() -> dict[str, float]:
    text = BASELINE_KT.read_text(encoding="utf-8")

    def const(name: str) -> float:
        m = re.search(rf"const val {name}\s*=\s*([0-9.]+)", text)
        if not m:
            raise SystemExit(f"missing {name} in {BASELINE_KT}")
        return float(m.group(1))

    return {
        "SLACK": const("SLACK"),
        "TTID_MEDIAN_MS": const("TTID_MEDIAN_MS"),
        "SCROLL_FRAME_P90_MS": const("SCROLL_FRAME_P90_MS"),
    }


def load_metrics(results_dir: Path) -> dict[str, dict]:
    """Return {testName: {metricName: {median, ...}}} from benchmarkData.json files."""
    out: dict[str, dict] = {}
    for path in results_dir.rglob("*benchmarkData.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        for bench in data.get("benchmarks", []):
            name = bench.get("name") or bench.get("fullName") or path.stem
            metrics = {}
            for metric_name, metric in (bench.get("metrics") or {}).items():
                metrics[metric_name] = metric
            # gfxinfo metrics may land under sampledMetrics depending on schema.
            for metric_name, metric in (bench.get("sampledMetrics") or {}).items():
                metrics.setdefault(metric_name, metric)
            out[name] = metrics
    return out


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <benchmark-results-dir>", file=sys.stderr)
        return 2
    results = Path(argv[1])
    if not results.exists():
        print(f"no results at {results}", file=sys.stderr)
        return 1
    base = parse_baseline()
    if base["TTID_MEDIAN_MS"] <= 0 or base["SCROLL_FRAME_P90_MS"] <= 0:
        print(
            "Baseline.kt still has a placeholder zero — record a real baseline before gating.",
            file=sys.stderr,
        )
        return 1
    ttid_budget = base["TTID_MEDIAN_MS"] * base["SLACK"]
    scroll_budget = base["SCROLL_FRAME_P90_MS"] * base["SLACK"]
    metrics = load_metrics(results)
    if not metrics:
        print(f"no benchmarkData.json under {results}", file=sys.stderr)
        return 1

    failed = []
    for name, m in metrics.items():
        if "timeToInitialDisplayMs" in m:
            med = m["timeToInitialDisplayMs"].get("median")
            print(f"{name} timeToInitialDisplayMs median={med} budget={ttid_budget:.1f}")
            if med is not None and med > ttid_budget:
                failed.append(f"{name} TTID median {med} > {ttid_budget:.1f}")
        scroll_key = next(
            (
                k
                for k in (
                    "gfxFrameTime90thPercentileMs",
                    "frameOverrunMs",
                    "frameDurationCpuMs",
                )
                if k in m
            ),
            None,
        )
        if scroll_key:
            med = m[scroll_key].get("median")
            p90 = m[scroll_key].get("p90") or (m[scroll_key].get("percentiles") or {}).get("90")
            value = med if scroll_key.startswith("gfx") else p90
            print(f"{name} {scroll_key} gate_value={value} budget={scroll_budget:.1f}")
            if value is not None and value > scroll_budget:
                failed.append(f"{name} scroll {scroll_key} {value} > {scroll_budget:.1f}")

    if failed:
        print("BUDGET FAIL:\n  " + "\n  ".join(failed), file=sys.stderr)
        return 1
    print("BUDGET OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
