---
name: h5w-unified
version: 1.0.0
description: >
  Unified autonomous mastermind for building, auditing, improving, and maintaining
  world-class apps. H5W (How, Who, Will, What, When, Where) is the chief guide
  — a simulation-driven orchestrator routing to six domain modules: app-audit,
  code-audit, design-aesthetic-audit, art-direction-engine, scope-context, and
  app-restructuring. Trigger on: ANY app-related request — "audit my app", "build
  this", "simulate usage", "improve my app", "redesign", "restructure", "code
  review", "design audit", "make it beautiful", "find issues", "fix everything",
  "run H5W", "simulate and fix", "what would a user encounter", "you decide what
  to fix", "improve whatever needs improving", "make it better", "full deep audit",
  "autonomous improvement", "build from scratch", "polish", "expand from this fix",
  or any request where Claude must discover what to do rather than being told.
  This skill IS the single entry point for all app development and quality work.
  Always use this skill even if another skill seems more specific — H5W routes.
---

# H5W Unified Autonomous System — Chief Guide

---

## SYSTEM IDENTITY

One system. Six domain modules. One brain. H5W is the chief guide — it holds
every shared protocol, runs the simulation engine, orchestrates builds, and
routes domain work to specialized modules.

```
┌─────────────────────────────────────────────────────────────────────┐
│                      H5W CHIEF GUIDE (this file)                    │
│                                                                     │
│  SHARED PROTOCOLS                                                   │
│  §0 Context · §LAW Iron Laws · §FMT Findings · §SRC Sources        │
│  §REV Tiers · §VER Verify · §DOC Working Docs · §TOOL · §PLAT     │
│                                                                     │
│  SIMULATION ENGINE                                                  │
│  §SIM Calibration · Personas · State Space · Walkthrough            │
│  §SIM Expansion (micro-H5W) · Checkpoints                          │
│                                                                     │
│  H5W LENSES (6 analysis dimensions)                                 │
│  §H How · §W Who · §P Will · §T What · §N When · §L Where         │
│                                                                     │
│  ORCHESTRATION                                                      │
│  §TRIAGE Routing · §BUILD Build · §WORKFLOW Handoffs · §SESSION     │
├──────────┬──────────┬──────────┬──────────┬──────────┬─────────────┤
│ MOD-APP  │ MOD-CODE │ MOD-DESG │ MOD-ART  │ MOD-SCOP │ MOD-REST   │
│ 120+ app │ 8 code   │ 21-step  │ original │ scope    │ live       │
│ audit    │ quality  │ visual   │ art dir  │ awareness│ restructure│
│ dims     │ dims     │ analysis │ engine   │ protocol │ pipeline   │
└──────────┴──────────┴──────────┴──────────┴──────────┴─────────────┘
```

**Prime directive.** The user delegated execution, not judgment on irreversible
matters. Claude does the work, verifies it, documents it, and stops for the
user on Tier-3 decisions only. In autonomous mode (§AUTO), T3 items are queued
for later — work continues on everything else until the user returns.

**Agent capability.** This system operates as an autonomous agent when invoked
with §AUTO. It self-routes, self-corrects, manages its own context window,
and produces a comprehensive report when done. The user can leave and return
to a completed or in-progress report — not a prompt waiting for input.

**Installation:** This skill REPLACES the following standalone skills:
`app-audit`, `code-audit`, `design-aesthetic-audit`, `art-direction-engine`,
`scope-context`, `app-restructuring`. Uninstall them before installing this.
Running both creates trigger phrase conflicts. Autonomy is not permission to guess about things
that cannot be undone.

**On skill load, do this first:**
1. Check user memory and past conversations for existing project context.
2. If the user provided a brief, uploads, or references — treat as primary source.
3. Read §TRIAGE to determine execution path from the user's request.
4. Fill §0 by extracting from code — ask user only for what can't be extracted.
5. Create working documents (§DOC): `H5W-LOG.md`, `H5W-QUEUE.md`, `H5W-ASSUMPTIONS.md`.
6. Use `TodoWrite` for progress tracking across phases.
7. Announce the plan in one message: which path, expected phases, what user will see.

---

## TABLE OF CONTENTS

### SHARED PROTOCOLS (all modules inherit — defined once here)

| Code | Section | Purpose |
|------|---------|---------|
| §0 | **Unified Context Block** | App identity, stack, constraints, design, domain rules |
| §I | **Adaptive Calibration** | Domain, architecture, scope, five-axis aesthetic, source rules, adaptive protocols |
| §LAW | **12 Iron Laws** | Governing rules with violation examples — never overridden |
| §SRC | **Source Integrity** | Sourcing, tagging, verifying domain facts — 7 rules, quality tiers |
| §FMT | **Finding Format** | Universal template with good/bad examples |
| §REV | **Reversibility Tiers** | T0–T3 classification with concrete scenarios |
| §VER | **Verification Protocol** | How fixes are confirmed correct |
| §DOC | **Working Documents** | H5W-LOG, QUEUE, ASSUMPTIONS — formats with examples |
| §TOOL | **Tool Integration** | Claude Code tool-to-task mapping |
| §PLAT | **Platform Awareness** | Cross-platform terminology + platform-specific checks |

---

### H5W SIMULATION ENGINE

| Code | Section | Purpose | Trigger Phrases |
|------|---------|---------|-----------------|
| §SIM.1 | **Persona Generation** | Simulated user population with walkthrough scripts | "simulate", "as a user", "personas" |
| §SIM.2 | **State Space Mapping** | Reachable states and transition matrix | "map states", "explore states" |
| §SIM.3 | **Walkthrough Protocol** | 4-stage × 6-lens walkthroughs with worked examples | "walkthrough", "simulate usage" |
| §SIM.4 | **Expansion Protocol** | Micro-H5W on every fix — expandantic loop | "expand from this fix" |
| §SIM.5 | **Checkpoint Protocol** | Triggers, format, worked report | "show checkpoint" |

---

### H5W LENS CATEGORIES (six analysis dimensions)

| Code | Lens | Sub-codes | What It Discovers |
|------|------|-----------|-------------------|
| §H | **HOW** — Mechanics & Flow | §H.1–§H.5 | Flow gaps, broken mechanics, missing feedback |
| §W | **WHO** — Personas & Context | §W.1–§W.5 | Persona mismatches, a11y gaps, device variance |
| §P | **WILL** — Prediction & Edge | §P.1–§P.5 | Edge cases, failure modes, unmet expectations |
| §T | **WHAT** — Concrete Issue | §T.1–§T.4 | Root cause, minimum fix, impact radius |
| §N | **WHEN** — Timing & Sequence | §N.1–§N.5 | Timing bugs, races, state-sequence failures |
| §L | **WHERE** — Localization & Cascade | §L.1–§L.5 | Root cause location, cascades, pattern spread |

---

### ORCHESTRATION

| Code | Section | Purpose | Trigger Phrases |
|------|---------|---------|-----------------|
| §TRIAGE | **Routing** | Determines execution path from any request | any request |
| §BUILD | **Build Protocol** | From-scratch app creation pipeline | "build", "create", "from scratch" |
| §WORKFLOW | **Inter-Module Workflows** | Concrete handoff examples between modules | — |
| §SESSION | **Session Continuity** | Resume from prior session, carry findings forward | "continue", "resume" |
| §AUTO | **Deep Autonomous Protocol** | Unattended operation: self-decide, self-correct, manage context, report at end | "autonomous", "I'll be back", "handle it", "run for 2 hours" |

---

### MODULE MAP (loaded on demand — never preloaded)

| Module | Reference File | Domain | When Loaded |
|--------|---------------|--------|-------------|
| MOD-APP | `references/mod-app-audit.md` | Security, state, domain logic, UX, a11y, perf, i18n, projections, R&D | Systemic app-level issues detected |
| MOD-CODE | `references/mod-code-audit.md` | Format, naming, dead code, optimization, architecture, logic, state, async | Code-level quality issues detected |
| MOD-DESG | `references/mod-design-audit.md` | Color science, typography, motion, hierarchy, brand, tokens, state design | Visual/aesthetic issues detected |
| MOD-ART | `references/mod-art-direction.md` | Original visual code, source research, anti-slop, craft, components | Building new UI or overhauling design |
| MOD-SCOP | `references/mod-scope-context.md` | "All X" inventory, disambiguation, large-scope awareness | Scope consistency or ambiguity detected |
| MOD-REST | `references/mod-restructuring.md` | Archaeology, architecture mapping, diagnosis, migration, live execution | Structural/architectural issues detected |

---

### EXECUTION PHASES

| Phase | What Happens | Owner |
|-------|-------------|-------|
| **0 — UNDERSTAND** | Fill §0, classify domain/complexity/scope | Chief Guide |
| **1 — DISCOVER** | H5W simulation: personas, states, walkthroughs | Chief Guide §SIM |
| **2 — ANALYZE** | Module audits triggered by findings | Modules via §WORKFLOW |
| **3 — PLAN** | Priority sort, roadmap, expansion map | Chief Guide |
| **4 — EXECUTE** | Fix, build, art-direct, restructure | Modules, verified by Chief Guide |
| **5 — VERIFY** | Micro-H5W on every change, regression check | Chief Guide §SIM.4 |
| **6 — EVOLVE** | Queue next cycle, session continuity | Chief Guide §SESSION |

---

### DELIVERABLES & SYSTEM

| Code | Section | Purpose |
|------|---------|---------|
| §MODE | **Execution Modes** | Full, Targeted, Single-Lens, Expansion, Continuous, Build |
| §ANTI | **Anti-Patterns** | 13 things Claude must never do |
| §XCUT | **Cross-Cutting Concerns** | Patterns spanning multiple modules |
| §DLVR | **Deliverables** | Required outputs per mode |
| §MANDATE | **Final Mandate** | Binding system contract |

---

### QUICK REFERENCE — "I want to..." → Run this

| I want to... | Run this |
|--------------|----------|
| Full simulation + autonomous fix | `"run H5W"` → full Phase 0–6 cycle |
| Full app audit | `"full app audit"` → MOD-APP all parts |
| Full deep EVERYTHING | `"full deep audit"` → all modules, all phases |
| Just the code | `"code review"` → MOD-CODE |
| Just the design | `"design audit"` → MOD-DESG |
| Art direction from scratch | `"design my app"` → MOD-ART |
| Restructure | `"restructure my app"` → MOD-REST |
| Build a new app | `"build [description]"` → §BUILD pipeline |
| Simulate one screen | `"run H5W on [screen]"` → targeted simulation |
| Find edge cases only | `"run WILL lens"` → §P deep dive |
| Check who's underserved | `"run WHO lens"` → §W deep dive |
| Trace a cascade | `"run WHERE lens"` → §L deep dive |
| Expand after a fix | `"expand from this"` → §SIM.4 |
| Keep improving until done | `"continuous mode"` → loop with expanded checkpoints |
| Polish existing app | `"polish my app"` → H5W simulation → targeted fixes |
| What should I build next | `"R&D mode"` → MOD-APP §X |
| Standardize all X | `"all X"` or `"every Y"` → MOD-SCOP |
| Continue from last session | `"continue"` → §SESSION resume |
| Run autonomously for hours | `"handle it"` or `"I'll be back"` → §AUTO FULL |
| Build autonomously | `"build this, I'll be back"` → §AUTO + §BUILD |
| Improve while I'm away | `"improve until I return"` → §AUTO + continuous |

---

## QUICK START

> **For Claude:** On activation:
> 1. Read §TRIAGE — determine execution path from user's request.
> 2. Fill §0 — extract from code first, ask user only for gaps.
> 3. Create working documents (§DOC).
> 4. Create progress tracker with `TodoWrite`.
> 5. Execute the determined path. Load module reference files only when needed.
> 6. After every fix from any source: run micro-H5W (§SIM.4). Log everything.
> 7. At checkpoint boundaries: stop, report (§SIM.5), wait for user.
>
> **For User:** Describe what you want in natural language. H5W routes to the
> right module and phase. You approve T3 decisions. Everything else is autonomous.

---

## SCOPE CONVENTION — "do X" vs "full deep X"

> **Binding instruction.** "Full deep" is a scope multiplier across all modules.

| User Says | Scope |
|-----------|-------|
| `"[action] [subject]"` | **Standard** — primary module only, within its boundaries |
| `"full deep [action] [subject]"` | **Expanded** — primary module + every cross-cutting section from every module that touches the subject |

### Full Deep Expansion Map

| Subject | Primary Module | Full Deep Adds |
|---------|---------------|----------------|
| **design / aesthetic** | MOD-DESG (21-step) | MOD-APP §E,§F1-F6,§G1-G4,§L3-L5,§D5 |
| **security** | MOD-APP §C | MOD-CODE §D5(logic),§D6(state),§D7(errors) + MOD-APP §B3,§K |
| **performance** | MOD-APP §D | MOD-CODE §D3(optimization) + MOD-APP §H4,§O1,§D5 |
| **code quality** | MOD-CODE (all dims) | MOD-APP §I,§L1-L2 + MOD-SCOP |
| **UX** | MOD-APP §F | MOD-DESG full + MOD-APP §E,§G + MOD-SCOP |
| **restructure** | MOD-REST | MOD-CODE §D4(architecture) + MOD-SCOP |
| **everything** | All modules | Full Phase 0–6 with every module |

---

## §TRIAGE — ROUTING

Determine execution path from the user's request. If ambiguous, ask using
`AskUserQuestion` with the mode options below.

### Signal Detection

| Signal in Request | Primary Route | Phases |
|-------------------|--------------|--------|
| "audit", "review", "check" | Module audit → detect which (see below) | 0→2→3 |
| "simulate", "H5W", "as a user", "find issues" | H5W simulation | 0→1→2→3→4→5→6 |
| "build", "create", "from scratch" | §BUILD pipeline | 0→BUILD→5→6 |
| "fix", "improve", "make better", "polish" | H5W simulation → targeted | 0→1→4→5 |
| "restructure", "reorganize", "untangle" | MOD-REST | 0→REST→5 |
| "design", "beautiful", "art direction", "look like X" | MOD-ART or MOD-DESG | 0→ART/DESG |
| "expand", "continue improving" | §SIM.4 expansion | 5 only |
| "you decide", "figure it out" (user present) | Full H5W cycle | 0→1→2→3→4→5→6 |
| "handle it", "I'll be back", "run for N hours" | §AUTO + Full H5W | §AUTO governs |
| Specific § code referenced | Jump directly | — |
| "continue", "resume", "pick up from" | §SESSION resume | Load prior state |

### Module Detection (for audit routing)

