"""Print JUnit counts and actionable failures; fixtures contain no user credentials."""
import os
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

total = failures = errors = skipped = 0
rows = []
for directory in sys.argv[1:]:
    for path in sorted(Path(directory).rglob("TEST-*.xml")):
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for suite in suites:
            count = int(suite.get("tests", 0))
            bad = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            total += count
            failures += int(suite.get("failures", 0))
            errors += int(suite.get("errors", 0))
            skipped += int(suite.get("skipped", 0))
            rows.append(f"| {suite.get('name', path.name)} | {count} | {bad} |")
            for case in suite.findall("testcase"):
                for failure in list(case.findall("failure")) + list(case.findall("error")):
                    print(f"FAILED {case.get('classname')}.{case.get('name')}")
                    print((failure.text or failure.get("message", ""))[:6000])
summary = f"Tests: {total}; failures: {failures}; errors: {errors}; skipped: {skipped}"
print(summary)
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
        out.write(summary + "\n\n| Suite | Tests | Failed |\n| --- | ---: | ---: |\n")
        out.write("\n".join(rows) + "\n")
if total == 0:
    print("No JUnit results were produced.")
