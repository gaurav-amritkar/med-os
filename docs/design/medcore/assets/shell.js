/* Shared app shell for the MedCore sample mockups.
   Reads:  <body data-active="capacity-forecasts" data-title="Capacity Forecasts" data-submenu?...>
   Builds: sidebar (left) + header top bar. */

(function () {
  const ICONS = {
    dashboard: `<path d="M3.5 9.5 10 4l6.5 5.5V16a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1V9.5Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>`,
    beds: `<path d="M3 16v-5h13a3 3 0 0 1 3 3v2M3 11V7m0 9v-1.5M18 16v1.5M3 13h15" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><circle cx="7" cy="8.5" r="1.6" fill="none" stroke="currentColor" stroke-width="1.5"/>`,
    emergency: `<path d="M6 4h8l3 4v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8l3-4Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M10 9.5v4M8 11.5h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>`,
    staff: `<circle cx="7" cy="7.5" r="2.6" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M2.8 16.5a4.4 4.4 0 0 1 8.4 0" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><circle cx="14.5" cy="8.5" r="2" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M12.7 16.5a3.5 3.5 0 0 1 6 0" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>`,
    alerts: `<path d="M10 3.2 17 15.5H3L10 3.2Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M10 8v3.2M10 13.4v.2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>`,
    forecast: `<rect x="3" y="3" width="6" height="6" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.5"/><rect x="11" y="3" width="6" height="10" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.5"/><rect x="3" y="11" width="6" height="6" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.5"/>`,
    reports: `<path d="M5 3h7l3 3v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M7 9h6M7 12h6M7 15h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>`,
    help: `<circle cx="10" cy="10" r="7.5" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M8 8.2a2.1 2.1 0 1 1 2.6 2.3c-.5.15-.6.5-.6 1" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M10 13.6v.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>`,
    settings: `<circle cx="10" cy="10" r="2.6" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M16.3 12a1.4 1.4 0 0 0 .3 1.5l.1.1a1.6 1.6 0 1 1-2.3 2.3l-.1-.1a1.4 1.4 0 0 0-1.5-.3 1.4 1.4 0 0 0-.9 1.3v.2a1.6 1.6 0 1 1-3.2 0v-.1a1.4 1.4 0 0 0-1-1.3 1.4 1.4 0 0 0-1.5.3l-.1.1a1.6 1.6 0 1 1-2.3-2.3l.1-.1a1.4 1.4 0 0 0 .3-1.5 1.4 1.4 0 0 0-1.3-.9h-.2a1.6 1.6 0 1 1 0-3.2h.1a1.4 1.4 0 0 0 1.3-1 1.4 1.4 0 0 0-.3-1.5l-.1-.1A1.6 1.6 0 1 1 4.8 3l.1.1a1.4 1.4 0 0 0 1.5.3H7a1.4 1.4 0 0 0 .9-1.3v-.2a1.6 1.6 0 1 1 3.2 0v.1a1.4 1.4 0 0 0 .9 1.3 1.4 1.4 0 0 0 1.5-.3l.1-.1a1.6 1.6 0 1 1 2.3 2.3l-.1.1a1.4 1.4 0 0 0-.3 1.5V7a1.4 1.4 0 0 0 1.3.9h.2a1.6 1.6 0 1 1 0 3.2h-.1a1.4 1.4 0 0 0-1.3.9Z" fill="none" stroke="currentColor" stroke-width="1.3"/>`,
  };

  const NAV = [
    { sec: "Main Menu" },
    { id: "dashboard", label: "Dashboard" },
    { id: "bed-wards", label: "Bed & Wards" },
    { id: "emergency-flow", label: "Emergency Flow" },
    { id: "staff-management", label: "Staff Management" },
    { sec: "Monitoring & Control" },
    { id: "critical-alerts", label: "Critical Alerts" },
    { id: "capacity-forecasts", label: "Capacity Forecasts" },
    { sec: "Analytics & Reports" },
    { id: "operational-reports", label: "Operational Reports" },
    { id: "help-supports", label: "Help & Supports" },
    { id: "settings", label: "Settings" },
  ];

  function icon(name) {
    return `<svg width="19" height="19" viewBox="0 0 20 20" aria-hidden="true">${ICONS[name] || ""}</svg>`;
  }

  const slug = (label) => label.toLowerCase().replace(/[^a-z]+/g, "-").replace(/(^-|-$)/g, "");

  function sidebar(active) {
    let html = `
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-name">
        <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden="true">
          <path d="M5 5c3.5.4 5.5 2.6 5.8 6.5.3-3.9 2.3-6.1 5.8-6.5-1 4.6-4.4 6-5.8 16C9.4 11 6 9.6 5 5Z" fill="#f5a623"/>
          <path d="M13.5 3.5c2.2 1.5 3.3 4 3.3 7.6 0 4.4-1.6 8.8-3.3 10.9-1.7-2.1-3.3-6.5-3.3-10.9 0-3.6 1.1-6.1 3.3-7.6Z" fill="#ffffff" opacity=".0"/>
        </svg>
        MedCore
      </span>
      <span class="brand-collapse" title="Collapse sidebar">
        <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M2 3h10M2 7h10M2 11h10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </span>
    </div>
    <div class="search-pill">
      <svg width="15" height="15" viewBox="0 0 15 15" aria-hidden="true"><circle cx="6.7" cy="6.7" r="4.6" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="m10.3 10.3 2.8 2.8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      Search <span class="kbd">⌘ K</span>
    </div>
    <nav aria-label="Main navigation">`;
    for (const item of NAV) {
      if (item.sec) {
        html += `<div class="nav-label">${item.sec}</div>`;
      } else {
        const isActive = item.id === active;
        const href = isActive ? "#" : `./${String(getIndex(item.id)).padStart(2, "0")}-${item.id}.html`;
        html += `<a class="nav-item${isActive ? " is-active" : ""}" ${isActive ? 'aria-current="page"' : ""} href="${href}">${icon(item.id)}<span>${item.label}</span></a>`;
      }
    }
    html += `</nav>
  </aside>`;
    return html;
  }

  const ORDER = ["dashboard", "bed-wards", "emergency-flow", "staff-management", "critical-alerts", "capacity-forecasts", "operational-reports", "help-supports", "settings"];
  function getIndex(id) { return ORDER.indexOf(id) + 1; }

  function shell() {
    const body = document.body;
    const active = body.dataset.active || "dashboard";
    const title = body.dataset.title || "Dashboard";

    document.body.insertAdjacentHTML("afterbegin", `<div class="app">${sidebar(active)}
  <div class="main">
    <header class="page-header">
      <h1></h1>
      <div class="header-actions">
        <button class="icon-btn" aria-label="Notifications">
          <svg width="17" height="17" viewBox="0 0 17 17" aria-hidden="true"><path d="M8.5 2a4.6 4.6 0 0 1 4.6 4.6c0 3.4 1.4 4.6 1.4 4.6H2.5s1.4-1.2 1.4-4.6A4.6 4.6 0 0 1 8.5 2Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M7 13.8a1.6 1.6 0 0 0 3 0" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
        </button>
        <span style="width:1px; height:22px; background:var(--line-strong);"></span>
        <span class="avatar" title="Dr. Aditi Raghavan">AR</span>
      </div>
    </header>
    <div class="page-content"></div>
    <div class="footer-note">MedCore · sample mockup for MedOS design review</div>
  </div>
</div>`);

    document.querySelector(".page-header h1").textContent = title;

    const original = Array.from(document.body.childNodes).filter((node) => node.parentNode === document.body);
    const content = document.createElement("div");
    content.className = "page-body";
    for (const node of original) {
      const isApp = node.nodeType === 1 && node.tagName === "DIV" && node.className === "app";
      if (!isApp) content.appendChild(node);
    }
    document.querySelector(".page-content").appendChild(content);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", shell);
  } else {
    shell();
  }
})();
