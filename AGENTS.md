# Component agent instructions

- English source/docs/commits. Follow the central workspace specification and ADRs when present.
- This is an independent repository. Commit only this component; never commit `.deps`, sibling repositories, SDKs, secrets or build outputs.
- `dependencies.lock.json` pins shared repositories. `make deps-check` verifies reproducible pins; path resolution permits explicit local development. Never duplicate shared business logic or transport code.
- Run `make format` and `make format-check`, then the checks listed in README. Use CodeGraph first when `.codegraph` exists; do not install Graphify or edit upstream clones.
- `monorepo/*` tags preserve filtered historical checkpoints; they are not independent release or milestone evidence. Component completion requires its own checks plus the global product gates.

- Android uses feature-first MVVM, manual constructor injection and semantic repository interfaces. Never import Android/JSON/data implementations into ViewModels. Preserve application IDs and isolated API-specific factory loading.

- Accepted Cast design: workspace `resources/cast-client-ui-concepto.jpeg` and `docs/design/cast-ui.md` (ADR0029). Keep discovery, trusted pairing, receiver readiness and projection consent distinct. Dedicated 21:9 layouts are outside V1; safe phone insets and scrolling remain required.
- Public capture baseline remains API21 video/API29 optional audio. Root/OEM/shell backends are planned opt-in capabilities, not a reason to claim pre-21 installation or universal internal-audio capture.
