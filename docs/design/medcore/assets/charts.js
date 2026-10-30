/* Tiny inline-SVG chart helpers for the MedCore sample mockups.
   No external dependencies; each function renders into a container element. */

(function () {
  const NS = "http://www.w3.org/2000/svg";

  function el(name, attrs, parent) {
    const node = document.createElementNS(NS, name);
    for (const k in attrs) node.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(node);
    return node;
  }

  function svgWithSize(container, height) {
    const width = container.clientWidth || 560;
    const svg = el("svg", {
      viewBox: `0 0 ${width} ${height}`,
      width: "100%",
      height: height,
      style: "display:block; overflow:visible;",
    });
    container.appendChild(svg);
    return { svg, w: width, h: height };
  }

  function niceBounds(values, maxPad) {
    let min = Math.min(...values);
    let max = Math.max(...values);
    for (const t of (maxPad || [])) if (t != null) max = Math.max(max, t);
    min = Math.min(0, min);
    const span = Math.max(max - min, 1);
    const pad = span * 0.12;
    max = Math.ceil((max + pad) / 50) * 50 || max + pad;
    if (max < 10) { max = Math.ceil(max + pad); }
    return { min, max };
  }

  function gridAndAxis(svg, w, h, pad, min, max, ticks, fmt) {
    for (let i = 0; i <= ticks; i++) {
      const v = min + ((max - min) * i) / ticks;
      const y = h - pad.b - ((h - pad.t - pad.b) * i) / ticks;
      el("line", { x1: pad.l, x2: w - pad.r, y1: y, y2: y, stroke: "#d9d9dc", "stroke-dasharray": "1 5", "stroke-width": 1.4 }, svg);
      el("text", { x: pad.l - 10, y: y + 4, "text-anchor": "end", fill: "#9ca3af", "font-size": 12 }, svg)
        .textContent = fmt ? fmt(v) : String(Math.round(v));
    }
  }

  const thresholdMeta = {
    critical: { color: "#ef4444" },
    warning: { color: "#f59e0b" },
    normal: { color: "#10b981" },
  };

  /* Smooth-ish catmull-rom to bezier path */
  function smoothPath(pts) {
    if (pts.length < 2) return "";
    let d = `M ${pts[0].x} ${pts[0].y}`;
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[Math.max(i - 1, 0)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(i + 2, pts.length - 1)];
      const c1x = p1.x + (p2.x - p0.x) / 6, c1y = p1.y + (p2.y - p0.y) / 6;
      const c2x = p2.x - (p3.x - p1.x) / 6, c2y = p2.y - (p3.y - p1.y) / 6;
      d += ` C ${c1x} ${c1y}, ${c2x} ${c2y}, ${p2.x} ${p2.y}`;
    }
    return d;
  }

  const Charts = {};

  /* Forecast line: solid series, dotted threshold lines, right-edge forecast rail
     with Critical / Warning / Normal labels — replicates the Dribbble forecast cards. */
  Charts.forecastLine = function (container, opts) {
    const labels = opts.labels;
    const values = opts.values;
    const th = opts.thresholds;          // {critical, warning, normal}
    const ticks = opts.ticks || 4;
    const fmt = opts.fmt;
    const { svg, w } = svgWithSize(container, opts.height || 250);
    const pad = { l: 44, r: 96, t: 26, b: 30 };
    const h = opts.height || 250;

    const { min, max } = niceBounds(values, Object.values(th));
    gridAndAxis(svg, w, h, pad, min, max, ticks, fmt);

    const X = (i) => pad.l + ((w - pad.l - pad.r) * i) / (labels.length - 1);
    const Y = (v) => h - pad.b - ((h - pad.t - pad.b) * (v - min)) / (max - min);

    // dotted threshold lines
    for (const k of ["critical", "warning", "normal"]) {
      if (th[k] == null) continue;
      const y = Y(th[k]);
      el("line", { x1: pad.l, x2: pad.l + (w - pad.l - pad.r), y1: y, y2: y, stroke: thresholdMeta[k].color, "stroke-dasharray": "1 6", "stroke-width": 1.6 }, svg);
      el("text", { x: pad.l + (w - pad.l - pad.r) + 16, y: y + 4, fill: thresholdMeta[k].color, "font-size": 12.5, "font-weight": 500 }, svg).textContent =
        k.charAt(0).toUpperCase() + k.slice(1);
    }

    // series
    const pts = values.map((v, i) => ({ x: X(i), y: Y(v) }));
    el("path", { d: smoothPath(pts), fill: "none", stroke: "#f5a623", "stroke-width": 3.4, "stroke-linecap": "round" }, svg);

    // right-edge forecast rail
    el("line", { x1: X(labels.length - 1), x2: X(labels.length - 1), y1: Y(th.critical != null ? th.critical : max), y2: h - pad.b, stroke: "#ef4444", "stroke-width": 2.4, "stroke-linecap": "round" }, svg);

    // x labels
    labels.forEach((lb, i) => {
      if (labels.length > 8 && i % 2 === 1) return;
      el("text", { x: X(i), y: h - 8, "text-anchor": "middle", fill: "#9ca3af", "font-size": 12 }, svg).textContent = lb;
    });
  };

  /* Declining bar chart with diagonal-hatch bars and value captions
     (Oxygen & Equipment card in the shot). */
  Charts.declineBars = function (container, opts) {
    const labels = opts.labels;
    const values = opts.values;
    const h = opts.height || 250;
    const { svg, w } = svgWithSize(container, h);
    const pad = { l: 40, r: 12, t: 26, b: 30 };
    const maxV = Math.max(...values) * 1.08;
    const bw = (w - pad.l - pad.r) / labels.length;

    // hatch pattern
    const defs = el("defs", {}, svg);
    const pattern = el("pattern", { id: (opts.id || "hatch") + "-pt", width: 9, height: 9, patternTransform: "rotate(45)", patternUnits: "userSpaceOnUse" }, defs);
    el("rect", { width: 9, height: 9, fill: "#7fb8f7" }, pattern);
    el("line", { x1: 0, y1: 0, x2: 0, y2: 9, stroke: "#fff", "stroke-width": 2.4, opacity: 0.7 }, pattern);

    // y grid
    for (let i = 0; i <= 4; i++) {
      const v = (maxV / 4) * i;
      const y = h - pad.b - ((h - pad.t - pad.b) * i) / 4;
      el("line", { x1: pad.l, x2: w - pad.r, y1: y, y2: y, stroke: "#d9d9dc", "stroke-dasharray": "1 5", "stroke-width": 1.4 }, svg);
      el("text", { x: pad.l - 9, y: y + 4, "text-anchor": "end", fill: "#9ca3af", "font-size": 12 }, svg).textContent = String(Math.round(v));
    }

    values.forEach((v, i) => {
      const bh = ((h - pad.t - pad.b) * v) / maxV;
      const x = pad.l + i * bw;
      const y = h - pad.b - bh;
      const bar = el("rect", { x: x + 2, y, width: bw - 6, height: bh, rx: 2 }, svg);
      if (i === 0) bar.setAttribute("fill", "url(#" + (opts.id || "hatch") + "-pt)");
      else bar.setAttribute("fill", "url(#" + (opts.id || "hatch") + "-pt)");
      el("text", { x: x + bw / 2 - 2, y: y - 8, "text-anchor": "middle", fill: "#17181c", "font-size": 13, "font-weight": 600 }, svg).textContent = String(v);
      el("text", { x: x + bw / 2 - 2, y: h - 8, "text-anchor": "middle", fill: "#9ca3af", "font-size": 12 }, svg).textContent = labels[i];
    });
  };

  /* Multi-series smooth line chart (Staff Demand, trends). */
  Charts.multiLine = function (container, opts) {
    const labels = opts.labels;
    const series = opts.series;
    const h = opts.height || 250;
    const { svg, w } = svgWithSize(container, h);
    const pad = { l: 40, r: 16, t: 18, b: 30 };
    const all = series.flatMap((s) => s.values);
    const { min, max } = niceBounds(all);
    const X = (i) => pad.l + ((w - pad.l - pad.r) * i) / (labels.length - 1);
    const Y = (v) => h - pad.b - ((h - pad.t - pad.b) * (v - min)) / (max - min);

    for (let i = 0; i <= 4; i++) {
      const v = min + ((max - min) * i) / 4;
      const y = Y(v);
      el("line", { x1: pad.l, x2: w - pad.r, y1: y, y2: y, stroke: "#d9d9dc", "stroke-dasharray": "1 5", "stroke-width": 1.4 }, svg);
      el("text", { x: pad.l - 9, y: y + 4, "text-anchor": "end", fill: "#9ca3af", "font-size": 12 }, svg).textContent = String(Math.round(v));
    }

    series.forEach((s) => {
      const pts = s.values.map((v, i) => ({ x: X(i), y: Y(v) }));
      el("path", { d: smoothPath(pts), fill: "none", stroke: s.color, "stroke-width": 2.8, "stroke-linecap": "round" }, svg);
    });

    labels.forEach((lb, i) => {
      if (labels.length > 8 && i % 2 === 1) return;
      el("text", { x: X(i), y: h - 8, "text-anchor": "middle", fill: "#9ca3af", "font-size": 12 }, svg).textContent = lb;
    });
  };

  /* Horizontal bars for category loads (DOM based). */
  Charts.hBars = function (container, items, color) {
    const maxV = Math.max(...items.map((i) => i.value));
    container.innerHTML = "";
    items.forEach((it) => {
      const row = document.createElement("div");
      row.style.cssText = "display:flex; flex-direction:column; gap:6px; padding:9px 0;";
      const top = document.createElement("div");
      top.style.cssText = "display:flex; justify-content:space-between; font-size:13.5px; color:#6b7280;";
      top.innerHTML = `<span>${it.name}</span><b style="color:#17181c; font-weight:600;">${it.value}</b>`;
      const bar = document.createElement("div");
      bar.className = "progress";
      bar.innerHTML = `<i style="width:${(it.value / maxV) * 100}%; background:${color || "var(--blue)"};"></i>`;
      row.append(top, bar);
      container.appendChild(row);
    });
  };

  window.Charts = Charts;
})();