| What User Describes | Module | Reference File |
|--------------------|--------|----------------|
| App-level: security, state, domain logic, UX, a11y, perf, i18n | MOD-APP | `references/mod-app-audit.md` |
| Code-level: quality, naming, optimization, async, architecture | MOD-CODE | `references/mod-code-audit.md` |
| Visual: color, typography, motion, brand, design system | MOD-DESG | `references/mod-design-audit.md` |
| Art direction / "make it look like X" / build new UI | MOD-ART | `references/mod-art-direction.md` |
| Scope: "all X", "every Y", "standardize Z", ambiguity | MOD-SCOP | `references/mod-scope-context.md` |
| Structure: file org, modules, architecture, decoupling | MOD-REST | `references/mod-restructuring.md` |
| Unclear / multiple | Ask user via `AskUserQuestion` | — |

### Triage Decision Tree

```
USER REQUEST
│
├─ Contains "build" / "create" / "from scratch"?
│  YES → §BUILD pipeline
│
├─ Contains specific module trigger? (see Module Detection above)
│  YES → Route to that module
│
├─ Contains "simulate" / "H5W" / "find issues" / "as a user"?
│  YES → Full H5W simulation
│
├─ Contains "you decide" / "autonomous" / "figure it out" / "handle it"?
│  YES → Check for time horizon / "I'll be back" signal
│        → If autonomous signal: activate §AUTO + Full H5W
│        → If interactive: Full H5W simulation
│
├─ Contains "expand" / "continue improving"?
│  YES → §SIM.4 expansion from most recent changes
│
├─ Contains "continue" / "resume" / "pick up"?
│  YES → §SESSION resume
│
├─ Contains "fix" / "improve" / "make better" / "polish"?
│  YES → H5W targeted simulation → fixes
│
├─ Contains "full deep"?
│  YES → Identify subject → apply expansion map
│
└─ Ambiguous?
   YES → AskUserQuestion with mode options:
   - Full App Audit (MOD-APP all parts)
   - Design & Aesthetic Audit (MOD-DESG 21-step)
   - Code Quality Audit (MOD-CODE all dims)
   - H5W Simulation (discover issues autonomously)
   - Build from Scratch (§BUILD pipeline)
   - Restructure (MOD-REST)
   - R&D & Improvement (MOD-APP §X)
```


---

## §0. UNIFIED CONTEXT BLOCK

> **One block for the entire system.** Fill once — all modules inherit.
> Extract from code first. Ask user only for gaps. Never re-fill in modules.
> In companion mode, inherit from the invoking context. Modules reference this
> as "Chief Guide §0" — do not create separate context blocks.

```yaml
# ═══════════════════════════════════════════════════════════════════
# H5W UNIFIED CONTEXT
# ═══════════════════════════════════════════════════════════════════

# ─── CROSS-SESSION CONTINUITY ─────────────────────────────────────
# Fill ONLY when this is NOT the first session on this app.
# Purpose: prevent silent contradiction between sessions.
# A finding that contradicts a prior confirmed rule is a CONFLICT —
# surface it explicitly, never silently apply.
Prior Session:
  Version Audited:     # Version number from prior session's §0
  Session Date:        # Approximate date
  Confirmed Rules:     # Domain rules confirmed [§0-CONFIRMED] last session
    - # "BASE_RATE = 0.008 — confirmed by user, session 1"
  Confirmed Findings:  # Findings confirmed as real bugs
    - # "F-007: rounding error — confirmed CRITICAL"
  Conflicts:           # Contradictions between sessions
    - # "CONFLICT: [prior: X] vs [now: Y] — needs user confirmation"

# ─── IDENTITY ─────────────────────────────────────────────────────
App Name:        # e.g. "Whispering Wishes"
Version:         # From package.json, build.gradle, etc.
Domain:          # e.g. "Wuthering Waves companion app"
Audience:        # e.g. "Wuthering Waves players"
Stakes:          # LOW (hobby) | MEDIUM (productivity) | HIGH (money) | CRITICAL (medical)
                 # Stakes is a severity multiplier — wrong data in CRITICAL = CRITICAL finding.

# ─── TECH STACK ───────────────────────────────────────────────────
Framework:       # e.g. "Next.js 14 (React 18)" / "Android (Kotlin, XML Views)"
Language:        # e.g. "TypeScript" / "Kotlin 1.9"
Styling:         # e.g. "Tailwind CSS" / "Material Design 3, XML themes"
State:           # e.g. "useState + Context" / "ViewModel + StateFlow"
Persistence:     # e.g. "localStorage" / "SharedPreferences + Room"
Build:           # e.g. "Vite 5" / "Gradle 8.x"
Linting:         # e.g. "ESLint + Prettier" / "ktlint + detekt" / "None"
Testing:         # e.g. "Jest + RTL" / "JUnit5 + MockK" / "None"
External APIs:   # e.g. "None" / "Game API" / "Stripe"
AI/LLM:          # e.g. "None" / "Claude claude-sonnet-4-6, streaming"
Workers:         # e.g. "None" / "Blob Web Worker" / "WorkManager"
Visualization:   # e.g. "None" / "Recharts" / "D3.js"

# ─── MOBILE / NATIVE ─────────────────────────────────────────────
Platform:        # "Android" / "iOS" / "Web" / "Flutter" / "React Native"
Min SDK:         # e.g. "Android 29 (10)" / "iOS 15.0" / "N/A"
Target SDK:      # e.g. "Android 35 (15)" / "iOS 17.0" / "N/A"

# ─── PLATFORM & LOCALE ───────────────────────────────────────────
Primary Locale:  # e.g. "en-US" / "multiple"
RTL Support:     # yes / no / planned
Deployment:      # e.g. "Vercel" / "Play Store" / "App Store"

# ─── ENTRY POINTS (every route/screen/interaction surface) ────────
Routes:
  - # "/" → Home
  - # "/teams" → Teams tab
  - # "HomeFragment" → Main screen

# ─── STATE SHAPE (name each store + structure) ────────────────────
State Stores:
  - # "teamStore: { teams: Team[], selectedTeam: string | null }"
  - # "HomeViewModel: StateFlow<HomeUiState>"

# ─── CONSTRAINTS ─────────────────────────────────────────────────
# Non-negotiable. Every recommendation MUST respect these.
# Suggestions to change them are proposals, not findings.
Primary Device:      # e.g. "Xiaomi 13T — 439×976 CSS, DPR 2.78"
Target Viewports:    # e.g. "Mobile-first, 360–440px"
Performance Budget:  # e.g. "LCP < 2.5s" / "Cold start < 1s"
Known Limitations:   # e.g. "No backend — client-side only"
Architecture:        # e.g. "Zero-build CDN" / "Feature-based modules"

# ─── DESIGN IDENTITY ─────────────────────────────────────────────
# The app's intentional visual character. Protects against generic "fixes."
Color System:    # e.g. "OKLCH-derived, dark theme dominant"
Typography:      # e.g. "Cormorant Garamond headings, Outfit body"
Motion:          # e.g. "Minimal" / "Aurora gradient, particle systems"
Visual Source:   # e.g. "Wuthering Waves aesthetic" / "Material You"
Aesthetic Role:  # SHOWCASE | PROFESSIONAL | UTILITY | INVISIBLE
Personality:     # Emotional character it should project — e.g. "precise & atmospheric"
Protected Elements: # Visual signatures that MUST be preserved during any polish/audit
  - # e.g. "neon glow on DPS bars" / "aurora gradient background"
  - # e.g. "circle portrait frames in banner history"

# Five-Axis Quick Profile (fill from §I.4 classification):
  A1 Commercial:  # Revenue-generating / Institutional / Non-revenue
  A2 Intensity:   # Focus-critical / High-stakes / Emotional / Creative / Leisure / Transactional
  A3 Audience:    # Expert / Enthusiast / General public / Mixed
  A4 Subject ID:  # Strong established / Moderate conventions / Weak or none
  A5 Aesthetic:   # Showcase / Professional / Utility / Functional

# ─── CONVENTIONS (extract from code) ─────────────────────────────
Naming Style:    # Detected casing, prefix patterns
Import Order:    # Detected grouping pattern
Error Pattern:   # try/catch, Result, sealed class, etc.
State Pattern:   # How state is currently managed
File Org:        # Feature-based, layer-based, flat, etc.

# ─── CODEBASE METRICS (auto-extract with tools) ──────────────────
# Used by MOD-REST, MOD-CODE, and scope sizing (§I.3).
# Claude Code: use Bash(find, wc) to populate automatically.
Total Files:       # `find src -type f | wc -l`
Total LOC:         # `find src -name '*.ts' -o -name '*.tsx' | xargs wc -l`
Source Dirs:       # Top-level directory listing
Test Files:        # Count and location
Test Coverage:     # If measurable
Config Files:      # List of config files found

# ─── CURRENT STRUCTURE (auto-extract for restructuring) ──────────
# Used by MOD-REST. Claude Code: use Bash(tree -L 2) to populate.
Org Style:         # layer-based / feature-based / mixed / flat / chaotic
Top-Level Tree:    # Actual directory tree (2 levels deep)
Route Structure:   # Actual page/route/activity listing
Shared Code:       # Location and contents of shared/common/utils
State Locations:   # Where state is defined and managed
Type Definitions:  # Where types/interfaces live

# ─── DOMAIN RULES ────────────────────────────────────────────────
# Every formula, constant, rate, threshold the code must implement correctly.
# This IS the specification. Wrong values here → wrong findings.
Domain Rules:
  - # "BASE_RATE = 0.008 [CODE: line 42] — needs user confirmation"
  - # "MAX_TEAMS = 8 [§0-CONFIRMED]"

# ─── TEST VECTORS ────────────────────────────────────────────────
Test Vectors:
  - # "Input: {values} → Expected: {result} — Source: {reference}"

# ─── CRITICAL WORKFLOWS (5–10 most important journeys) ───────────
Workflows:
  - # "Add team → select characters → compare DPS → save"

# ─── KNOWN ISSUES ────────────────────────────────────────────────
Known Issues:
  - # "Banner history page has layout bugs on narrow screens"

# ─── AUDIT SCOPE ─────────────────────────────────────────────────
Files to Audit:  # "All" / specific paths
Out of Scope:    # e.g. "node_modules" / "generated code" / "third-party libs"

# ─── GROWTH CONTEXT (for projection analysis §O) ─────────────────
Growth:
  Users:         # Current count / growth rate
  Data Volume:   # Per-user data, growth trajectory
  Feature Plans: # Next features planned
  Scale Target:  # Where the app needs to be in 6–12 months
```

---

## §I. ADAPTIVE CALIBRATION

Before any work, classify the app. Classifications determine which modules get
deepest scrutiny, which personas to generate, and where severity multipliers apply.

### §I.1. Domain Classification → Severity + Module Emphasis

| Domain | Amplify Modules/Sections | Persona Emphasis | Stakes Default |
|--------|--------------------------|------------------|----------------|
| Medical / Health | MOD-APP §A1, §B3, §C6 | Hostile-env, a11y, first-time | CRITICAL |
| Financial / Fintech | MOD-APP §A1, §K1, §C1, §C6 | Power (fraud), first-time (trust) | HIGH→CRITICAL |
| Gambling / Gacha | MOD-APP §A2, §L5, §C6 | Power (odds), first-time | MEDIUM→HIGH |
| E-commerce | MOD-APP §C1, §C2, §K1, §C6 | First-time (trust), hostile (network) | HIGH |
| Social / Multi-user | MOD-APP §C5, §K4, §C6 | All equally | MEDIUM→HIGH |
| Productivity / SaaS | MOD-APP §B, §D + MOD-CODE §D3,§D4 | Power (efficiency), first-time | MEDIUM |
| Game / Companion | MOD-APP §A, MOD-DESG | Power (optimizer), enthusiast | LOW→MEDIUM |
| AI / LLM-Powered | MOD-APP §K5, §C2, §C5 | Hostile (failure), first-time (trust) | MEDIUM→HIGH |
| Data / Analytics | MOD-APP §A1, §B + MOD-CODE §D3 | Power (data density) | MEDIUM→HIGH |

### §I.2. Architecture Classification → Failure Modes

| Architecture | Primary Failure Modes | State Risk |
|-------------|----------------------|------------|
| Single-file CDN | Dead code, blob Worker compat, CSS specificity, no splitting | Low |
| Multi-file SPA (Vite/Webpack) | Bundle bloat, stale chunks, tree-shaking, import cycles | Medium |
| SSR / Next.js | Hydration mismatch, server/client divergence, SEO gaps | Medium-High |
| Vanilla JS | Global pollution, event leaks, DOM coupling | Low-Medium |
| PWA | SW versioning, cache poisoning, offline edge cases | Medium |
| LocalStorage-only | Quota exhaustion, schema migration, concurrent-tab | Medium |
| Backend-connected | Race conditions, optimistic failures, token leaks, CORS | High |
| Android MVVM/Kotlin | Fragment lifecycle, ViewModel scope, coroutine cancel, process death, ProGuard | High |
| Android Compose | Recomposition storms, state hoisting, side-effect lifecycle, LazyColumn | Medium-High |
| iOS UIKit | Retain cycles, main-thread violations, lifecycle misuse, deep-link | Medium-High |
| iOS SwiftUI | @State/@StateObject confusion, view identity, NavigationStack | Medium |
| Cross-platform (Flutter/RN) | Bridge bottleneck, platform fallback gaps, native module versioning | High |

### §I.3. Scope Sizing

| LOC | Audit Parts | Simulation Personas | Simulation Depth | Estimated Time |
|-----|-------------|--------------------|--------------------|----------------|
| < 500 | 4–5 | 3 (mandatory only) | All stages, all lenses | 15–25 min |
| 500–2K | 6–8 | 3–4 | All stages, all lenses | 25–45 min |
| 2K–6K | 8–12 | 4–5 | All stages, priority states | 45–75 min |
| 6K–15K | 12–16 | 5+ domain-specific | Per-module walkthroughs | 75–120 min |
| > 15K | 16+ | 5+ domain-specific | Multi-checkpoint | 120+ min |

### §I.4. Five-Axis Aesthetic Profile

> Classify all five axes before writing any visual/design finding (MOD-DESG, MOD-APP §E/§F/§L).
> Tag conditional findings with axis codes: `[A1]`–`[A5]`.

**AXIS 1 — Commercial Intent** `[A1]`

| Level | Aesthetic Implication |
|-------|-----------------------|
| Revenue-generating (paid, subscription, ads) | Trust signals actively matter — every choice supports or undermines willingness to pay |
| Institutional (non-profit, government) | Credibility and legitimacy — design communicates seriousness |
| Non-revenue (free, open-source) | Craft and authenticity — commercial signals irrelevant or harmful |

**AXIS 2 — Use Intensity & Emotional Context** `[A2]`

| Mode | Aesthetic Implication |
|------|-----------------------|
| Focus-critical / high-frequency | Near-invisible design, zero-distraction, every animation is a tax |
| High-stakes / low-frequency | Cognitive load reduction primary, calm, high-contrast, unambiguous |
| Emotionally sensitive | Safety and warmth are structural — harsh colors or playful copy cause harm |
| Creative / exploratory | Expressiveness valid, discovery and inspiration are functional goals |
| Learning / progressive | Progress communication, reward effort, reduce intimidation |
| Leisure / casual | Delight primary, polish and playfulness appropriate |
| Occasional / transactional | Get in, get answer, get out — complexity above "clean" is waste |

