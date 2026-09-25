# Design review — `Messages-Signal.pen`

Audit date: 2026-09-24
Scope: all 71 top-level frames (54 English screens, 11 Persian screens, 3 spec sheets), 51 components, 1,367 nodes.
Method: structural traversal, WCAG contrast sweep (1,028 text nodes), design-token diff against `GetVariables()`, RTL/i18n pass, and verification against the design's own accessibility contract (`00c · Accessibility and Persian / implementation contract`).

**No changes were made to the file.** Evidence PNGs (3×) are in `/tmp/opencode/audit/`.

Severity: 🔴 critical (visible breakage) · 🟠 high (contract violation) · 🟡 medium (systemic drift) · 🔵 low.

---

## 🔴 Critical

### 1. Screen 67 · Conversation / blocked — entire header is invisible
- Screen: `W0UUs`, header instance `oc3ti`.
- The instance has a fill override `#FFFFFF`, but children kept their light-on-pine colors:
  - `oc3ti/s8TQYP` Contact name "Mina Farahani" — `#FFFFFF` on `#FFFFFF` → **1.00:1**
  - `oc3ti/w2EX0d/Z0KONl` status bar "9:41" + signal/wifi/battery — **1.00:1**
  - back / call / ⋯ icons — white on white
  - `oc3ti/E2D2e` Contact subtitle "mobile · SIM 1" — `#CFE1D9` on white → **1.36:1**
- Evidence: `/tmp/opencode/audit/W0UUs.png` — white band, no title, no back arrow.
- Fix: restore header fill to `$primary`, or recolor children to `on-surface` if a white header was intended.

### 2. Screen 60 · فارسی / نمایش و زبان — active nav label fails AA
- Node: `G9fBLI/zeAY0` "Settings label" inside `Nav / Settings` (`G9fBLI/VyYKR`).
- Active lilac pill `#D5D9F5` + label `#53665E` → **4.39:1** (needs 4.5).
- Every other active instance (LTR settings screens 24–29) switches the label to pine `#194C43`.
- Fix: override the active label fill to `$primary` / `#194C43` on this instance.

---

## 🟠 High — violates the 00c contract ("48 × 48 dp minimum touch targets")

| Control | Node | Size | Screen |
|---|---|---|---|
| Action / Copy | `zp42h` | **75×44** | `F5fuED` 10 · selected message |
| Action / Forward | `aYKED` | **75×44** | `F5fuED` |
| Action / Star | `H4tTLS` | **75×44** | `F5fuED` |
| Action / Delete | `d2o7qi` | **75×44** | `F5fuED` |
| Date / Thu 24 | `t5ejFf` | **74×41** | `CGlRY` 12 · schedule |
| Date / Fri 25 | `a5qWG` | **74×41** | `CGlRY` |
| Date / Sat 26 | `X3vICo` | **74×41** | `CGlRY` |
| Date / Sun 27 | `RyjmQ` | **74×41** | `CGlRY` |
| Filter / All | instance of `kpec4` | **43×48** (width) | filter rows — all other chips ≥50 wide |

### 3. Tab-level empty states lost their chrome
- `mYL3W` 34 · Inbox / empty and `IQHMi` 61 · فارسی / بدون پیام:
  - **no bottom navigation** (no `plIFb` / `Vmtuu` ref),
  - use a *Page app bar* instead of the Inbox identity band (title + search + filters) used by `P0QxLz` (01) / `PJcuS` (02) / `U7PxRJ` (57).
- Same tab, completely different shell.

### 4. Unread dot sits outside its own container
- `X2AFmd` "Unread signal" (10×10) lies entirely below its parent `bCJq0` "Conversation metadata" (70×17) — 17px overflow.
- 8 affected instances: screens `P0QxLz` (01), `bt2bb` (19 ×3), `zndEE` (23 ×2), `U7PxRJ` (57).
- Renders today only because no ancestor sets `clip:true`. One `clip` toggle and every unread badge disappears.
- Fix: let `Conversation metadata` be `fit_content` (or reserve space for the dot).

---

## 🟡 Medium — design-system drift

### 5. Spacing scale is effectively unused
- **195 of 219 gap nodes (89%) are off-scale**, while `$space-1…8` (4/8/12/16/20/24/32) exist:
  `14`×56 · `5`×28 · `3`×22 · `13`×22 · `7`×19 · `6`×18 · `10`×9 · `9`×5 · `11`×5 · `2`×3 · `17`×2 · `18`×3 · `22/26/38`×3
- **156 off-scale paddings**, e.g. `[13,$space-5,22,$space-5]`, `[14,15]`, `[18,18,22,18]`, `[17,18]`, `[10,9]`, `[13,15]`.
- Note: control rows keep ≥12px, so the 8dp *contract* rule holds — this is token-discipline, not a11y.

