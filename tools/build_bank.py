"""Build the shipped question bank from the upstream repo's questions.json.

Run from the repo root:

    python tools/build_bank.py

Input  : build-data/questions.json   (git-ignored; copied from data/questions.json
                                      on main in mvnshrikanth/candian_citizenship_exam)
Output : app/src/main/assets/bank.json
         app/src/main/assets/explanations.json
         app/src/test/resources/{bank,explanations}.json   (JVM test copies)

History worth keeping: an earlier version of this script joined an `id -> topic` map in
from the design project's own bank.json, because the upstream categories at the time were
nine compound buckets that could not be recomputed. That join is gone, and must not come
back: the upstream ids were renumbered and now point at different questions, so joining on
id would file 500 questions under topics belonging to the questions they replaced -
silently, and plausibly enough to survive review.

Upstream now ships seven categories of its own, which are adopted as the app's topics.
"""

import collections
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "build-data" / "questions.json"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
TEST_RESOURCES = ROOT / "app" / "src" / "test" / "resources"

# Upstream category -> the app's topic key. Every category must appear here: an unknown
# one aborts rather than guessing, because a mis-filed question is invisible in the UI.
TOPIC_BY_CATEGORY = {
    "Rights and Responsibilities": "rights",
    "Law and Justice": "law",
    "Canada's History": "history",
    "Identity and Symbols": "symbols",
    "Government and Elections": "government",
    "Economy": "economy",
    "Geography and Regions": "geography",
}

OPTION_COUNT = 4


def fail(message):
    sys.exit(f"build_bank: {message}")


def main():
    if not SOURCE.exists():
        fail(f"{SOURCE.relative_to(ROOT)} is missing - copy data/questions.json there first")

    questions = json.loads(SOURCE.read_text(encoding="utf-8"))

    unknown = sorted({q.get("category", "") for q in questions} - TOPIC_BY_CATEGORY.keys())
    if unknown:
        fail(f"unknown categories {unknown} - add them to TOPIC_BY_CATEGORY")

    bank = []
    explanations = {}
    seen_ids = set()

    for q in questions:
        qid = q["id"]
        if qid in seen_ids:
            fail(f"duplicate id {qid}")
        seen_ids.add(qid)

        options = q["options"]
        if len(options) != OPTION_COUNT:
            fail(f"id {qid} has {len(options)} options, expected {OPTION_COUNT}")
        if not 0 <= q["answer"] < len(options):
            fail(f"id {qid} has answer {q['answer']} outside its options")
        if not q["question"].strip():
            fail(f"id {qid} has no question text")

        bank.append(
            {
                "id": qid,
                "topic": TOPIC_BY_CATEGORY[q["category"]],
                "category": q.get("category", ""),
                "difficulty": q.get("difficulty", ""),
                "question": q["question"],
                "options": options,
                "answer": q["answer"],
            }
        )

        # Upstream now authors why/tip for every question, so the quiz's "not written yet"
        # panel becomes a fallback rather than the common case. Blank entries are dropped
        # so a missing one stays distinguishable from an empty one.
        why = str(q.get("why", "")).strip()
        tip = str(q.get("tip", "")).strip()
        if why or tip:
            explanations[str(qid)] = {"why": why, "tip": tip}

    ASSETS.mkdir(parents=True, exist_ok=True)
    TEST_RESOURCES.mkdir(parents=True, exist_ok=True)

    compact = {"ensure_ascii": False, "separators": (",", ":")}
    bank_json = json.dumps(bank, **compact)
    explanations_json = json.dumps(explanations, **compact)

    for directory in (ASSETS, TEST_RESOURCES):
        (directory / "bank.json").write_text(bank_json, encoding="utf-8")
        (directory / "explanations.json").write_text(explanations_json, encoding="utf-8")

    counts = collections.Counter(q["topic"] for q in bank)
    ids = sorted(seen_ids)
    print(f"shipped questions : {len(bank)}")
    print(f"ids               : {ids[0]}..{ids[-1]}, contiguous={ids == list(range(ids[0], ids[-1] + 1))}")
    print(f"explanations      : {len(explanations)}")
    print("topics            :")
    for topic in sorted(counts, key=lambda t: -counts[t]):
        print(f"  {topic:<11} {counts[topic]}")

    missing = set(TOPIC_BY_CATEGORY.values()) - counts.keys()
    if missing:
        fail(f"topics with no questions: {sorted(missing)}")
    print("every topic is populated and every category was mapped explicitly")


if __name__ == "__main__":
    main()
