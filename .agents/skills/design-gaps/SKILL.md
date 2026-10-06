---
name: design-gaps
description: Close one design-gap issue in design/open-line.pen with the pen CLI and the pen-dev skill, comment the screens and components changed on the issue, then stop for review. Use when working an issue labeled design-gap or editing the .pen file.
---

# Design gaps

Close one gap on the canvas. Record where to review. Stop.

## Files

- Work item: one GitHub issue labeled `design-gap` (`gh issue view <n>`). Its body holds Built as and Design needs to decide.
- Canvas: `design/open-line.pen`. Screen map and tokens: `DESIGN.md`.

The `.pen` file is encrypted. Read and edit it only through pen.dev. A Read or Grep of `design/open-line.pen` is not a design.

## Canvas

`pen` and `pencil` are the same CLI. Design in interactive mode. A one-shot `pen --prompt` starts a second agent and is the wrong tool for this workflow.

Prefer the pencil MCP when it is connected. Pass `filePath` `design/open-line.pen` on `execute`. Otherwise connect to the running app:

```
pen interactive --app desktop --in design/open-line.pen
```

With no app open, headless mode writes the same file:

```
pen interactive --in design/open-line.pen --out design/open-line.pen
```

Before any insert, load the pen-dev skill and read the document:

1. `read_skill()`
2. `read_skill({ path: "pen-schema.md" })`
3. `read_skill({ path: "execute.md" })`
4. `read_skill({ path: "guide/components.md" })`
5. `get_app_state()`
6. `execute` with `Get` for the components and the screens this gap touches

`save()` before leaving the shell. In the MCP, the execute call is the edit.

pen.dev style archetypes and `guide/mobile-app.md` do not apply here. This file already has its visual system. Follow `DESIGN.md` and the nodes `Get` returns.

## Design the gap

Draw only from nodes, variables, and components `Get` returned. A color, type style, component, or screen that call did not return is not in the design. Reuse `$paper`, `$pine`, `$ink`, `$lime`, `$lilac`, and `$coral`.

New UI is a reusable component (`reusable: true`) on the component board, then an instance (`ref`) on the screen. Name every node. The document root holds screen frames, component boards, and spec notes.

Phone screens stay 390×844 with `clip: true`. One job per screen. Touch targets stay large. Status uses words, shape, or line weight together with color, so meaning survives without hue.

Persian is part of the design, not a later pass. A new screen or component gets an RTL variant: Vazirmatn, mirrored layout, Persian copy, and Persian digits where the file already uses them. Read the existing Persian screens (inbox 57, conversation 37) before drawing a new RTL pattern.

When the issue's **Design needs to decide** conflicts with a node already on the canvas, comment the conflict on the issue and stop.

## One gap

1. Take the issue the user names, or the oldest open `design-gap` issue labelled `ready-for-agent` with no open PR. Follow the workflow in `AGENTS.md`: branch `<issue>-<slug>`, draft PR with `Closes #<n>`.
   Done when that issue is the only gap this run touches.

2. Design it on the canvas, using the rules above.
   Done when the new or updated component is instanced on the screen, the Persian RTL variant exists, and no other gap was drawn.

3. Comment on the issue what a reviewer opens, canvas first, then implement it in the app on the same branch. Before editing Kotlin UI, read `compose-multiplatform-patterns` and `kotlin-patterns`.

```
Screens: 01 everyday inbox; ui/inbox/InboxScreen.kt
Components: added pinned header; changed InboxRow
```

Name a frame, file, or component only when this gap edited it. Omit a mark that has nothing under it.

## Stop

The run ends when the app matches the design and the PR follows the workflow in `AGENTS.md`, or when a canvas conflict stops the run. Report the issue number, the comment and the PR.
