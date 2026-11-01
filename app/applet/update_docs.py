import os

phase115_blueprint = """
- **Phase 115 (CI Syntax & Bracket Scope Alignment in MainScreen)**:
  1. Syntax & Scope Restoration in MainScreen:
     - Restored the missing closing brace '}' on the else branch of 'val durationStr' at line 777 in 'MainScreen.kt'.
     - Re-established proper lexical scoping for durationStr, the thumbnail duration Box, and the item card Column within its RowScope.
     - Eliminated all 4 fatal CI compilation failures:
       * Unresolved reference 'FolderCard' (line 642)
       * Unresolved reference 'durationStr' (line 786)
       * 'Modifier.weight(...) cannot be called in this context with an implicit receiver' (line 790)
       * Syntax error: Expecting '}' (line 1432)
     - Verified perfect bracket balance (braces: 0, parens: 0, brackets: 0).
"""

with open("BLUEPRINT.md", "r", encoding="utf-8") as f:
    bp_content = f.read().rstrip()

with open("BLUEPRINT.md", "w", encoding="utf-8") as f:
    f.write(bp_content + "\n" + phase115_blueprint + "\n")

print("Updated BLUEPRINT.md successfully")

phase115_receipt = """
## Phase 115: CI Syntax & Bracket Scope Alignment in MainScreen
* Timestamp: 2026-11-01T11:20:00Z
* One-line summary: Restored missing closing brace in MainScreen durationStr else block, fixing 4 cascading CI compilation errors.
* Files touched:
  - app/src/main/java/com/example/ui/screens/MainScreen.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_029.md
* What was actually done:
  - Restored the missing closing brace '}' on the else branch of 'val durationStr' at line 777 in MainScreen.kt.
  - Re-established correct lexical scoping for durationStr, the thumbnail duration Box, and the item card Column in RowScope.
  - Eliminated the 4 fatal CI compilation failures: Unresolved reference 'FolderCard', Unresolved reference 'durationStr', 'Modifier.weight(...) cannot be called in this context', and Syntax error: Expecting '}'.
  - Verified 100% bracket balance across all braces, parens, and square brackets.
* How it was verified: AST bracket balance check (braces=0, parens=0, brackets=0), lint_applet clean run, and compile_applet.
* Any deviation from what was requested: None.
* Any known issue or follow-up needed: None.
"""

with open("receipts/RECEIPTS_029.md", "r", encoding="utf-8") as f:
    r_content = f.read().rstrip()

new_r = r_content + "\n" + phase115_receipt + "\n"
line_count = len(new_r.splitlines())
print(f"Total lines in RECEIPTS_029.md: {line_count}")

with open("receipts/RECEIPTS_029.md", "w", encoding="utf-8") as f:
    f.write(new_r)

print("Updated receipts/RECEIPTS_029.md successfully")
