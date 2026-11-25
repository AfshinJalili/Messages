// Fills download links and version details from release.json, which the Pages workflow
// writes at deploy time (tools/site_release_json.sh). Without it every link keeps its
// static fallback: the Latest release on GitHub.
(() => {
  const persian = (value) => String(value).replace(/\d/g, (d) => "۰۱۲۳۴۵۶۷۸۹"[d]);
  const megabytes = (bytes) =>
    `${persian((bytes / 1048576).toFixed(1)).replace(".", "٫")} مگابایت`;
  const date = (iso) => new Date(iso).toLocaleDateString("fa-IR", { dateStyle: "long" });

  /** Numeric parts of "1.8.0" or "1.8.0-nightly…"; null when the value is not a version. */
  const parts = (version) => {
    const match = /^v?(\d+(?:\.\d+)*)/.exec(version || "");
    return match ? match[1].split(".").map(Number) : null;
  };
  const compare = (a, b) => {
    for (let i = 0; i < Math.max(a.length, b.length); i++) {
      const d = (a[i] || 0) - (b[i] || 0);
      if (d) return Math.sign(d);
    }
    return 0;
  };

  const fields = {
    version: (r) => persian(r.version),
    date: (r) => date(r.published),
    size: (r) => megabytes(r.apk.size),
    sha256: (r) => r.apk.sha256,
  };

  function fill(data) {
    for (const channel of ["stable", "nightly"]) {
      const release = data[channel];
      if (!release || !release.apk) continue;
      document.querySelectorAll(`[data-apk="${channel}"]`).forEach((a) => (a.href = release.apk.url));
      document.querySelectorAll(`[data-notes="${channel}"]`).forEach((a) => (a.href = release.page));
      document.querySelectorAll(`[data-release^="${channel}."]`).forEach((el) => {
        el.textContent = fields[el.dataset.release.split(".")[1]](release);
      });
      // Parts that only make sense with real values (size, date, checksum) stay hidden until now.
      document.querySelectorAll(`[data-when-loaded="${channel}"]`).forEach((el) => {
        if (el.dataset.needs !== "sha256" || release.apk.sha256) el.hidden = false;
      });
    }
    showUpdateStatus(data);
  }

  // The app's "Check for updates" opens download.html?v=<versionName>.
  function showUpdateStatus(data) {
    const box = document.getElementById("update-status");
    const installedName = new URLSearchParams(location.search).get("v");
    const installed = parts(installedName);
    if (!box || !installed || !data.stable) return;
    const text = box.querySelector("[data-status-text]");
    const icon = box.querySelector("use");
    const mine = `<bdi>${persian(installedName.split("-")[0])}</bdi>`;
    const latest = `<bdi>${persian(data.stable.version)}</bdi>`;

    if (/nightly/i.test(installedName)) {
      text.innerHTML = `نسخهٔ شبانهٔ ${mine} روی گوشی شماست. تازه‌ترین نسخهٔ شبانه پایین همین صفحه است.`;
      document.getElementById("nightly")?.classList.add("highlight");
    } else if (compare(installed, parts(data.stable.version)) < 0) {
      box.classList.add("new");
      icon.setAttribute("href", "assets/icons.svg#i-download");
      text.innerHTML = `نسخهٔ تازه آماده است: ${latest}. نسخهٔ شما ${mine} است. فایل تازه را روی همان برنامه نصب کنید؛ پیام‌ها و تنظیمات سر جایشان می‌مانند.`;
    } else {
      box.classList.add("current");
      icon.setAttribute("href", "assets/icons.svg#i-check_circle");
      text.innerHTML = `برنامهٔ شما به‌روز است. نسخهٔ ${mine} تازه‌ترین نسخه است.`;
    }
    box.hidden = false;
  }

  fetch(`release.json`, { cache: "no-cache" })
    .then((response) => (response.ok ? response.json() : Promise.reject(response.status)))
    .then(fill)
    .catch(() => {});
})();