**AXIS 3 — Audience Relationship** `[A3]`

| Relationship | Aesthetic Implication |
|--------------|-----------------------|
| Domain expert | Info density is a feature, precision vocabulary required, generic = distrust |
| Enthusiast / community | Community vocabulary signals insider status, not knowing norms = outsider |
| Casual / general public | Progressive disclosure mandatory, jargon avoided or explained |
| Mixed / bridging | Hardest problem — don't condescend to experts or overwhelm novices |

**AXIS 4 — Subject Visual Identity** `[A4]`

| Strength | Aesthetic Implication |
|----------|-----------------------|
| Strong established (game IP, major brand) | Fidelity is a goal — deviation needs justification |
| Moderate (genre conventions) | Conventions inform but don't dictate |
| Weak / none | Freedom to define — but define deliberately, not by default |

**AXIS 5 — Aesthetic Investment** `[A5]`

| Level | Aesthetic Implication |
|-------|-----------------------|
| Showcase (art, portfolio) | Every pixel deliberate, craft IS the product |
| Professional (SaaS, business) | Clean, trustworthy, consistent — not flashy |
| Utility (tools, calculators) | Invisible design, maximum clarity, minimum decoration |
| Functional (internal, MVP) | Works > looks — baseline consistency still matters |

**Usage:** When a finding is conditional on an axis, tag it. Skip or reframe
findings tagged with axes that don't apply. E.g., `[A1]` commercial trust
finding → skip if the app is non-revenue.

### §I.5. Domain Rule Extraction

When domain rules aren't in §0, extract from code with strict source discipline:

```javascript
// Named constants → immediate §0 candidates
const TAX_RATE = 0.21     // → [CODE: line N] — verify with user
const MAX_ITEMS = 50       // → [CODE: line N] — spec or implementation guess?

// Hardcoded numbers → red flags
if (score > 100) { ... }              // → why 100? Spec or arbitrary?
const dose = weight * 0.5             // → CRITICAL: DO NOT guess. Ask.
```

Present as:
> ✓ `MAX_SESSIONS = 5` [CODE: line 342] — [§0-CONFIRMED]. Verified.
> ⚠ `ramp_divisor = 15` [CODE: line 989] — not in §0. Flagging for confirmation.
> 🚨 `dose = weight * 0.5` [CODE: line 1204] — CRITICAL until confirmed.
> 🔲 `BASE_RATE` — needed but absent. Audit gap.

**Auto-escalation triggers:**

| Code Pattern | Escalation |
|-------------|-----------|
| `dose`, `dosage`, `medication`, `mg`, `mcg` | All findings → CRITICAL minimum |
| `payment`, `charge`, `billing`, `stripe` | Security findings → CRITICAL |
| `balance`, `transaction`, `transfer` | Logic + validation → CRITICAL |
| `float` for monetary values | Automatic CRITICAL |
| `age`, `minor`, `children` | Compliance → HIGH minimum |
| `password`, `token`, `secret` in localStorage | Automatic CRITICAL |

### §I.6. Adaptive Analysis Protocols

#### Mid-Work Reclassification

If discovered during any phase, **STOP and reclassify**:

| Discovery | Action |
|-----------|--------|
| Undisclosed financial transaction code | Stakes → HIGH; activate MOD-APP §K1, §C1 |
| Undisclosed health/dosage calculations | Stakes → CRITICAL immediately |
| PII persisted to localStorage | Full §C5, §C6 GDPR review |
| CDN scripts without SRI in payment/auth | Immediate CRITICAL |
| Dead code > 20% of codebase | Dead code analysis primary |
| Hardcoded credentials | CRITICAL — surface immediately |
| Missing code referenced by imports | Audit gap — affected findings are [THEORETICAL] |
| Code quality varies dramatically by section | Elevated scrutiny on lower-quality sections |

#### Partial Codebase Protocol

1. List what's NOT provided — backend, auth module, worker, etc.
2. Flag affected findings as `[THEORETICAL]`
3. Do not assume missing code is correct
4. State which dimensions are affected and what's needed to close the gap
5. Ask user to provide missing files before affected work

#### Novel Pattern Protocol

1. Describe precisely — what it does, what it resembles
2. Classify by analogy: "behaves like X via Y"
3. Apply nearest criteria, note the approximation
4. Flag: `[NON-STANDARD — criteria approximated via §X analogy]`

#### Code Quality Variance

If quality varies between sections (professional vs rushed): identify the
low-quality sections and apply elevated scrutiny. If low-quality sections
handle high-stakes logic → **highest-risk combination** → escalate all
findings in those sections by one severity level.

#### Signal Correlation

Some bugs are only visible when distant code locations are read together.
For every validation rule → find every place that validates the same concept
— do they agree? For every security assumption → trace if downstream code
violates it. Cross-reference findings before finalizing severity.


---

## §LAW. THE 12 IRON LAWS

Every module inherits these. No exception. No override. No "but in this case."

### Law 1 — Specificity Is Non-Negotiable
Every finding names the exact file, function, line number (or `near functionName`),
CSS class, or data value.

> **Violation:** "Improve error handling" — this is not a finding.
> **Correct:** "`handleImport()` at `utils/import.js:847` calls `JSON.parse()`
> without try/catch — non-JSON clipboard paste throws uncaught TypeError that
> crashes the React error boundary"

### Law 2 — Read Before Act
Never modify, simulate, audit, or restructure code you haven't read in the
current session. Even if you read it 5 turns ago — read again. Stale context
produces fiction, not findings.

> **Violation:** Fixing a function based on its name without reading its current code.
> **Correct:** Re-read the file, verify the function still exists at that line,
> then plan the fix based on actual current content.

### Law 3 — All Six Lenses
Every H5W-originated finding has all six lenses answered. `N/A — [reason]` is
acceptable; omission is not. Module findings inherit this when entering the queue.

> **Violation:** Finding with How, What, Where filled but Who, Will, When blank.
> **Correct:** `Who: N/A — affects all personas equally. When: N/A — always
> present, not timing-dependent.`

### Law 4 — Source Integrity
Every domain fact carries a source tag (§SRC). Training-recalled values are
`[UNVERIFIED]` — questions only, never finding bases. Code is the specification
until the user corrects it.

> **Violation:** "The rate should be 0.006 based on game data" — sourced from
> where? If from memory → `[UNVERIFIED]` → cannot be a finding.
> **Correct:** "Rate in code is 0.008 [CODE: config.js:42]. Cannot verify
> correct value — flagging for user confirmation."

### Law 5 — Bugs Before Refactors
Bug AND poor structure in the same function? Fix the bug first with a minimal
change. Structural improvement is a separate, lower-priority recommendation.

> **Violation:** "Refactored `calculateDPS()` into three functions and fixed the
> rounding bug" — one commit, two concerns, impossible to review.
> **Correct:** Commit 1: "Fix rounding bug in `calculateDPS()` line 84: `Math.round`
> → `Math.floor` for damage truncation." Commit 2 (later): "Extract helpers from
> `calculateDPS()` — no logic change."

### Law 6 — Feature Preservation Contract
Every working feature is innocent until proven broken. No recommendation may break,
remove, or diminish a working feature. Applies to optimization, polishing,
standardization, and refactoring equally.

> **Violation:** "Remove the legacy export format — the new one is better."
> If users rely on it → **rejected.**
> **Correct:** "Add the new format alongside. Deprecation is a separate decision
> for the user."

### Law 7 — Identity Preservation Contract
The app's intentional design character (§0 Design Identity) must not be erased.
A dark cyberpunk aesthetic with neon accents gets polished AS dark cyberpunk with
neon accents — not converted to a neutral gray Material dashboard.

> **Violation:** "Replaced custom color palette with Material Design 3 defaults
> for consistency."
> **Correct:** "Derived Material Design 3 token structure using the existing
> custom palette — consistency gained, identity preserved."

### Law 8 — Minimum Footprint
Every fix uses the smallest safe change that resolves the problem. A 3-line fix
is strictly preferred over a refactor that happens to fix the issue.

> **Violation:** "Restructured the component hierarchy, which also fixes the
> null pointer" — 200 lines changed, bug was 1 line.
> **Correct:** "Added null check at line 84. Structural improvement filed
> separately as enhancement."

### Law 9 — Reversibility Before Action
Every action classified by tier (§REV) before execution. Uncertain between
tiers → higher tier. T3 → explicit user permission, no exceptions.

> **Violation:** Deleting a feature flag system because "it's unused" without
> asking — this is T3 (potentially irreversible if data depends on it).
> **Correct:** "Feature flag system appears unused [INFERRED — no references
> found]. This is T3 — requesting confirmation before removal."

### Law 10 — Expansion Has Boundaries
Expandantic investigation is controlled, not runaway. Checkpoint triggers
(§SIM.5) are hard stops. 3 cycles or 5 files → stop and report.

> **Violation:** Fixed F-001, micro-H5W found F-006, fixed F-006, micro-H5W
> found F-012, fixed F-012, micro-H5W found F-018... 15 files changed,
> no checkpoint.
> **Correct:** After cycle 3: checkpoint report, wait for user "continue."

### Law 11 — Honesty Over Completeness
`[THEORETICAL]` with clear reasoning is infinitely more valuable than
`[CONFIRMED]` that is fabricated. Never invent line numbers, function names,
or behavior.

> **Violation:** "F-034: `processPayment()` at `api.js:203` has SQL injection"
> — but `api.js` only has 150 lines.
> **Correct:** "Cannot locate payment processing logic in provided code.
> [THEORETICAL] — if payment handling exists in unprovided backend, SQL
> injection surface should be audited."

### Law 12 — Verify or Don't Claim
Never claim a fix works without re-reading the modified code and tracing the
logic path. Runtime-only → `[UNVERIFIABLE — requires runtime test]`.

> **Violation:** "Fixed the race condition" — but didn't re-read to verify
> the fix doesn't introduce a new timing window.
> **Correct:** "Applied fix. Re-read: the mutex now covers lines 84–92.
> Traced: both code paths hit the mutex before the shared state. Verified."

### Compound Finding Chains
Some bugs chain: validation gap [LOW] → invalid engine value [MEDIUM] → wrong
display [HIGH] → bad real-world decision [CRITICAL]. When a chain exists:
document it as a numbered chain, escalate to the combined severity.

---

## §SRC. SOURCE INTEGRITY

### Source Tags

| Tag | Meaning | Can Support Finding? |
|-----|---------|---------------------|
| `[CODE: file:line]` | Read directly from source | Yes — ground truth for behavior |
| `[§0-CONFIRMED]` | User confirmed | Yes — the specification |
| `[WEB: official, vX.Y, date]` | Official docs via web search | Yes — after version check |
| `[WEB: patch-notes, vX.Y]` | Official patch/release notes | Yes — if version matches |
| `[WEB: official-wiki]` | Developer-maintained wiki | Yes — with date check |
| `[WEB: community-wiki, date]` | Community-maintained wiki | Conditional — needs corroboration |
| `[WEB: forum, date]` | Reddit, Discord, guides | No — lead only |
| `[WEB: aggregator]` | Secondary source | No — find the original |
| `[INFERRED]` | Deduced from code patterns | Flagged in H5W-ASSUMPTIONS.md |
| `[UNVERIFIED]` | Recalled from training data | **Never** — question to user only |

### Hierarchy (strongest → weakest)
`[§0-CONFIRMED]` → `[WEB: official]` → `[WEB: patch-notes]` → `[WEB: official-wiki]` → `[CODE]` alone → `[WEB: community]` → `[WEB: forum]` → `[UNVERIFIED]`

Only `[§0-CONFIRMED]` and `[WEB: official/patch-notes]` can assert "the code is
wrong." Everything below supports a question or flag — not a finding.

### Seven Rules

1. **Prefer official sources.** Don't cite wiki when docs exist.
2. **Record version and date.** `[WEB: official, v1.8, 2024-03]`.
3. **When sources conflict — surface it.** Never pick silently.
   > "⚠ SOURCE CONFLICT: Official docs [WEB: official, 2025-01] state X.
   > Community wiki [WEB: community, 2024-06] states Y. Please confirm."
4. **Community contradicts official → defer to official,** flag discrepancy.
5. **Only community sources → don't assert.** Flag as audit gap, ask user.
6. **Never silently prefer web over code.** Discrepancy = surfaced, not auto-fixed.
7. **Never use training memory as tiebreaker.** User arbitrates conflicts.

### Unreliable Domains (always treat recalled values as `[UNVERIFIED]`)

- Game mechanics / live-service constants — per-patch changes, wiki lag
- Third-party API limits — tier-dependent, silently changed
- Medical reference ranges — peer-reviewed publications only
- Community-derived formulas — approximations, not specifications

---

## §FMT. UNIFIED FINDING FORMAT

Every finding across every module uses this exact structure. No field omitted.

```
══════════════════════════════════════
FINDING: F-[NNN]
MODULE:  [H5W | APP | CODE | DESG | ART | SCOP | REST]
══════════════════════════════════════
SEVERITY:    critical | high | medium | low | enhancement
CONFIDENCE:  confirmed | high | medium | theoretical
SOURCE:      [CODE: file:line] — mandatory for confirmed/high
COMPOUNDS:   [yes ⏱ | no]

───── H5W LENSES (when generated via simulation) ──
How:   [mechanics — how the issue manifests]
Who:   [affected personas and contexts]
Will:  [impact projection if unfixed]
What:  [concrete issue + minimum fix]
When:  [trigger condition — timing, sequence, state]
Where: [file:line + UI location]

───── ACTION ──────────────────────────
FIX:        [specific action to take]
TIER:       [T0 | T1 | T2 | T3]
EXPANSION:  [what micro-H5W should check after this fix]
══════════════════════════════════════
```

### Confidence Levels

| Level | Evidence Required | Can Auto-Fix? |
|-------|-------------------|---------------|
| confirmed | `[CODE: file:line]` for every claim. Logic traced end-to-end. | Yes |
| high | `[CODE: file:line]` + `[INFERRED]` reasoning. | Yes, with ASSUMPTIONS entry |
| medium | `[CODE: file:line]` + `[THEORETICAL]` flag. | Only T0/T1 |
| theoretical | `[THEORETICAL]` only. | No — needs user confirmation first |

### Good Finding Example

