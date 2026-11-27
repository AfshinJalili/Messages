// The demo inbox on the landing page. The chips filter the sample rows the way InboxFilter
// does, and once the phone is on screen a promotional text arrives and goes straight to spam:
// no notification, no inbox row, only the unread-spam count on Library grows.
(() => {
  const screen = document.querySelector("[data-demo]");
  if (!screen) return;
  const list = screen.querySelector("[data-rows]");
  const empty = screen.querySelector("[data-empty]");
  const badge = screen.querySelector("[data-spam-badge]");
  const chips = [...screen.querySelectorAll(".chip")];
  const persian = (n) => String(n).replace(/\d/g, (d) => "۰۱۲۳۴۵۶۷۸۹"[d]);

  function show(filter) {
    let label = null;
    let labelHasRows = false;
    let any = false;
    const closeSection = () => { if (label) label.hidden = !labelHasRows; };
    for (const item of list.children) {
      if (item.classList.contains("section-label")) {
        closeSection();
        label = item;
        labelHasRows = false;
        continue;
      }
      const visible = filter === "all" || item.dataset.cat.split(" ").includes(filter);
      item.hidden = !visible;
      labelHasRows ||= visible;
      any ||= visible;
    }
    closeSection();
    empty.hidden = any;
  }

  chips.forEach((chip) =>
    chip.addEventListener("click", () => {
      chips.forEach((c) => c.setAttribute("aria-pressed", String(c === chip)));
      show(chip.dataset.filter);
    }),
  );

  function arrive() {
    const card = document.createElement("div");
    card.className = "incoming";
    card.setAttribute("aria-hidden", "true");
    card.innerHTML = `
      <svg class="icon" aria-hidden="true"><use href="assets/icons.svg#i-gpp_bad"/></svg>
      <span><b>تبلیغ، بی‌اعلان به هرزنامه رفت</b>حراج بزرگ! ۷۰٪ تخفیف فقط تا امشب</span>`;
    screen.append(card);

    const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
    card.style.transition = "transform 750ms cubic-bezier(0.3, 0, 0.8, 0.15), opacity 750ms";
    card.style.opacity = "0";
    card.style.transform = "translateY(-24px)";
    requestAnimationFrame(() => requestAnimationFrame(() => {
      card.style.transition = "transform 600ms cubic-bezier(0.05, 0.7, 0.1, 1), opacity 300ms";
      card.style.opacity = "1";
      card.style.transform = "none";
    }));

    setTimeout(() => {
      // Fly into the Library tab, where unread spam is counted.
      const from = card.getBoundingClientRect();
      const to = badge.getBoundingClientRect();
      const dx = to.left + to.width / 2 - (from.left + from.width / 2);
      const dy = to.top + to.height / 2 - (from.top + from.height / 2);
      card.style.transition = "transform 750ms cubic-bezier(0.3, 0, 0.8, 0.15), opacity 750ms";
      card.style.transform = `translate(${dx}px, ${dy}px) scale(0.08)`;
      card.style.opacity = "0.2";
      setTimeout(() => {
        card.remove();
        badge.textContent = persian(3);
        badge.classList.add("bump");
      }, reduced ? 0 : 760);
    }, 2600);
  }

  show("all");
  const watcher = new IntersectionObserver(
    (entries) => {
      if (!entries.some((e) => e.isIntersecting)) return;
      watcher.disconnect();
      setTimeout(arrive, 900);
    },
    { threshold: 0.6 },
  );
  watcher.observe(screen);
})();
