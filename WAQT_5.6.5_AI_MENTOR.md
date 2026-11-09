# WAQT 5.6.5 — AI Mentor foundation

## What changed
- Repositioned the assistant from a task-only daily planner to **WAQT Mentor**, a general coach for goals, learning, problem-solving, and planning.
- Reworked the Gemini system instructions to encourage context gathering, practical goal decomposition, milestone-based plans, honest feasibility feedback, teaching with examples/exercises, and one clear next step.
- Added explicit action-consent rules: discussing a goal or requesting a plan must not automatically create tasks. The assistant should only return app actions after a clear request or approval.
- Updated the live voice session instructions to use the same Mentor identity and coaching approach, while clearly stating that live voice cannot execute app actions.
- Changed the first chat greeting to ask what the user wants to work on instead of automatically pushing a daily-priority routine.
- Increased Gemini output capacity to 1,600 tokens to support useful multi-stage plans.
- Version bumped to 5.6.5 (versionCode 31).

## Current scope / honest limitations
- This is the first Mentor foundation: coaching behavior and prompt logic are upgraded.
- Persistent cross-chat user profile/memory, structured goal objects, interactive plan cards, explicit in-app plan approval UI, and scheduled progress reviews are not implemented yet.
- Gemini still returns the existing JSON envelope (`reply`, `actions`); the visible plan is currently presented as a chat response.
- Actions still use the existing WAQT action handler. A code-level intent check now blocks action execution unless the latest user message clearly requests execution, or is an approval immediately following an assistant offer. A dedicated review/approval screen is still a future safety improvement.
- No Android build or on-device test was run in this environment.