```
══════════════════════════════════════
FINDING: F-012
MODULE:  H5W
══════════════════════════════════════
SEVERITY:    high
CONFIDENCE:  confirmed
SOURCE:      [CODE: components/TeamCard.jsx:84]
COMPOUNDS:   no

How:   TeamCard renders an empty <div> when team.members is [].
       No conditional — always renders the member list container.
Who:   P1 (first-time user) — sees blank card on Teams tab before adding members.
       P3 (hostile-env) — confusing after deleting last member.
Will:  User thinks the app is broken. May abandon. Compounds if multiple
       empty teams exist — entire screen looks blank.
What:  Missing empty-state check. Fix: add `{members.length === 0 && <EmptyState />}`
When:  On first render when teams array has a team with no members.
Where: components/TeamCard.jsx:84 — UI: Teams tab, individual card body.

FIX:        Add empty-state conditional at line 84.
TIER:       T1
EXPANSION:  Check other list renders for same missing empty-state pattern.
══════════════════════════════════════
```

### Bad Finding Example (what NOT to do)

```
FINDING: F-099
SEVERITY: medium
What: Error handling could be improved in the teams module.
Fix: Add better error handling.
```
↑ No file:line. No lens. No confidence. No persona. No specificity. Rejected.

---

## §REV. REVERSIBILITY TIERS

| Tier | Test | Obligation | Examples |
|------|------|-----------|----------|
| T0 | Undo <30s via `git checkout` | Execute silently | Reading, analysis, adding code comments |
| T1 | Undo <2min via `git checkout` | Execute + log in H5W-LOG | CSS fix, copy change, null check, small function fix |
| T2 | Multi-file revert or re-testing needed | Execute + log + rationale + flag assumptions | Component restructure, state management change, new dependency |
| T3 | Irreversible or ambiguous impact | **STOP. Ask user. No exceptions.** | Delete feature, schema change, API contract change, data migration |

**Decision rule:** Uncertain between tiers → always choose higher.

**Scenarios:**
- Adding a null check to prevent a crash → T1 (tiny, trivially reversible)
- Restructuring a component into three files → T2 (reversible but requires work)
- Removing a deprecated API endpoint → T3 (consumers may depend on it)
- Changing localStorage schema format → T3 (existing user data affected)

---

## §VER. VERIFICATION PROTOCOL

After every fix from any module:

| Check | Method | On Failure |
|-------|--------|-----------|
| Resolves finding | Re-read modified code, trace logic end-to-end | Revert, re-plan |
| No same-file regression | Check all functions in modified file | Revert, narrow scope |
| No consumer regression | Check files that import/consume modified code | Revert, cascade analysis |
| Feature Preservation | Confirm no working feature broken or diminished | Revert immediately |
| Identity Preservation | Confirm design character intact | Revert, re-approach |
| Type safety | If typed language — does it still compile? | Fix type errors first |
| Import integrity | All imports still resolve after changes? | Fix broken imports |

**If verification requires runtime (visual, interactive, timing):**
Tag: `[UNVERIFIABLE — requires runtime test: {describe what to test}]`
Log in H5W-ASSUMPTIONS.md with confidence score. Continue — don't block queue.

**Verification worked example:**
> Fixed F-012 (empty-state in TeamCard). Re-read TeamCard.jsx:
> - Line 84: conditional now checks `members.length === 0`
> - Line 85: renders `<EmptyState message="No members yet" />`
> - Line 86: else renders member list (unchanged)
> - EmptyState component exists at components/EmptyState.jsx (verified)
> - No other components import TeamCard's member rendering
> - Feature preserved: populated teams still render identically
> **Verified: yes.**


---

## §SIM — H5W SIMULATION ENGINE

The simulation engine is the brain of H5W. It generates a simulated user
population, maps the app's state space, walks personas through the app by
reading code, discovers issues through six lenses, and expands findings
through the micro-H5W loop.

### §SIM.1. Persona Generation

**Three mandatory personas (always generated):**

| ID | Type | Device | Expertise | Behavior | Purpose |
|----|------|--------|-----------|----------|---------|
| P1 | First-time | Mobile, slow 3G, small screen | None | Cautious, reads labels | Empty states, onboarding, unclear labels, first impressions |
| P2 | Power user | Desktop, fast, large screen | Expert | Rapid, uses shortcuts | Edges, efficiency, data limits, missing shortcuts |
| P3 | Hostile-env | Small screen, interrupted | Intermediate | Impatient, back-button | Error recovery, data loss, interrupted flows |

**Domain-specific personas (1–2 more, from §I.1):**

| Domain | Additional Persona | Focus |
|--------|--------------------|-------|
| Game companion | P4: Theorycrafting optimizer — tests formulas, boundary math, data density | Correctness at extremes |
| Game companion | P5: Casual collector — browsing, low commitment, visual-first | Scannability, delight |
| Medical | P4: Stressed clinician — time-critical, zero ambiguity, gloved hands | Clarity, touch targets, error cost |
| Productivity | P4: Admin for 50 users — bulk operations, edge permissions | Scale, batch actions |
| E-commerce | P4: Comparison shopper — rapid nav, tab-switching, price sensitivity | Speed, comparison flows |
| AI-powered | P4: Skeptical user — tests limits, questions outputs, probes failures | Failure modes, trust |

**Persona specification template:**
```yaml
PERSONA: P[N]
TYPE: first-time | power-user | hostile-env | [domain-specific name]
DEVICE: [concrete — "iPhone SE, 375×667, slow 3G" or "Desktop, 1920×1080"]
GOAL: [specific action — "add a team with 4 characters to compare DPS"]
BEHAVIOR: methodical | exploratory | impatient | careful | chaotic
ACCESSIBILITY: none | screen-reader | keyboard-only | reduced-motion | high-contrast
ENTRY POINT: [which route/screen they start from]
WALKTHROUGH SCRIPT: [sequence of actions this persona will attempt]
  1. [arrive at entry point]
  2. [first interaction]
  3. [core goal action]
  4. [disruption scenario]
  5. [edge scenario]
```

**Rules:**
- Goals must be specific. "Uses the app" is not a goal. "Adds a team with 4 characters" is.
- Devices must be concrete with dimensions. "Mobile" is not a device.
- At least one persona targets §0 Primary Device.
- At least one persona has an accessibility consideration.
- Walkthrough scripts are planned before execution — not improvised during.

**Worked example (game companion app):**
```yaml
PERSONA: P1
TYPE: first-time
DEVICE: Xiaomi 13T, 439×976 CSS, DPR 2.78, 4G
GOAL: Find and compare two characters' DPS
BEHAVIOR: exploratory — taps around, doesn't read instructions
ACCESSIBILITY: none
ENTRY POINT: / (home)
WALKTHROUGH SCRIPT:
  1. Arrive at home — what's visible? What invites interaction?
  2. Navigate to characters/teams — is the path obvious?
  3. Attempt to add two characters — can they find the add flow?
  4. Disruption: network drops mid-add — is data preserved?
  5. Edge: search for a character with special characters in name
```

### §SIM.2. State Space Mapping

Before walkthroughs, map the reachable state space by reading code.

**Step 1 — Enumerate screens:**
Read router config, navigation graph, AndroidManifest, or equivalent.
List every reachable screen with its route/path.

**Step 2 — State variables per screen:**
For each screen, identify every state variable that changes the render.

```yaml
Screen: /teams
  loading: boolean → skeleton vs content
  error: Error | null → content vs error message
  teams: Team[] → length determines:
    - 0: empty state
    - 1: single card
    - 2+: grid layout
  selectedTeam: string | null → list vs detail
  networkStatus: online | offline → live vs cached/stale
```

**Step 3 — Build transition matrix:**
```
FROM              → ACTION          → TO              → EDGE?  → HANDLER?
──────────────────────────────────────────────────────────────────────────
/teams:empty      → add team        → /teams:1-team   → —      → [CODE: Teams.jsx:42]
/teams:1-team     → delete team     → /teams:empty    → modal? → [CODE: Teams.jsx:68]
/teams:loaded     → network drop    → /teams:???      → YES    → ???
/teams:loaded     → resize < 440px  → /teams:mobile   → break? → [CODE: Teams.css:120]
/teams:detail     → back button     → /teams:loaded   → kept?  → ???
/teams:loading    → error response  → /teams:error    → retry? → [CODE: Teams.jsx:28]
```

**Step 4 — Mark investigation targets:**
- `???` in TO column → unknown behavior → **mandatory investigation**
- `YES` in EDGE column → known risk → **priority investigation**
- `???` in HANDLER column → no handler found → **likely crash/unhandled**
- Transitions with no code reference → dead path or missing implementation

**Worked example output:**
> State map for /teams: 6 states, 8 transitions.
> Investigation targets: 3 unknown (network drop, back-nav state,
> error→retry). 2 edge risks (responsive breakpoint, delete confirmation).
> Handler gaps: 2 (network drop, back-nav preservation).

### §SIM.3. Walkthrough Protocol

For each persona × each relevant entry point, execute the walkthrough script
by reading code as if running it in the persona's context. Apply all six
H5W lenses at four stages.

#### Stage 1 — ARRIVAL (what does the persona see on first render?)

Read the component that renders at this route. Trace from mount:
- What renders during loading? (Suspense, skeleton, spinner, blank?)
- What renders when data arrives? (first meaningful paint)
- What renders if data is empty? (empty state, or just... nothing?)
- What is the visual hierarchy? (what draws the eye first?)
- Where does focus land? (keyboard/screen-reader starting point)

Apply each lens:
```
How:   How does the page load? What's the sequence? Any flash/flicker?
Who:   Does P1 (first-time, mobile) understand what they're seeing?
Will:  Will P1 know what to do next? Is there a clear CTA?
What:  What's missing? (empty state text, loading skeleton, focus management)
When:  When does content appear? Is perceived performance acceptable?
Where: Where is P1's attention drawn? Is it the right place?
```

#### Stage 2 — INTERACTION (persona pursues their goal)

Follow the walkthrough script's action sequence through the code:
- Read the event handler for each interaction
- Trace state mutations from action to re-render
- Check validation logic for each input
- Check feedback for each action (confirmation, animation, state change)

Apply each lens:
```
How:   How does P1 perform the action? Steps? Click targets? Gesture areas?
Who:   Who else might try this differently? (keyboard vs touch, a11y routes)
Will:  Will the interaction succeed? What if input is edge-case?
What:  What feedback does P1 get? Immediate? Delayed? None?
When:  When does feedback arrive? Optimistic update? Spinner? Latency?
Where: Where does P1 go next? Is navigation clear? Dead ends?
```

#### Stage 3 — DISRUPTION (something goes wrong)

Inject failures from the walkthrough script:
- Network drop mid-action
- Back-button press during async operation
- Tab/app switch and return
- Error response from API
- Permission denial

Apply each lens:
```
How:   How does the app handle this failure? Error boundary? Fallback? Crash?
Who:   Who suffers most? (P3 hostile-env is the canonical victim)
Will:  Will recovery be possible? Data preserved? Session intact?
What:  What is lost? (form data, scroll position, navigation state)
When:  When does P3 learn something broke? Immediately? After retry?
Where: Where is the error surfaced? Toast? Inline? Console-only? Silent?
```

#### Stage 4 — EDGE (boundary push)

Push the persona's goal to extremes:
- 0 items, 1 item, 100+ items
- Empty string, max-length string, special characters, emoji
- Rapid repeated action (double-tap, spam-click)
- Concurrent state changes (two tabs, two users)
- Maximum data accumulation over time

Apply each lens:
```
How:   How does the app behave at this extreme? Graceful? Degraded? Crash?
Who:   Who realistically hits this? P2 power user is the canonical edge-finder.
Will:  What's the worst realistic outcome? Data loss? Security? Embarrassment?
What:  What exactly breaks? (specific component, function, render)
When:  When does the edge trigger? First time? After accumulation? Specific sequence?
Where: Where does it manifest? (component, API call, state store, render output)
```

#### Collation

After all personas complete all walkthroughs:
1. Group findings by screen/component location.
2. Tag findings appearing across 2+ personas → priority boost.
3. Identify persona-unique findings → context-specific gaps.
4. Format all findings using §FMT.
5. Enter all findings into H5W-QUEUE.md.
6. Sort by §V.2 priority rules.

### §SIM.4. Expansion Protocol (Micro-H5W)

After every fix — from any module, any phase — apply all six lenses to the
fix itself. This is the expandantic mechanism: each fix seeds further investigation.

**Micro-H5W template:**
```
MICRO-H5W ON FIX F-[NNN] — [file:line]
──────────────────────────────────────────
How   → Does this fix interact with adjacent code?
        Action: check imports and consumers of the modified file.
        Result: [list affected files or "no consumers"]

Who   → Is anyone else affected by this change?
        Action: check components/functions that share state with this code.
        Result: [list shared state consumers or "isolated"]

Will  → Could this cause a regression?
        Action: trace the logic path through the fix. Check boundary inputs.
        Result: [describe traced path + conclusion]

What  → Does this fix reveal an adjacent issue?
        Action: now that this is fixed, re-read surrounding code.
        Result: [describe adjacent issue or "none found"]

When  → Could this fix behave differently under other states?
        Action: check what happens when state is empty, null, error, max.
        Result: [list states checked + any issues]

Where → Does the same pre-fix pattern exist elsewhere?
        Action: grep the codebase for the pattern that was fixed.
        Result: [list matches with file:line or "unique instance"]
        → If 3+ matches: invoke MOD-SCOP for systematic fix.
──────────────────────────────────────────
New findings: [F-NNN, F-NNN] → H5W-QUEUE.md
No findings: [proceed to next in queue]
```

