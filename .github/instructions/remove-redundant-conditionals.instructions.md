---
applyTo: "**"
---

## Remove Redundant Conditionals

**Severity: MUST FIX**

Avoid conditional checks and null guards that are already implied by earlier logic, framework defaults, or database behavior. Remove duplicate or always-true/false branches and simplify boolean expressions or if-else chains so each condition changes control flow.
