# v4.5.0 — Sound engine + timer fixes
- NEW SoundEngine.kt: 14 distinct synthesized sounds (tick-tock, countdown, wind+crickets, classroom, wilderness, stream, beach, rain, cafe, fire, library, wind, night frogs, brown noise). Previously all of them were the same brown noise.
- FIX: tick-tock toggle only saved a setting and never played anything. It now ticks/tocks while a focus session runs (not during breaks) and stops on pause/finish.
- FIX: break mode could never start (isBreakSession was never set). Now a finished focus session starts a break automatically (every 4th session = longer break), a finished break returns to focus, plus a manual "start a break / back to focus" button.
- NEW: completion chime when a session ends.
- FIX: volume slider now controls the synthesized sounds.
- Not compiled here (no Android SDK) - build via GitHub Actions and test on device.

# v4.6.0 — Focus dashboard
- Timer screen is now scrollable and has: sessions today, focus time, day streak, daily-goal progress bar (tap the card to change the goal), one-tap ambient sound chips, and a 7-day glass bar chart with a dashed goal line.

# v4.7.0
- Tasks: sorted by importance (done tasks sink), priority badge + priority filter, ✅ + strike-through on finished tasks, restart icon, add-task FAB outside the rail, quick reminder with chosen duration, stats (day/week/month/custom range), rank titles.
- Rail (all screens): distinct bright colors, swipe or tap to open/close, colored icons.
- Focus: slower calmer tick-tock (60 BPM), stereo 44.1 kHz ambient sounds, living hourglass (falling sand), stats + rank titles.
- Habits: stats icon + streak rank titles.
- Notes: user-defined categories only (rail icon 🏷, filter row, picker in the note editor).
- Fixes: weekday labels / streak across month boundaries (the app's day counter is year*366+dayOfYear), light/dark theme for glass cards, rail and buttons.

# v4.8.0
- NEW bottom tab bar (tap a tab) replaces the hold-and-drag radial wheel.
- NEW real Gemini assistant (GeminiClient.kt, INTERNET permission): reviews your day, picks top priorities, adds tasks/notes by itself, never discusses how to execute. Key entered via the key icon in the chat or in Settings. Model alias: gemini-flash-latest.
- NEW backup saved as a real .json file to device or Google Drive (system file picker); restore already supported.
- Google account sign-in is NOT included (requires OAuth setup in Google Cloud).

# v5.0.0
- Side rail is movable on every tab: long-press the closed rail (or drag the grip when open) and drop it on any edge (left/right/top/bottom); it rotates to horizontal on top/bottom and remembers its place.
- Add button (+) is now a floating orange circle outside the rail on Tasks, Habits and Notes.
- Habits also appear in the Tasks tab (new "Habits" filter, "Habit" badge); they live in both places and stay in sync.
- Focus: "Stop sound" chip + mute icon in the rail.
- Ambient sounds rebuilt: 48 kHz 32-bit float stereo, pink-noise based, physically-inspired rain/water/fire/wind/crowd models, with room reverb.
- AI bubble drag direction fixed for RTL (Arabic) layout.
- Removed unused REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission (reduces Play Protect flags).

# v5.1.0
- New-habit dialog now has a category picker (+ "New" category).
- Rail drag hardened (no crash path) + crash reporter: if the app ever closes unexpectedly, the next launch shows the error text to copy/screenshot.
- Restored battery-optimization permission + real permission requests (notifications, battery, exact alarms, overlay) with live status.
- First-run animated tour (8 slides) ending in the permissions step; re-openable from Settings (App tour / Reminder permissions).