### 6. Inconsistent artboard heights
- 56 screens @ 900 · 10 Persian screens @ **1000** · `c7nFBU` 26 · Settings/writing @ **1040** (content ends 960, nav 960–1040) · `vvAER` 65 @ **320×1120** · 3 spec frames fit-content.

### 7. Four component-width conventions
- 690: undo snackbar, attention notice, empty state, draft preserved, sending, blocked, loading result
- 390: bars, nav, composer, headers
- 350: most content components
- **352**: `nJGW0` conversation row (LTR) vs **350**: `I40rDg` row · RTL

### 8. Latent overflow in `draft preserved` / `sending`
- Master `ngU1j` (draft) and `p9YAP` (sending), rects `GuFQp` / `ySaz3`, width `fill_container(656)`.
- Instances `W81wj` (354px card) and `GsOrL` (280px card) resolve the rect to **656px** → 319px / 393px overflow.
- Currently harmless only because the instance sets `enabled:false`. Re-enable and a bar draws across and past the card.
- Same overrides also use raw `#D5D9F5` / `#D9F46A` instead of `$secondary-container` / `$accent`.

### 9. Token bypasses
- `hsbDV` "Conversation preview" (LTR row master) uses raw lineHeight **1.4** instead of `$leading-body` (1.45 LTR / **1.7 RTL**). The RTL master does it correctly.
- 9 raw font sizes (16/18/24) in the `aLjGj` 00c contract frame; 10 raw line-heights (1.4/1.5).
- 3 raw hex fills in the loading skeleton: `#DCE8E1`, `#EAF0EC`.

### 10. Dead tokens (never referenced anywhere)
`scrim`, `state-pressed`, `size-target`, `size-icon`, `text-brand`.
Notably `size-target:48` and `size-icon:24` sit unused while targets are hardcoded 41/43/44/46 and icons are 15/16/17/18/20/21/22/24/25/34/41/46/51.

---

## 🔵 Low

- **6 orphan components** (defined, never instanced): `nDfAi` attention notice, `uooxN` radio, `iiDXV` switch, `vCA9B` confirmation dialog, `Vg7ru` attachment bottom sheet, `J3ur8I` extended compose FAB. Confirmation flows (43 delete, 39 rename, 50 add phrase) are built as full pages instead — the dialog component is dead and the pattern is inconsistent.
- **13 components used exactly once**: undo snackbar, draft preserved, sending, blocked, text field, contact avatar, validation notice, deletion summary, decision option, media message, selected message, scheduled message, disabled composer · RTL.
- **Status bar master illegible in spec sheet**: `S5eca` sits directly on `$paper` in `ef2BY` (00b) with white text → **1.10:1** (`Z0KONl`). The component has no fill of its own; all real instances survive only because an ancestor band is pine. Give the master its own backdrop.
- **4 odd icon sizes**: 46×46 message symbol, 51×51 lock, 41×41 about, 34×34 empty (off the 24px scale).

---

## Verified clean (no issues found)

- 0 root-level junk · 0 leftover `placeholder` flags · 0 unnamed nodes · 0 fill-less (invisible) text · 0 overlapping top-level frames.
- Contrast: 1,028 text nodes checked → only the 5 failures listed above.
- All 16 nodes named `· touch target` are ≥48; all 87 icons are valid lucide, none empty.
- Radius scale 100% respected (0 off-scale values).
- Themes correctly assigned: `mode:dark` on 36/64, `dir:rtl` on all 11 Persian screens + 37.
- **Zero LTR/RTL component mixing**; mirroring correct (back arrow, menu order, composer +/send sides, incoming/outgoing bubble sides).
- **All RTL numbers/URLs/OTPs isolated** with FSI/LRI marks; 0 un-isolated numeric content in any RTL screen.
- Vazirmatn (`$fontBody` dir=rtl) and `$leading-body` 1.7 applied in RTL components.
- Screen numbering 00–68 complete, no gaps or duplicates.
- Message text 18sp / supporting 14–16sp per contract.

---

## Suggested fix order

1. Screen 67 header (`oc3ti`) — critical, one fill override.
2. Screen 60 nav label (`G9fBLI/zeAY0`) — one fill override.
3. The 9 sub-48dp touch targets (screens 10, 12, filter chips).
4. Empty-state nav + chrome (screens 34, 61).
5. Unread-dot container (`bCJq0`) so the badge survives `clip`.
6. Spacing/padding sweep onto `$space-*`; dead tokens; raw hex/line-height/font-size values.
7. Component housekeeping: 690→350 width convention, orphan components, status-bar master backdrop.