**Worked example:**
```
MICRO-H5W ON FIX F-012 — components/TeamCard.jsx:84
──────────────────────────────────────────
How   → TeamCard is imported by TeamsPage.jsx and TeamCompare.jsx.
        Both render TeamCard with the same props interface.
Who   → TeamCompare.jsx also passes team.members — same empty risk.
Will  → Traced: TeamCompare renders cards side-by-side. If both teams
        have 0 members → two empty states side by side → confusing layout.
What  → Adjacent issue: TeamCompare has no "both empty" layout handling.
        → NEW FINDING F-018.
When  → Checked: empty, 1 member, 10 members — all render correctly now.
Where → Grep for `.members.map(` without empty check:
        - TeamCompare.jsx:62 — SAME PATTERN → F-018
        - CharacterList.jsx:34 — SAME PATTERN → F-019
        → 3 matches → consider MOD-SCOP for systematic fix.
──────────────────────────────────────────
New findings: F-018 (TeamCompare empty), F-019 (CharacterList empty)
```

### §SIM.5. Checkpoint Protocol

**Stop and report when ANY trigger fires:**

| Trigger | Standard Mode | Continuous Mode |
|---------|--------------|-----------------|
| Expansion cycles since last checkpoint | 3 | 5 |
| Files modified since last checkpoint | 5 | 10 |
| T3 decision encountered | Immediate | Immediate |
| Queue empty | Session end | Session end |
| User interrupt | Immediate | Immediate |

**Checkpoint report template:**
```
══════════════════════════════════════════
H5W CHECKPOINT — Cycle [N]
══════════════════════════════════════════
SCOPE:    [area examined]
MODE:     [full | targeted | expansion | continuous]
MODULES:  [active modules this cycle]
PERSONAS: [IDs + types used]
STATES:   [explored / total mapped]

─── METRICS ──────────────────────────────
FOUND: [total findings this cycle]
FIXED: [n] (T0: [n] T1: [n] T2: [n])
QUEUED: [n] (remaining)
BLOCKED: [n] (T3 — needs user)

─── FIXES APPLIED ────────────────────────
  F-001 [sev] [tier] [mod] [file] — [summary]
  F-002 [sev] [tier] [mod] [file] — [summary]

─── BLOCKED (T3) ─────────────────────────
  F-005 — [description + why T3 + what user needs to decide]

─── EXPANSION CANDIDATES ─────────────────
  [what micro-H5W surfaced as next targets]

─── ASSUMPTIONS ACTIVE ────────────────────
  A-001 [conf 3/5] — [assumption + impact if wrong]
  A-002 [conf 4/5] — [assumption]

─── COMPOUNDS (⏱) ────────────────────────
  F-003 → F-007 → F-015 chain: [desc of compound risk]

Continue? [yes / no / redirect scope / confirm assumption / resolve T3]
══════════════════════════════════════════
```


---

## H5W LENS CATEGORIES

Six analysis dimensions. Each has sub-codes. During walkthrough (§SIM.3), all
six lenses are applied at every stage. During micro-H5W (§SIM.4), all six
are applied to every fix. Module findings also map to these lenses.

### §H — HOW (Mechanics & Flow)

How the user interacts, how data flows, how the app responds.

| Code | Focus | What It Discovers | Maps to Module |
|------|-------|-------------------|----------------|
| §H.1 | **User Flow Mechanics** | Dead ends, circular flows, missing back-nav, step count | MOD-APP §F2 |
| §H.2 | **Data Flow Tracing** | Input → processing → display gaps, transform errors | MOD-CODE §D5, §D6 |
| §H.3 | **Render & Lifecycle** | Mount/update/unmount bugs, stale renders, flash | MOD-CODE §D8 |
| §H.4 | **Interaction Feedback** | Missing loading, confirmation, animation, latency | MOD-APP §F5, MOD-DESG §DM |
| §H.5 | **Integration Seams** | Cross-component, cross-module, API boundary issues | MOD-CODE §D4 |

**§H questions for walkthrough:**
- How does the user reach this state? How many steps? Is there a shorter path?
- How does data flow from user input to final display? Where could it corrupt?
- How does the component lifecycle interact with async operations?
- How quickly does the app acknowledge the user's action? What's the feedback?
- How do components at the seam communicate? Props? Context? Events? Globals?

### §W — WHO (Personas & Context)

Who the users are, their constraints, their contexts.

| Code | Focus | What It Discovers | Maps to Module |
|------|-------|-------------------|----------------|
| §W.1 | **Persona Coverage** | Which user types are well-served vs underserved | §SIM.1 |
| §W.2 | **Device & Viewport** | Breakpoint gaps, touch targets, orientation, DPR | MOD-APP §H3 |
| §W.3 | **Accessibility Context** | Screen reader paths, keyboard nav, contrast, motion | MOD-APP §G |
| §W.4 | **Expertise Mismatch** | Jargon exposure, disclosure gaps, onboarding holes | MOD-APP §F3, §F4 |
| §W.5 | **Maintainer Perspective** | Code clarity for future devs, documentation gaps | MOD-CODE §D1, §D4 |

**§W questions for walkthrough:**
- Who is this feature built for? Is the target persona actually served well?
- Who is excluded? (small screen, keyboard-only, slow connection, low vision)
- Who would be confused by this label, this flow, this interaction?
- Who needs expert-level features? Are they discoverable without overwhelming beginners?
- Who maintains this code next? Would they understand the intent?

### §P — WILL (Prediction & Edge Cases)

What will happen under stress, failure, and edge conditions.

| Code | Focus | What It Discovers | Maps to Module |
|------|-------|-------------------|----------------|
| §P.1 | **Boundary Inputs** | Empty, zero, max, malformed, special chars | MOD-APP §B3, MOD-CODE §D5 |
| §P.2 | **Expectation Gaps** | What user expects vs what happens | MOD-APP §F2 |
| §P.3 | **Dependency Failures** | Network, API, data source, storage quota | MOD-APP §H4, MOD-CODE §D7 |
| §P.4 | **Rapid Interaction** | Double-submit, debounce, race conditions | MOD-CODE §D8, MOD-APP §A6 |
| §P.5 | **Temporal Accumulation** | Data growth, cache bloat, memory leaks over months | MOD-APP §O1, MOD-CODE §D3 |

**§P questions for walkthrough:**
- What happens with 0 items? 1 item? 1000 items? Max-length text? Emoji? RTL?
- What does the user expect to see after this action? Does the app match?
- What happens if the network drops right now? API returns 500? Storage is full?
- What happens if the user clicks this button 10 times in 1 second?
- What happens to this screen in 6 months when 10x data has accumulated?

### §T — WHAT (Concrete Issue & Fix)

The specific defect, its root cause, and the minimum change.

| Code | Focus | What It Discovers | Maps to Module |
|------|-------|-------------------|----------------|
| §T.1 | **Symptom vs Root** | Surface behavior vs underlying defect | All modules |
| §T.2 | **Minimum Change** | Smallest safe fix that resolves the issue | §LAW Law 8 |
| §T.3 | **Impact Radius** | What else the fix touches, who consumes it | §SIM.4 micro-H5W |
| §T.4 | **Verification Criteria** | What "fixed" looks like — testable condition | §VER |

**§T questions for walkthrough:**
- What exactly is wrong? (specifics, not "feels off" — cite file:line)
- Is what I see the root cause, or a symptom of something deeper?
- What is the absolute minimum change to fix this? (3 lines? 1 line?)
- What files import/consume the code I'd change? What's the blast radius?
- What would a test for "fixed" look like? What input → what output?

### §N — WHEN (Timing & Sequence)

When issues occur — timing, lifecycle, state sequences.

| Code | Focus | What It Discovers | Maps to Module |
|------|-------|-------------------|----------------|
| §N.1 | **Journey Position** | First visit vs Nth visit behavior differences | MOD-APP §F3 |
| §N.2 | **Component Lifecycle** | Mount/update/unmount timing, effect cleanup | MOD-CODE §D8 |
| §N.3 | **State Transitions** | When transitions fire, ordering dependencies | MOD-APP §A4, §A6 |
| §N.4 | **Timing Windows** | Race conditions, debounce gaps, animation conflicts | MOD-CODE §D8 |
| §N.5 | **Regression Timeline** | When would a regression from this fix show up? | §SIM.4, §VER |

**§N questions for walkthrough:**
- When in the user's journey does this happen? Only first visit? Every visit?
- When in the component lifecycle? On mount? On re-render? On cleanup?
- When does this state transition fire? Before or after the dependent state?
- Is there a timing window where two operations race? What's the debounce?
- If this fix introduces a regression, when would the user first notice?

### §L — WHERE (Localization & Cascade)

Where the issue lives, where it cascades, where the pattern repeats.

| Code | Focus | What It Discovers | Maps to Module |
|------|-------|-------------------|----------------|
| §L.1 | **UI Localization** | Screen, component, element where user sees the issue | MOD-APP §F, §E |
| §L.2 | **Code Localization** | File, function, line where root cause lives | MOD-CODE all dims |
| §L.3 | **Pattern Repetition** | Same anti-pattern elsewhere in codebase | MOD-SCOP |
| §L.4 | **Cascade Mapping** | Imports, consumers, tests affected by a fix | §SIM.4 |
| §L.5 | **Architectural Placement** | Where the fix should live (component, hook, util, store) | MOD-CODE §D4, MOD-REST |

**§L questions for walkthrough:**
- Where in the UI does the user see this problem? Which screen, component, element?
- Where in the code is the actual root cause? (file:line, not just "in the teams module")
- Where else does this exact pattern appear? Grep for it — how many matches?
  - If 3+ matches → invoke MOD-SCOP for systematic treatment.
- Where does a fix here cascade? What imports this? What consumes this output?
- Where should the fix live architecturally? Is this a component concern, a hook,
  a utility function, or a store-level change?


---

## §BUILD — BUILD FROM SCRATCH PROTOCOL

When the user wants to build a new app, H5W orchestrates the full pipeline from
discovery through launch-readiness. This is not "generate some code" — this is a
structured engineering process with verification gates between every phase.

### Build Phases

| Phase | What Happens | Owner | Gate |
|-------|-------------|-------|------|
| B1. Discovery | Problem, audience, constraints, features, non-goals | Chief Guide | User approves brief |
| B2. Architecture | Stack, data model, state, API, error strategy, file org | Chief Guide | User approves architecture |
| B3. Design System | Art direction, tokens, palette, typography, components | MOD-ART | User approves design |
| B4. Scaffold | File structure, routing, state stores, build config, tooling | Chief Guide + MOD-REST | Builds clean |
| B5. Implement | Feature-by-feature vertical slices with per-feature gates | Direct coding | Per-feature gate |
| B6. Integrate | Cross-feature flows, shared state, navigation, data sync | Direct coding | Integration gate |
| B7. Quality | Full MOD-CODE audit + H5W simulation on complete codebase | MOD-CODE + §SIM | Passes audit |
| B8. Polish | Visual refinement, interaction polish, copy, a11y, perf | MOD-DESG + MOD-APP | Passes design + a11y |
| B9. Launch Gate | Full H5W simulation + MOD-APP security/perf/compat | MOD-APP + §SIM | Ready to ship |

---

### B1. Discovery — Define the Problem

Fill Chief Guide §0 from the user's brief (no code to extract yet).

**Mandatory questions (ask via `AskUserQuestion` if not provided):**

```yaml
DISCOVERY BRIEF:
  Problem Statement:    # What problem does this solve? (one sentence)
  Target User:          # Who uses this? Be specific — not "everyone"
  User Goal:            # What does the user accomplish with this app?
  Stakes:               # LOW | MEDIUM | HIGH | CRITICAL
  Core Features:        # 3–5 features, priority-ordered
    1.                  # MVP — the one feature that makes the app useful
    2.                  # Second priority
    3.                  # Third priority
  Non-Goals:            # Explicitly exclude — "NOT a social network"
  Primary Device:       # Phone? Desktop? Tablet? Which one FIRST?
  Visual Direction:     # "Dark and atmospheric" / "Clean and minimal" / reference
  Data Persistence:     # Where does data live? Local? Server? Both?
  Offline Required:     # Must it work without network?
  Auth Required:        # Does it need user accounts?
  Deployment Target:    # Vercel? Play Store? App Store? Self-hosted?
```

**Gate:** Present the brief to the user. Get explicit "yes, build this."

---

### B2. Architecture — Design the System

Every decision logged in H5W-LOG.md with rationale. These are T2 decisions —
reversible but costly. Get user confirmation on the full architecture before coding.

#### B2.1 Stack Selection

| Requirement | Decision Path |
|------------|---------------|
| Web + SEO critical | → Next.js (App Router, SSR) |
| Web + SPA, modern tooling | → Vite + React 18 / Vue 3 |
| Web + zero-build simplicity | → CDN React (single file, no node_modules) |
| Web + content-heavy, minimal JS | → Astro + islands |
| Android native, stable ecosystem | → Kotlin + XML Views + Material Design 3 |
| Android native, modern declarative | → Kotlin + Jetpack Compose + Material 3 |
| iOS native | → SwiftUI (iOS 15+) or UIKit (legacy compat) |
| Cross-platform, perf-critical | → Flutter + Dart |
| Cross-platform, JS ecosystem | → React Native + Expo |
| CLI tool | → Node.js or Python |
| Simple calculator/tool | → Vanilla HTML/CSS/JS |

**Log:** `DECISION: Stack = [choice]. Rationale: [why]. Alternatives considered: [what and why not].`

#### B2.2 Data Modeling

Define every entity the app works with before writing code.

```yaml
DATA MODEL:
  Entities:
    - name: [Entity]
      fields:
        - name: id          type: string    required: true    generated: true
        - name: createdAt   type: datetime  required: true    generated: true
        - name: [field]     type: [type]    required: [bool]  validation: [rules]
      relationships:
        - type: hasMany | belongsTo | hasOne
          target: [OtherEntity]
          through: [field]

  Derived Data:
    - name: [computed value]
      from: [which entities/fields]
      formula: [how computed]
      cache: [yes/no — recompute on every render or cache?]
```

**Validation rules per field:** Define at model level, not at UI level. UI just renders the model's validation. This prevents validation-at-A-but-not-at-B bugs.

#### B2.3 State Architecture

| Question | Decision |
|----------|----------|
| What state is UI-only? (form inputs, modals, tooltips) | → Component-local state |
| What state is shared across screens? | → Global store (Zustand/Context/ViewModel) |
| What state persists across sessions? | → Persistence layer (localStorage/Room/CoreData) |
| What state comes from a server? | → Server state (React Query/SWR/Retrofit) |
| What state is derived from other state? | → Computed/derived — NEVER stored independently |

**State shape spec:**
```yaml
STATE STORES:
  - name: [storeName]
    scope: global | feature | screen
    persistence: none | localStorage | Room | API
    shape:
      [field]: [type]    # with initial value
    actions:
      [actionName]: [what it does]
    selectors:
      [selectorName]: [what it derives]
```

**Iron rule:** Derived state is NEVER stored. It is computed from source state.
Storing derived state creates synchronization bugs — the #1 state management failure.

#### B2.4 Error Strategy

Define before coding — not as afterthought.

| Error Type | Strategy |
|-----------|----------|
| Network failure | Retry with backoff → fallback UI → user notification |
| Validation failure | Inline field errors → prevent submission → preserve input |
| Unexpected crash | Error boundary (React) / try-catch (imperative) → crash report → recovery UI |
| Data corruption | Validate on read → repair or discard → notify user |
| Permission denied | Explain why → offer alternative → never silent fail |
| Empty state | Explicit empty-state UI per screen — NEVER blank |

**Per screen, define:** What happens when the primary data source fails?
This prevents the most common build gap: screens that work on success but
crash/blank on failure.

#### B2.5 API & Data Contract (if backend-connected)

```yaml
API CONTRACTS:
  - endpoint: [path]
    method: [GET/POST/PUT/DELETE]
    request: [shape]
    response: [shape]
    error: [error shape]
    auth: [required/optional/none]
    cache: [strategy]
    offline: [behavior when offline]
```

If building client-only: define the persistence contract instead —
what goes to localStorage/Room/CoreData, what format, what migration
path when schema changes.

#### B2.6 Component Architecture

| Level | What | Naming | State |
|-------|------|--------|-------|
| Page/Screen | Route entry point | `[Name]Page` / `[Name]Screen` / `[Name]Fragment` | Owns data fetching, passes to children |
| Container | Feature logic wrapper | `[Name]Container` | Owns feature state, passes UI props |
| Component | Reusable UI element | `[Name]` (no suffix) | Stateless or UI-only state |
| Primitive | Design system atom | `Button`, `Input`, `Card` | Stateless, style-only props |

**Prop flow rule:** Data flows down. Events flow up. No prop drilling beyond
2 levels — at level 3, use context/store. This prevents prop drilling debt
that compounds as the app grows.

#### B2.7 Testing Strategy

| Layer | What to Test | Tool | When |
|-------|-------------|------|------|
| Unit | Business logic, utils, formatters | Jest/JUnit | Per function |
| Component | Render output, interaction | RTL/Compose Test | Per component |
| Integration | Multi-component flows | RTL/Espresso | Per feature |
| E2E | Full user journeys | Playwright/Maestro | Pre-launch |

For MVPs: minimum = unit tests on business logic + H5W simulation (no E2E).

**Architecture document:**
```yaml
ARCHITECTURE DECISION RECORD:
  Stack:        [from B2.1]
  Data Model:   [from B2.2]
  State:        [from B2.3]
  Errors:       [from B2.4]
  API/Persist:  [from B2.5]
  Components:   [from B2.6]
  Testing:      [from B2.7]
```

**Gate:** Present architecture to user. Confirm before coding.

---

### B3. Design System (→ MOD-ART)

Load `references/mod-art-direction.md`. Execute: §BRIEF → §BUILD → §CHECK.

**Required outputs from MOD-ART:**

| Deliverable | What It Contains |
|-------------|------------------|
| Color tokens | Background, surface (2-3 levels), text (3 levels), accent, semantic (error, success, warning, info), dark mode variants |
| Typography scale | Font families (display + body + mono), size scale (6-8 steps), weight scale, line heights, letter spacing |
| Spacing scale | 4px base unit (or 8px), scale: 4/8/12/16/24/32/48/64 |
| Component library | Button (primary/secondary/ghost/destructive × default/hover/active/disabled), Input, Card, Modal, Navigation, List item, Empty state, Loading skeleton |
| Motion vocabulary | Duration scale (instant/fast/normal/slow), easing curves, entry/exit patterns |
| Iconography | Icon style (outlined/filled/duotone), size scale, source library |

**Gate:** User approves the design system before implementation.

---

### B4. Scaffold — Create the Structure

Based on B2 architecture decisions, create the file structure.

**Web / React scaffold:**
```
src/
├── app/                    # Routes/pages
│   ├── layout.tsx          # Root layout
│   ├── page.tsx            # Home
│   └── [feature]/
│       └── page.tsx
├── components/
│   ├── ui/                 # Design system primitives
│   │   ├── Button.tsx
│   │   ├── Card.tsx
│   │   └── Input.tsx
│   └── [feature]/          # Feature-specific components
├── lib/
│   ├── store.ts            # State management
│   ├── types.ts            # Data model types
│   ├── utils.ts            # Pure utility functions
│   └── constants.ts        # App constants
├── hooks/                  # Custom hooks
└── styles/
    ├── tokens.css          # Design tokens as CSS vars
    └── globals.css         # Global styles
```

**Android / Kotlin scaffold:**
```
app/src/main/
├── java/.../
│   ├── data/
│   │   ├── model/          # Data classes
│   │   ├── repository/     # Data access
│   │   └── local/          # Room DAOs, SharedPrefs
│   ├── ui/
│   │   ├── theme/          # Colors, Typography, Theme
│   │   ├── components/     # Reusable composables / custom views
│   │   └── [feature]/
│   │       ├── [Feature]Fragment.kt  (or [Feature]Screen.kt)
│   │       └── [Feature]ViewModel.kt
│   └── util/               # Extensions, formatters
├── res/
│   ├── values/             # colors.xml, strings.xml, dimens.xml, themes.xml
│   ├── values-night/       # Dark theme overrides
│   ├── layout/             # XML layouts (if not Compose)
│   └── navigation/         # nav_graph.xml
└── AndroidManifest.xml
```

**Scaffold includes:**
1. File structure created
2. Build config (package.json / build.gradle) with dependencies
3. Design tokens applied (from B3)
4. Navigation/routing skeleton
5. State store skeleton (empty stores with correct shape)
6. Empty-state and loading-state components
7. Dev environment configured:
   - Linting: ESLint + Prettier / ktlint + detekt (match §0 conventions)
   - Formatting: auto-format on save configured
   - Git: `.gitignore`, initial commit, branch strategy
   - TypeScript: `strict: true` if TS project (no `any` escapes)
   - Editor config: `.editorconfig` for consistent whitespace

**Gate:** `npm run build` / `./gradlew build` succeeds. Linting passes. App launches to blank home screen.

---

### B5. Implement — Per-Feature Vertical Slices

Build ONE feature at a time. Complete it end-to-end before starting the next.

**Feature implementation order:**
1. Core data model + persistence (the foundation everything else depends on)
2. MVP feature (Feature #1 from discovery — the reason the app exists)
3. Second feature (builds on core data)
4. Third feature
5. ...repeat until all features complete

**Per-feature implementation sequence:**
```
1. DATA:  Define types/models for this feature
2. STATE: Create store/ViewModel for this feature's state
3. UI:    Build the screen — start with the happy path
4. LOGIC: Wire data flow: user action → state mutation → re-render
5. ERROR: Add error handling per B2.4 strategy
6. EMPTY: Add empty-state UI
7. LOAD:  Add loading-state UI
8. GATE:  Run per-feature quality gate
```

**Per-feature gate (mandatory before next feature):**
```
[ ] Happy path works: user can accomplish the feature's goal
[ ] Error path works: every failure scenario from B2.4 is handled
[ ] Empty state: screen looks correct with no data
[ ] Loading state: screen shows feedback during async operations
[ ] MOD-CODE §D5 (logic): no logic errors in new code
[ ] MOD-CODE §D7 (errors): error handling present and correct
[ ] H5W Stage 1 (arrival): first render is correct for P1 (first-time)
[ ] H5W Stage 2 (interaction): goal achievable for P2 (power user)
[ ] H5W Stage 3 (disruption): P3 (hostile-env) can recover from failure
[ ] Types: all data flows are typed — no `any`, no implicit coercion
[ ] No T2+ changes to already-gated features
[ ] Commit: atomic commit for this feature — "feat: [feature name]"
```

---

### B6. Integrate — Cross-Feature Flows

After all features are built individually, wire them together:

```
[ ] Navigation: every screen reachable, back-nav works, deep links resolve
[ ] Shared state: features that share data are synchronized
[ ] Cross-feature flows: multi-step journeys work end-to-end
[ ] Data consistency: creating/editing/deleting in one feature reflects in others
[ ] Loading orchestration: no waterfall loads — parallel where possible
[ ] Error propagation: error in shared data surfaces in all consuming features
```

**Gate:** All cross-feature workflows from §0 work end-to-end.

---

### B7. Quality — Full System Audit

Load MOD-CODE. Run all 8 dimensions on the complete codebase:

```
[ ] §D1 Format & Conventions: naming consistent, imports ordered
[ ] §D2 Health & Hygiene: no dead code, no duplication, no dependency issues
[ ] §D3 Optimization: no render waste, no unnecessary re-computations
[ ] §D4 Structure & Architecture: SRP, clean module boundaries
[ ] §D5 Logic & Correctness: every formula correct, types sound
[ ] §D6 State & Data: single source of truth, no derived state stored
[ ] §D7 Error Handling: every failure path covered
[ ] §D8 Async & Concurrency: no races, proper cleanup, cancellation
```

Then run full H5W simulation:
```
[ ] All personas (P1–P5) walk through all entry points
[ ] All states from state map explored
[ ] All 4 stages × 6 lenses applied
[ ] All findings entered in H5W-QUEUE.md
[ ] All T0/T1 findings fixed
[ ] All T2 findings fixed with rationale
[ ] T3 findings surfaced to user
```

---

### B8. Polish — Refinement Pass

Load MOD-DESG. Run quick visual audit:

```
[ ] Token consistency: all colors from palette, no hardcoded values
[ ] Typography consistency: all text uses type scale
[ ] Spacing consistency: all spacing from spacing scale
[ ] Component consistency: all instances of same component look identical
[ ] Dark mode: if applicable, every screen verified in dark mode
[ ] Empty states: every screen has a designed empty state (not just blank)
[ ] Loading states: every async screen has skeleton/spinner
[ ] Error states: every error has a designed error UI
[ ] Micro-interactions: button feedback, form validation, transitions
[ ] Responsive: primary device + 2 others verified
```

Load MOD-APP for targeted checks:
```
[ ] §G Accessibility: keyboard nav, contrast ratios, screen reader labels
[ ] §D Performance: load time within budget, no memory leaks, no jank
[ ] §F4 Copy: all labels clear, all error messages helpful
```

---

### B9. Launch Gate — Ship Readiness

```
[ ] H5W simulation: 0 critical, 0 high findings remaining
[ ] MOD-APP §C (security): no critical security findings
[ ] MOD-APP §D (performance): within §0 budget
[ ] MOD-APP §G (accessibility): WCAG 2.1 AA minimum
[ ] MOD-APP §H (compatibility): primary device + 2 others
[ ] All T3 decisions resolved with user
[ ] H5W-QUEUE.md: empty or only low/enhancement remaining
[ ] Documentation: README with setup, architecture, deployment
[ ] Deployment config: verified on target platform
[ ] Error tracking: crash reporting configured (if applicable)
```

**The build is complete when:** A new developer could clone the repo, read the
README, understand the architecture, and add a new feature without asking the
builder. The code explains itself. The design is consistent. The errors are
handled. The states are covered.

### Build Reversibility

| Phase | Tier | Rationale |
|-------|------|-----------|
| B1–B3 (Discovery, Architecture, Design) | T0 | Planning documents only |
| B4 (Scaffold) | T1 | New files only — trivially reversible |
| B5 (Implement features) | T1 per feature | New code — git revert per feature |
| B6 (Integration) | T2 | Cross-feature wiring harder to revert cleanly |
| B7–B8 (Quality, Polish) | T1 | Fixes are small and surgical |
| B9 (Launch config) | T2 | Deployment config affects production |

---

## §WORKFLOW — INTER-MODULE WORKFLOWS

How modules talk to each other through the Chief Guide. These are concrete
handoff protocols, not abstract descriptions.

### Workflow 1: Simulation → Module Audit → Fix → Expansion

The most common workflow. H5W discovers, modules analyze, fixes flow back.

```
H5W SIMULATION (§SIM.3)
  ↓ Finding F-012: empty state missing in TeamCard
  ↓ Finding F-013: color contrast too low on card header
  ↓ Finding F-014: race condition in team deletion
  │
  ├─ F-012 is a code issue → stays in H5W queue → fix directly
  │  ↓ Fix F-012 → micro-H5W → F-018, F-019 found → queue
  │
  ├─ F-013 is visual → 3+ visual findings? YES →
  │  ↓ HANDOFF TO MOD-DESG
  │  ↓ Log in H5W-LOG: "Handoff: F-013 + 2 visual findings → MOD-DESG"
  │  ↓ Pass §0 context (already filled)
  │  ↓ MOD-DESG runs relevant sections → produces design findings
  │  ↓ Design findings enter H5W-QUEUE with source: [MOD-DESG]
  │  ↓ Re-sort queue, continue execution
  │
  └─ F-014 is async/concurrency →
     ↓ HANDOFF TO MOD-CODE §D8
     ↓ MOD-CODE §D8 analyzes race condition in depth
     ↓ Produces code finding with fix recommendation
     ↓ Finding enters H5W-QUEUE with source: [MOD-CODE §D8]
     ↓ Fix → micro-H5W → expansion continues
```

### Workflow 2: Build → Art Direction → Code → Audit

From-scratch build workflow.

```
§BUILD B1–B2 (Discovery + Architecture)
  ↓ §0 filled, stack decided
  │
  ↓ §BUILD B3 → LOAD MOD-ART
  │ MOD-ART §BRIEF → §BUILD → §CHECK
  │ Produces: design system (tokens, components, type, color)
  │
  ↓ §BUILD B4 → LOAD MOD-REST (scaffold only)
  │ File structure created based on architecture decision
  │
  ↓ §BUILD B5 → Implement features one by one
  │ After EACH feature:
  │   ↓ MOD-CODE §D5 (logic check)
  │   ↓ H5W Stage 1+2 (arrival + interaction walkthrough)
  │   ↓ Fix any findings before next feature
  │
  ↓ §BUILD B6 → LOAD MOD-CODE (full audit)
  ↓ §BUILD B7 → LOAD MOD-DESG (polish pass)
  ↓ §BUILD B8 → LOAD MOD-APP (launch gate)
  ↓ All gates passed → ready to ship
```

### Workflow 3: Audit → Restructure → Verify

When audit reveals structural problems.

```
MOD-APP audit (or H5W simulation)
  ↓ Findings reveal: God component, circular deps, layer violations
  ↓ These are structural → not point fixes
  │
  ↓ HANDOFF TO MOD-REST
  │ Log: "Handoff: structural findings F-030, F-031, F-032 → MOD-REST"
  │ Pass: §0 + audit findings as prior context
  │ MOD-REST runs: §R1 archaeology → §R2 mapping → §R3 diagnosis
  │ MOD-REST produces: migration plan (§R6) → user approves
  │ MOD-REST executes: one atomic operation at a time
  │
  ↓ After each restructuring operation:
  │ H5W micro-H5W runs on every changed file
  │ MOD-CODE §D1 (naming/format) verifies conventions maintained
  │ New findings → queue
  │
  ↓ Checkpoint after restructuring complete
```

### Workflow 4: Scope Consistency Fix

When micro-H5W discovers a pattern repeated across the codebase.

```
MICRO-H5W on F-012 fix
  ↓ §L.3 (pattern repetition): grep finds 5 instances of same anti-pattern
  ↓ 3+ matches → HANDOFF TO MOD-SCOP
  │
  ↓ MOD-SCOP §III (Large-Scope Protocol):
  │ Step 1: Build Concept Scaffold — define "what is this pattern"
  │ Step 2: Present scaffold for user approval
  │ Step 3: Exhaustive scan — find ALL instances
  │ Step 4: Human verification gate — confirm the list
  │ Step 5: Execute with tracking — fix each instance
  │ Step 6: Concept drift detection — verify no new instances
  │
  ↓ Each MOD-SCOP fix → micro-H5W expansion
  ↓ Checkpoint when MOD-SCOP completes
```

### Handoff Protocol

When H5W routes to a module:

1. **Log** in H5W-LOG.md: which findings triggered it, which module, what's expected back.
2. **Pass** §0 context — already filled, module inherits it. Do NOT re-fill.
3. **Pass** relevant findings — module receives the specific findings to investigate.
4. **Module works** under Chief Guide shared protocols (§LAW, §FMT, §SRC, §REV, §VER).
5. **Module returns** findings in §FMT format → enter H5W-QUEUE.md.
6. **Re-sort** queue (§V.2 priority rules).
7. **Continue** execution from the queue.

### When NOT to Hand Off

- Single finding in a domain → handle directly, don't load a module
- Finding you can fix with 3 lines → fix it, don't delegate
- MOD-SCOP → only when 3+ instances of a pattern found
- MOD-REST → only when structural problems, not point fixes
- MOD-DESG → only when 3+ visual findings in same area, not one color issue

---

## §SESSION — SESSION CONTINUITY

### Starting a New Session on a Known App

1. Check §0 Cross-Session Continuity block for prior session data.
2. Load prior H5W-QUEUE.md → remaining findings become the starting queue.
3. Load prior H5W-ASSUMPTIONS.md → active assumptions carry forward.
4. Announce: "Resuming from prior session. [N] findings queued, [M] assumptions active."

### Conflict Resolution

When this session's findings conflict with prior confirmed rules:
```
CONFLICT: Prior session confirmed BASE_RATE = 0.008 [§0-CONFIRMED]
          This session's code shows BASE_RATE = 0.006 [CODE: config.js:42]
          → SURFACE IMMEDIATELY. Do not resolve silently.
          → Ask user: "Did the rate change? Which is correct?"
```

### Carrying Findings Forward

At session end, write to H5W-QUEUE.md:
- All unfixed findings with current priority
- All blocked (T3) findings with context
- Recommended next scope for next session

Write to H5W-ASSUMPTIONS.md:
- All active assumptions with confidence scores
- Note which assumptions were confirmed/denied this session

---

## §AUTO — DEEP AUTONOMOUS AGENT PROTOCOL

> **This section transforms H5W from a skill into an agent.** When the user
> says "run autonomously", "I'll be back", "you decide everything", "handle it",
> or sets a time horizon ("improve for the next 2 hours"), this protocol governs
> ALL behavior until the user returns.

### Activation

User says anything indicating unattended operation. Claude:
1. Confirms the scope and autonomy level (once, at start).
2. Sets the session parameters.
3. Begins work. Does NOT stop until §AUTO termination triggers.

```
AUTONOMOUS SESSION START
─────────────────────────
Scope:           [from user or self-selected per §I.3]
Autonomy Level:  [FULL — default if user says "you decide"]
Session Budget:  [time horizon if stated, else "until done or blocked"]
Working Branch:  [git branch name if applicable]
Report Target:   [H5W-REPORT.md — written at end]
─────────────────────────
Proceeding autonomously. Will report when done.
```

### Autonomy Levels

| Level | What Claude Decides | What Waits for User |
|-------|--------------------|--------------------|
| **FULL** (default) | Everything T0–T2. Module routing. Scope expansion. Build decisions. | T3 only (queued, not blocking) |
| **GUIDED** | T0–T1. Module routing within stated scope. | T2+, scope changes, build architecture |
| **SUPERVISED** | T0 only. | Everything else logged as recommendations |

**Default is FULL.** User can specify otherwise: "autonomous but don't touch
the API layer" → FULL except T3 on API-touching changes.

### The Five Rules of Autonomous Operation

**Rule 1 — Never Stop, Always Log**
Checkpoints (§SIM.5) switch from STOP-AND-ASK to LOG-AND-CONTINUE.
Instead of printing "Continue? [yes/no]" and waiting, Claude:
- Writes the checkpoint report to H5W-LOG.md
- Continues to the next cycle
- Stopping only on termination triggers (see below)

**Rule 2 — T3 Items Queue, Don't Block**
When a T3 decision is encountered:
- Log it in H5W-QUEUE.md with tag `[T3-BLOCKED]`
- Log the full context: what decision is needed, what the options are, Claude's recommendation
- SKIP to the next non-blocked finding
- Continue working on everything that ISN'T blocked by T3
- T3 items are presented in the final report for user decision

**Rule 3 — No Questions, Best Judgment**
In autonomous mode, Claude NEVER uses `AskUserQuestion`. Instead:
- Make the best decision based on available evidence
- Log the decision and rationale in H5W-LOG.md
- Tag: `[AUTO-DECIDED: chose X because Y. Override if wrong.]`
- If confidence < 3/5 → also log in H5W-ASSUMPTIONS.md

**Rule 4 — Self-Correct Before Moving On**
After every fix, the verification protocol (§VER) runs. If verification FAILS:
```
SELF-CORRECTION PROTOCOL:
  Attempt 1: Revert the fix. Re-read the code. Re-plan from scratch.
  Attempt 2: Try alternative approach (different fix strategy).
  Attempt 3: If still failing → log as [STUCK], add to T3 queue,
             move to next finding. Do NOT keep retrying.
```
Maximum 3 attempts per finding. After 3 failures → skip and log.

**Rule 5 — Manage Context Proactively**
Long autonomous sessions consume context. Claude manages this:
- After every 5 fixes: check if context is growing heavy
- If approaching context limits: run `/compact` (Claude Code) or
  summarize findings-so-far into H5W-LOG.md and continue from the log
- H5W-LOG.md, H5W-QUEUE.md, and H5W-ASSUMPTIONS.md serve as
  **external memory** — write everything there, read back when needed
- When loading a module reference file: read only the sections relevant
  to current findings, not the entire file

### Autonomous Checkpoint (replaces §SIM.5 in auto mode)

Instead of stopping, Claude writes to H5W-LOG.md:

```
──── AUTO CHECKPOINT [N] ────
Time: [timestamp]
Cycle: [N] | Files changed: [N] | Findings fixed: [N]
Queue: [remaining] | Blocked: [T3 count]
Context health: [ok / compacting / heavy]
Decision: continuing to cycle [N+1]
────────────────────────────
```

### Autonomous Module Routing

In autonomous mode, Claude doesn't ask which module — it routes based on
the finding pattern:

| Pattern Detected | Action |
|-----------------|--------|
| 3+ code quality findings | Load MOD-CODE, run relevant dimensions |
| 3+ visual findings in same area | Load MOD-DESG, run targeted analysis |
| Structural anti-patterns found | Load MOD-REST, run diagnosis |
| 3+ instances of same pattern | Load MOD-SCOP, run concept scaffold |
| All modules return clean | Expand scope to adjacent areas |

No confirmation needed. Log the routing decision in H5W-LOG.md.

### Autonomous Build (§BUILD in auto mode)

Build gates that normally require user approval become:

| Gate | Normal Mode | Autonomous Mode |
|------|------------|-----------------|
| B1 Discovery brief | User approves | Claude writes best brief, logs `[AUTO-DECIDED]`, proceeds |
| B2 Architecture | User approves | Claude selects per decision trees, logs all decisions |
| B3 Design system | User approves | Claude runs MOD-ART with best judgment, logs results |
| B5 Per-feature | User reviews | Gate auto-passes if all checks ✓, fails → self-correct |
| B9 Launch gate | User signs off | Claude runs all checks, presents results in report |

### Termination Triggers

Claude stops autonomous execution when ANY is true:

| Trigger | What Happens |
|---------|-------------|
| Queue is empty | All findings fixed. Write final report. |
| All remaining items are T3-blocked | Nothing left Claude can do alone. Write report. |
| Context window approaching limit | Compact, write progress report, indicate "resume needed" |
| Self-correction failures > 5 total | System is hitting issues it can't solve. Write report. |
| Error in code execution | Build/compile/lint error. Revert last change. Write report. |

### Build Failure Recovery (§AUTO + §BUILD)

When `npm run build`, `./gradlew build`, or equivalent fails during autonomous operation:
1. **Read the error.** Extract the file, line, and message.
2. **Revert the last change** that caused the failure.
3. **Diagnose:** Is this a fixable error (typo, missing import) or structural?
   - Fixable → fix it, re-run build, continue if green.
   - Structural → log as `[STUCK]`, add to T3 queue, move on.
4. **If build was already broken before the session:** Log this at session start.
   Do NOT count pre-existing build failures against the self-correction budget.

### Git Branch Strategy (§AUTO)

Autonomous sessions work on a dedicated branch to keep main clean:
```
git checkout -b h5w/auto-[date]-[scope]
# All autonomous work happens here
# User reviews branch, merges to main when satisfied
```
If git is not available (no repo), work directly — but log every file changed
in H5W-REPORT.md so the user can review diffs.
| User returns | Switch back to interactive mode. Present report. |

### The Autonomous Report (H5W-REPORT.md)

Written at session end. This is what the user reads when they return.

```markdown
# H5W Autonomous Session Report

## Session Parameters
- Started: [timestamp]
- Scope: [what was worked on]
- Autonomy: [FULL/GUIDED/SUPERVISED]
- Termination: [why it stopped]

## Executive Summary
[3-5 sentences: what was accomplished, what remains, what needs your decision]

## What Was Done
[chronological list of all fixes applied, grouped by area]

### Fixes Applied ([count])
| ID | Sev | File | Summary | Verified |
|----|-----|------|---------|----------|
| F-001 | high | TeamCard.jsx:84 | Added empty state | ✓ |
| ...

### Modules Invoked ([count])
| Module | Why | Findings Produced |
|--------|-----|-------------------|
| MOD-CODE | 4 code quality findings | F-020 through F-025 |
| ...

## What Needs Your Decision ([count] T3 items)
| ID | Decision Needed | Claude's Recommendation | Why T3 |
|----|----------------|------------------------|--------|
| F-005 | Delete legacy export? | Keep it (users may depend) | Irreversible |
| ...

## Autonomous Decisions Made ([count])
[Every [AUTO-DECIDED] item — review these for correctness]
| Decision | Rationale | Confidence | Override? |
|----------|-----------|------------|-----------|
| Stack: Next.js | SEO needed, React ecosystem | 4/5 | Change if wrong |
| ...

## What Remains
- Queue: [N] findings remaining
- Assumptions: [N] active (review H5W-ASSUMPTIONS.md)
- Recommended next: [what to focus on]

## Files Changed
[git diff --stat or equivalent]

## How to Review
1. Read "What Needs Your Decision" — resolve T3 items
2. Scan "Autonomous Decisions" — override any you disagree with
3. Run the app — verify nothing feels wrong
4. Say "continue" to resume, or "revert [F-NNN]" to undo specific fixes
```

### Context Window Strategy

For sessions exceeding single-context capacity:

```
CONTEXT BUDGET PROTOCOL:
  1. Chief Guide SKILL.md is always in context (~2,300 lines)
  2. Module reference files loaded ONLY when needed, ONLY relevant sections
  3. After reading a module section → extract the relevant rules → unload file
  4. H5W-LOG.md is the persistent brain — write EVERYTHING to it
  5. When context feels heavy:
     a. Summarize current state to H5W-LOG.md
     b. Platform-specific compaction:
        - Claude Code CLI: run `/compact`
        - Claude Code web/claude.ai: summarize current state into
          H5W-LOG.md, discard intermediate reasoning, re-read LOG
          to restore working context
     c. Re-read H5W-LOG.md to restore awareness
     d. Continue from where you left off
  6. Module files > 2,000 lines: read by section anchor (grep for §), not whole file
```

### Integration with Existing Protocols

| Protocol | Interactive Mode | Autonomous Mode |
|----------|-----------------|-----------------|
| §SIM.5 Checkpoints | Stop, report, wait | Log, continue |
| §REV T3 decisions | Stop, ask | Queue, skip, report at end |
| §BUILD gates | User approves | Auto-pass with logging |
| §WORKFLOW handoffs | May ask which module | Auto-route by pattern |
| §VER failures | Report to user | Self-correct (3 attempts) |
| §SESSION continuity | Resume on user command | Resume automatically |
| `AskUserQuestion` | Used freely | **NEVER used** |

## §DOC. WORKING DOCUMENTS

Create on system activation. Append-only — never overwrite previous entries.

### H5W-LOG.md — Audit Trail

```markdown
# H5W Unified Log — [app name]

## Session: [date] — Mode: [mode] — Scope: [scope]

### Phase 0: Understand
- §0 filled: [timestamp]
- Domain: [class] | Architecture: [class] | LOC: [est] | Scope: [size]
- Aesthetic profile: A1:[val] A2:[val] A3:[val] A4:[val] A5:[val]
- Modules planned: [list]

### Phase 1: Discover
- Personas: P1(first-time), P2(power), P3(hostile), P4([domain])
- States mapped: [count] screens × [count] states = [count] transitions
- Investigation targets: [count] unknown, [count] edge risk
- Walkthrough: P1 → /home, /teams | P2 → /teams, /compare | ...
- Findings: F-001 through F-[N]

### Phase 2: Analyze
- Module handoffs: MOD-CODE(F-014), MOD-DESG(F-013,F-016,F-017)
- Module findings: F-020 through F-[N]

### Phase 4: Execute
- [ts] F-001 FIXED T1 [TeamCard.jsx:84] — added empty state — Verified: yes
- [ts] F-002 FIXED T1 [Teams.css:120] — fixed breakpoint — Verified: yes
- [ts] F-003 BLOCKED T3 — delete workflow changes user data contract

### Phase 5: Verify (Expansion Cycle 1)
- Micro-H5W F-001 → F-018, F-019 (same pattern in 2 other files)
- Micro-H5W F-002 → no new findings
- F-018 FIXED T1 — F-019 FIXED T1
- Micro-H5W F-018 → no new findings
- Micro-H5W F-019 → no new findings
- Checkpoint 1: [paste report]

### Handoff Log
- [ts] → MOD-DESG: F-013,F-016,F-017 (3 visual findings in Teams tab)
- [ts] ← MOD-DESG: F-020(color fix), F-021(spacing), F-022(typography)
- [ts] → MOD-SCOP: F-019 pattern (5 instances of missing empty-state)
- [ts] ← MOD-SCOP: F-023 through F-027 (systematic empty-state fixes)
```

### H5W-QUEUE.md — Priority Queue

```markdown
# H5W Finding Queue — [app name]
# Sorted by: severity → cascade → tier → persona overlap → compounds

| # | ID | Sev | Tier | Mod | Conf | Source | Summary |
|---|------|-----|------|-----|------|--------|---------|
| 1 | F-003 | crit | T3 | H5W | confirmed | walkthrough P1 | Delete workflow - T3 blocked |
| 2 | F-020 | high | T1 | DESG | confirmed | MOD-DESG handoff | Card header contrast |
| 3 | F-014 | high | T2 | CODE | high | walkthrough P3 | Race in team deletion |
| 4 | F-023 | med | T1 | SCOP | confirmed | MOD-SCOP | Empty-state: CharList |
| 5 | F-024 | med | T1 | SCOP | confirmed | MOD-SCOP | Empty-state: Compare |
```

### H5W-ASSUMPTIONS.md — Unconfirmed Beliefs

```markdown
# H5W Assumptions — [app name]
# Active assumptions. Each fix that depends on one references it.

| # | Assumption | Conf | Impact if Wrong | Depends On | Source |
|---|-----------|------|-----------------|------------|--------|
| 1 | Empty team.members renders empty-state | 3/5 | F-001 fix wrong | F-001 | [INFERRED: component structure] |
| 2 | Network timeout → Error (not null) | 4/5 | F-014 misses null | F-014 | [CODE: api.js:42 try/catch] |
| 3 | localStorage quota > 5MB available | 2/5 | Persistence fails silently | F-030 | [INFERRED: browser default] |
```

---

## §TOOL. CLAUDE CODE INTEGRATION

| Task | Tool | When |
|------|------|------|
| Read codebase | `Agent` (Explore) | Start of any phase — parallel, no context bloat |
| Search patterns | `Grep` / `Glob` | §I.5 extraction, §SIM.4 pattern grep, MOD-SCOP |
| Track progress | `TodoWrite` | Multi-phase work — create at start, update per phase |
| Ask user | `AskUserQuestion` | §TRIAGE routing, T3 decisions, §0 gaps, scope confirmation |
| Research | `WebSearch` / `WebFetch` | §SRC source verification, MOD-APP §X competitive research |
| Edit files | `Edit` | All fixes — surgical edits |
| Create files | `Write` | Scaffolding, new components, working documents |
| Move/rename | `Bash` (mv, cp) | MOD-REST restructuring operations |
| Verify build | `Bash` (build commands) | §VER after multi-file changes |
| Run tests | `Bash` (test commands) | §VER regression check |
| Git | `Bash` (git add, commit) | Atomic commits — one per fix or operation |
| Map structure | `Bash` (find, tree) | §SIM.2 state mapping, MOD-REST §R2 |

### Parallel Strategy (> 2K LOC)
```
Agent(Explore, "Read all UI/component files — list exports, imports, responsibility per file")
Agent(Explore, "Read all util/service/hook files — list what each exports and who imports")
Agent(Explore, "Read all route/navigation files — list structure and data deps per route")
Agent(Explore, "Read all style/theme/token files — list colors, spacing, typography values")
```

### Pre-Flight Extraction Patterns

**Web / React / Next.js:**
```
Grep("useEffect|useState|useContext|useReducer", glob: "*.{tsx,jsx,ts,js}")
Grep("--[a-z]", glob: "*.css")  # CSS variables
Read: package.json, router/pages/app config, tailwind.config.*
```

**Android / Kotlin:**
```
Grep("class.*Fragment|class.*Activity|class.*ViewModel", type: "kotlin")
Grep("(val|const|var)\\s+[A-Z_]{2,}\\s*=", type: "kotlin")  # Constants
Grep("SharedPreferences|Room|DataStore", type: "kotlin")
Read: AndroidManifest.xml, build.gradle*, nav_graph.xml, res/values/*
```

**iOS / Swift:**
```
Grep("@State|@StateObject|@ObservedObject|@Published", glob: "*.swift")
Read: Info.plist, Package.swift or Podfile, navigation structure
```

---

## §PLAT. PLATFORM AWARENESS

| Concept | Web / React | Android / Kotlin | iOS / Swift |
|---------|------------|-------------------|-------------|
| Route/Screen | Route, page component | Fragment, Activity | ViewController, View |
| State store | useState, Zustand, Redux | ViewModel, StateFlow | @State, @StateObject |
| Persistence | localStorage, IndexedDB | SharedPreferences, Room | UserDefaults, CoreData |
| Error boundary | ErrorBoundary component | try/catch + CoroutineExceptionHandler | do/catch + Result |
| Loading state | Suspense, loading boolean | sealed class UiState | enum LoadingState |
| Navigation | react-router, Next.js links | Navigation Component, nav_graph | NavigationStack |
| Lifecycle | useEffect mount/cleanup | onResume/onPause/onDestroy | onAppear/onDisappear |
| Empty state | Conditional on array.length | RecyclerView empty view | ContentUnavailableView |
| Touch target | min 44px | min 48dp | min 44pt |
| Animation | CSS transition, framer-motion | MotionLayout, ObjectAnimator | UIView.animate, withAnimation |
| Design tokens | CSS vars, Tailwind config | colors.xml, themes.xml, dimens.xml | Asset catalog, extensions |
| Config files | package.json | build.gradle, AndroidManifest.xml | Info.plist, Podfile |

**Platform-specific simulation checks:**

Android:
- Fragment lifecycle → state loss on config change / process death
- Coroutine scope cancellation in ViewModel
- ProGuard rules for reflection-dependent code
- Low-memory kill → onSaveInstanceState coverage
- 48dp touch targets (Material spec)

iOS:
- @State vs @StateObject scoping errors
- NavigationStack depth management
- Retain cycles in closures
- Background → foreground state restoration
- 44pt touch targets (Apple HIG)

Web:
- Hydration mismatches (SSR)
- localStorage quota (5MB typical)
- Service worker cache invalidation
- CSS specificity at scale
- 44px touch targets (WCAG)

---

## §MODE. EXECUTION MODES

| Mode | Trigger | Phases | Modules | Checkpoint |
|------|---------|--------|---------|------------|
| **Full Simulation** | "run H5W" | 0→1→2→3→4→5→6 | As needed | 3 cycles / 5 files |
| **Targeted Simulation** | "H5W on [X]" | 0→1→4→5 scoped | As needed | 3 / 5 |
| **Full Audit** | "full app audit" | 0→2→3 | MOD-APP all parts | Per part |
| **Full Deep Audit** | "full deep audit" | 0→2→3 | All modules | Per part |
| **Module Audit** | "code review" etc. | 0→2 | Single module | End of module |
| **Art Direction** | "design my app" | 0→BUILD.B3 | MOD-ART | Per component |
| **Restructure** | "restructure" | 0→2→4 | MOD-REST | Per operation |
| **Build** | "build [desc]" | 0→BUILD | ART+CODE+APP | Per feature |
| **Single-Lens** | "run WILL lens" | 0→1 (one lens) | None | End (findings only) |
| **Expansion** | "expand" | 5 only | As needed | 3 / 5 |
| **Continuous** | "keep improving" | 0→6 loop | As needed | 5 / 10 |
| **Autonomous Full** | "handle it, I'll be back" | §AUTO + 0→6 loop | As needed | Log-only (no stops) |
| **Autonomous Build** | "build this, I'll be back" | §AUTO + §BUILD | ART+CODE+APP | Log-only |

**Single-Lens mode:** Produces findings only — no auto-fix. User decides
which to execute. Useful for "show me what WILL break" without changing anything.

**Continuous mode:** Checkpoint boundaries expand. System loops until T3, empty
queue, or user interrupt. Each cycle: rescan for new issues, expand from fixes.

---

## §ANTI. ANTI-PATTERNS — 13 Things Claude Must Never Do

| # | Pattern | What It Is | Why It's Harmful |
|---|---------|-----------|-----------------|
| 1 | **Phantom Finding** | Issue without `[CODE: file:line]` | Wastes time on non-issues |
| 2 | **Fix-and-Forget** | Fix without micro-H5W | Misses cascading regressions |
| 3 | **Scope Creep** | Past checkpoint boundary | Large uncommitted diffs, trust erosion |
| 4 | **Persona Theater** | Narrative prose instead of functional spec | Wasted tokens, no analytical value |
| 5 | **Lens Skip** | Silently omit a lens | Missing context for the finding |
| 6 | **Module Dump** | Hand off to module to avoid doing the work | Fragments findings, slows cycle |
| 7 | **Silent Assumption** | Fix based on unlogged inference | Invisible failure mode |
| 8 | **Memory Assertion** | Training data used as finding basis | Fabricated facts → false findings |
| 9 | **Rewrite Creep** | Finding used to justify rewriting working code | Violates Feature Preservation (Law 6) |
| 10 | **Identity Erasure** | Generic conventions replace intentional design | Violates Identity Preservation (Law 7) |
| 11 | **Scope Blindness** | Missing instances of "all X" | Incomplete fix (→ MOD-SCOP) |
| 12 | **Restructure Mixing** | Structure change + feature in same commit | Unmergeable, unreviewable |
| 13 | **Source Laundering** | Training recall without `[UNVERIFIED]` tag | Fabrication disguised as knowledge |

---

## §XCUT. CROSS-CUTTING CONCERNS

Patterns spanning multiple modules and lenses. When a finding touches 3+ modules,
create a cross-cutting chain in H5W-LOG.md.

| Pattern | Modules | Lenses | Example |
|---------|---------|--------|---------|
| Empty→populated transition | DESG + CODE + APP | H,P,N,L | First item added — empty state animates out? |
| Error recovery chain | CODE + APP + DESG | H,W,P,N | Network drop mid-submit — data preserved? |
| State persistence boundary | CODE + APP + REST | T,N,L | App killed — what state survives? |
| Viewport transition | DESG + CODE + APP | W,H,N,L | Device rotation — layout adapts? Scroll kept? |
| Progressive disclosure | DESG + APP + SCOP | W,H,N | Expert settings discoverable without overwhelming? |
| Cross-component data | CODE + APP + REST | H,T,L | Shared state updated — all consumers re-render? |
| Auth + permissions | APP + CODE | W,P,N | Permission denied — graceful fallback? |
| Build + perf chain | CODE + APP + REST | H,T,L | Bundle size → load time → user patience |

---

## §DLVR. DELIVERABLES

| Mode | Required Outputs |
|------|-----------------|
| **Full Simulation** | §0, personas, state map, all findings (§FMT), H5W-LOG, H5W-QUEUE, checkpoint reports, session summary |
| **Full Audit** | §0, per-part findings, summary dashboard, quick wins list, prioritized roadmap |
| **Full Deep Audit** | All of Full Audit + all module findings, cross-cutting chains |
| **Module Audit** | §0 lightweight, module findings, module summary |
| **Art Direction** | §0, source research doc, design system (tokens, components, type, color, motion) |
| **Restructure** | §0, archaeology report, architecture map, migration plan (approved), per-op logs, verification |
| **Build** | §0, architecture decisions, design system, codebase, per-feature gate results, launch gate |
| **Single-Lens** | Findings list (unfixed), lens-specific analysis, recommendations |
| **Continuous** | All simulation deliverables per cycle, cumulative session summary |
| **Autonomous** | H5W-REPORT.md (comprehensive), H5W-LOG.md, H5W-QUEUE.md (remaining), all T3 items with recommendations |

### Session Summary (at end or on request)

```
══════════════════════════════════════════
H5W SESSION SUMMARY
══════════════════════════════════════════
APP:       [name] v[version]
MODE:      [mode]
SCOPE:     [scope]
CYCLES:    [completed]
DURATION:  [phases completed]

─── FINDINGS ─────────────────────────────
Total: [n]
  Critical: [n] | High: [n] | Medium: [n] | Low: [n] | Enhancement: [n]
Compounds (⏱): [n chains]

─── EXECUTION ────────────────────────────
Fixed:   [n] (T0: [n], T1: [n], T2: [n])
Blocked: [n] (T3 — awaiting user)
Queued:  [n] (remaining for next session)

─── MODULES ──────────────────────────────
Used: [list with finding counts per module]
Handoffs: [n] (logged in H5W-LOG)

─── ASSUMPTIONS ──────────────────────────
Active: [n]
Highest-impact: [top 3]
Confirmed this session: [n]
Denied this session: [n]

─── PATTERNS ─────────────────────────────
  1. [most common finding pattern + count]
  2. [second + count]
  3. [third + count]

─── NEXT SESSION ─────────────────────────
Recommended focus: [what to work on next]
Blocked items: [T3 decisions needed]
══════════════════════════════════════════
```

---

## §MANDATE. FINAL MANDATE — BINDING SYSTEM CONTRACT

This system is a **binding protocol**. When activated:

1. §TRIAGE routes every request. No module is invoked directly — H5W routes.
2. §0 is filled once. Modules inherit — never re-fill.
3. §LAW governs every action in every module. No override.
4. §SRC governs every domain fact. No untagged assertions.
5. §FMT governs every finding. No shortcut formats.
6. §REV classifies every action before execution. No skipping.
7. §VER confirms every fix. No "probably fine."
8. §SIM.4 runs after every fix. No fix-and-forget.
9. §SIM.5 stops at every checkpoint trigger. No scope creep.
10. §DOC logs everything. No silent changes.
11. §WORKFLOW governs every module handoff. No ad-hoc delegation.
12. §SESSION preserves continuity. No lost findings between sessions.
13. §AUTO governs unattended operation. No stopping for questions.
    Log and continue. Self-correct. Report at end.

**Module loading:** Reference files loaded on demand. When §TRIAGE or
§WORKFLOW routes to a module, Claude reads that reference file. Modules
inherit ALL shared protocols from this Chief Guide — they do NOT re-derive
§0, §LAW, §SRC, §FMT, §REV, §VER, or any other shared section.

**The trust contract:** The user trusts this system to discover what they
haven't seen, fix what needs fixing, build what needs building, and maintain
what needs maintaining. That trust is maintained by:
- Rigorous process (every finding grounded, every fix verified)
- Honest uncertainty (theoretical findings declared, assumptions logged)
- Controlled expansion (checkpoints respected, user decides scope)
- Minimum footprint (smallest safe change, always)
- Feature preservation (nothing working is broken)
- Identity preservation (nothing intentional is erased)

---

## APPENDIX A — MODULE REFERENCE MAP

When §TRIAGE or §WORKFLOW routes to a module, Claude reads the corresponding file.
Each module contains ONLY domain-specific content — shared protocols live here.

| Module | File | Content Summary |
|--------|------|-----------------|
| MOD-APP | `references/mod-app-audit.md` | Categories A–O: domain logic, state, security, performance, visual design, UX, accessibility, compatibility, code quality, AI/LLM, i18n, projections. R&D mode. Polish mode. |
| MOD-CODE | `references/mod-code-audit.md` | 8 dimensions: format/conventions, health/hygiene, optimization, structure/architecture, logic/correctness, state/data, error handling, async/concurrency. JS/React + Kotlin/Android stack modules. |
| MOD-DESG | `references/mod-design-audit.md` | 21-step path: style classification, color science, typography, motion architecture, visual hierarchy, surface/atmosphere, iconography, component character, copy alignment, brand identity, competitive positioning, source research. |
| MOD-ART | `references/mod-art-direction.md` | Art direction engine: source research, anti-slop enforcement, visual craft (color, depth, texture, light, shape, composition), typography system, component design, interaction design, brand identity, psychology, audience analysis, platform tokens. |
| MOD-SCOP | `references/mod-scope-context.md` | Two protocols: large-scope awareness (concept scaffold, pattern inventory, exhaustive scan, human verification gates) and ambiguity resolution (referential, spatial, implicit value disambiguation). Combined workflow. |
| MOD-REST | `references/mod-restructuring.md` | 12-phase pipeline: archaeology, architecture mapping, structural diagnosis, debt triage, target design, migration plan, foundation, extract/relocate, rewire, decouple, consolidate, cleanup. 8 targeted operations (T1–T8). Verification gates. |
