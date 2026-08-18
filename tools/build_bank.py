#!/usr/bin/env python3
"""Build app/src/main/assets/bank.json from the two upstream sources.

  build-data/questions.json  authoritative text, options, answer keys, raw category
                             (data/questions.json on main in the web repo)
  build-data/bank.json       the design project's derived bank; the ONLY source of the
                             7-topic taxonomy the Android design is built around

Six of the repo's nine raw categories map 1:1 onto a topic. Three compound ones
(Government & Economy, History & Geography, Regions & Geography - 147 questions) were
split per question by the design and cannot be recomputed from the category, so the
per-id join is the only correct source. Ids missing from the map fall back to their
category's dominant topic and are REPORTED, never silently guessed.

Re-run this whenever either upstream file changes. It is deterministic.
"""
import collections
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC_QUESTIONS = ROOT / "build-data" / "questions.json"
SRC_TOPICS = ROOT / "build-data" / "bank.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "bank.json"

CLEAN = {
    "Rights & Responsibilities": "rights",
    "Indigenous Peoples & Communities": "indigenous",
    "History": "history",
    "Symbols & Modern Canada": "symbols",
    "Government": "government",
    "Government & Justice": "government",
}
DOMINANT = {
    "Government & Economy": "government",
    "History & Geography": "history",
    "Regions & Geography": "history",
}
KNOWN_TOPICS = {"rights", "indigenous", "history", "symbols", "government", "economy", "geography"}


def main() -> int:
    for p in (SRC_QUESTIONS, SRC_TOPICS):
        if not p.exists():
            sys.exit(f"missing {p} - see the plan's Task 2 Steps 1-2")

    questions = json.loads(SRC_QUESTIONS.read_text(encoding="utf-8"))
    topic_by_id = {q["id"]: q["topic"] for q in json.loads(SRC_TOPICS.read_text(encoding="utf-8"))}

    unmapped, out = [], []
    for q in questions:
        topic = topic_by_id.get(q["id"])
        if topic is None:
            cat = q.get("category", "")
            topic = CLEAN.get(cat) or DOMINANT.get(cat)
            if topic is None:
                sys.exit(f"id {q['id']}: unknown category {cat!r} - add it to CLEAN or DOMINANT")
            unmapped.append((q["id"], cat, topic))
        if topic not in KNOWN_TOPICS:
            sys.exit(f"id {q['id']}: topic {topic!r} is not one of the seven")
        out.append({
            "id": q["id"],
            "topic": topic,
            "category": q.get("category", ""),
            "difficulty": q.get("difficulty", ""),
            "question": q["question"],
            "options": q["options"],
            "answer": q["answer"],
        })

    # The Charter was entrenched in 1982; question 11 in the same bank says so.
    # Patch only if upstream still has it wrong, so a fixed upstream is a no-op.
    q4 = next((q for q in out if q["id"] == 4), None)
    if q4 is None:
        print("NOTE: no question with id 4 in this bank")
    elif q4["options"] == ["1867", "1921", "1982", "2015"]:
        if q4["answer"] != 2:
            q4["answer"] = 2
            print("PATCHED q4 -> 1982 (upstream still had the wrong answer key)")
        else:
            print("q4 already correct upstream - no patch needed")
    else:
        print(f"REVIEW BY HAND: q4 options changed upstream: {q4['options']}")

    for q in out:
        if not 0 <= q["answer"] < len(q["options"]):
            sys.exit(f"id {q['id']}: answer {q['answer']} out of range for {len(q['options'])} options")

    ids = [q["id"] for q in out]
    if len(ids) != len(set(ids)):
        dupes = [i for i, n in collections.Counter(ids).items() if n > 1]
        sys.exit(f"duplicate ids: {dupes}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    print(f"shipped questions: {len(out)}")
    print("topics:", dict(collections.Counter(q["topic"] for q in out).most_common()))
    if unmapped:
        print()
        print(f"{len(unmapped)} ids were NOT in the design's topic map and used a fallback.")
        print("These need a real topic assigned by a human:")
        for i, cat, t in unmapped:
            print(f"  id {i}: category {cat!r} -> guessed {t!r}")
        return 2
    print("every topic came from the design's map - no guesses")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
