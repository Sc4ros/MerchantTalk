"use strict";

// ------------------------------------------------------------------ utilitaires
const $ = (sel, root = document) => root.querySelector(sel);
const el = (tag, props = {}, ...children) => {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") e.className = v;
    else if (k === "text") e.textContent = v;
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (v !== undefined && v !== null && v !== false) e.setAttribute(k, v === true ? "" : v);
  }
  for (const c of children) if (c != null) e.append(c);
  return e;
};

const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* stockage indisponible */ } },
};

let token = store.get("mt-token");
let state = { merchants: [], sounds: [], triggers: [], sections: [], pack: {}, players: [] };
let selectedId = store.get("mt-selected");
let draft = null;     // copie du marchand en cours d'édition
let dirty = false;
let currentAudio = null;
let testPlayer = "";  // joueur qui entend les tests ("" = le plus proche)

function toast(msg, isError = false) {
  const t = $("#toast");
  t.textContent = msg;
  t.classList.toggle("error", isError);
  t.classList.add("show");
  clearTimeout(t._timer);
  t._timer = setTimeout(() => t.classList.remove("show"), isError ? 5000 : 2500);
}

async function api(method, path, body, headers = {}) {
  const opts = { method, headers: { ...headers } };
  if (token) opts.headers.Authorization = "Bearer " + token;
  if (body instanceof Blob || body instanceof ArrayBuffer) opts.body = body;
  else if (body !== undefined) { opts.body = JSON.stringify(body); opts.headers["Content-Type"] = "application/json"; }
  const res = await fetch("/api/" + path, opts);
  let data = null;
  try { data = await res.json(); } catch { /* pas de JSON */ }
  if (res.status === 401) { logout(); throw new Error("Session expirée, reconnecte-toi."); }
  if (!res.ok) throw new Error((data && data.error) || ("Erreur " + res.status));
  return data;
}

const fmtDur = (s) => s ? s.toFixed(1) + " s" : "?";
const fmtMoney = (n) => (n || 0).toLocaleString("fr-FR", { maximumFractionDigits: 2 });
const fmtDate = (ms) => ms ? new Date(ms).toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }) : "jamais";
const stripTags = (s) => (s || "").replace(/<[^>]*>/g, "");
const soundUrl = (id) => `/api/sounds/${encodeURIComponent(id)}.ogg?token=${encodeURIComponent(token)}`;
const skinUrl = (m) => `/api/merchants/${encodeURIComponent(m.id)}/skin?token=${encodeURIComponent(token)}&v=${encodeURIComponent(m.skinFile || "")}`;
const debounce = (fn, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };

function play(id) {
  if (currentAudio) { currentAudio.pause(); currentAudio = null; }
  if (!id) return;
  currentAudio = new Audio(soundUrl(id));
  currentAudio.play().catch(() => toast("Lecture impossible dans ce navigateur", true));
}

// ------------------------------------------------------------------ skins
const images = new Map();
function loadImage(src) {
  if (!images.has(src)) {
    images.set(src, new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = src;
    }));
  }
  return images.get(src);
}

function placeholder(ctx, w, h) {
  ctx.fillStyle = "#2e3342";
  ctx.fillRect(0, 0, w, h);
  ctx.fillStyle = "#9097a8";
  ctx.font = `${Math.min(w, h) / 2}px system-ui`;
  ctx.textAlign = "center"; ctx.textBaseline = "middle";
  ctx.fillText("?", w / 2, h / 2 + 2);
}

// Tête (avec la couche du chapeau).
async function drawFace(canvas, src) {
  const ctx = canvas.getContext("2d");
  ctx.imageSmoothingEnabled = false;
  if (!src) return placeholder(ctx, canvas.width, canvas.height);
  try {
    const img = await loadImage(src);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 8, 8, 8, 8, 0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 40, 8, 8, 8, 0, 0, canvas.width, canvas.height);
  } catch { placeholder(ctx, canvas.width, canvas.height); }
}

// Personnage entier vu de face (16 x 32 pixels de skin, agrandis), bras fins gérés.
async function drawBody(canvas, src, slim) {
  const ctx = canvas.getContext("2d");
  ctx.imageSmoothingEnabled = false;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  if (!src) return placeholder(ctx, canvas.width, canvas.height);
  let img;
  try { img = await loadImage(src); } catch { return placeholder(ctx, canvas.width, canvas.height); }
  const s = canvas.width / 16;
  const legacy = img.height === 32; // ancien format 64x32 : bras/jambe gauches = miroir des droits
  const aw = slim ? 3 : 4;
  const part = (sx, sy, w, h, dx, dy, mirror = false) => {
    ctx.save();
    if (mirror) { ctx.translate((dx + w) * s, dy * s); ctx.scale(-1, 1); ctx.drawImage(img, sx, sy, w, h, 0, 0, w * s, h * s); }
    else ctx.drawImage(img, sx, sy, w, h, dx * s, dy * s, w * s, h * s);
    ctx.restore();
  };
  // Couche de base
  part(8, 8, 8, 8, 4, 0);                                     // tête
  part(20, 20, 8, 12, 4, 8);                                  // torse
  part(44, 20, aw, 12, 4 - aw, 8);           // bras droit (à gauche à l'écran)
  if (legacy) part(44, 20, aw, 12, 12, 8, true); else part(36, 52, aw, 12, 12, 8); // bras gauche
  part(4, 20, 4, 12, 4, 20);                                  // jambe droite
  if (legacy) part(4, 20, 4, 12, 8, 20, true); else part(20, 52, 4, 12, 8, 20);    // jambe gauche
  // Seconde couche (chapeau, veste, manches, pantalon)
  part(40, 8, 8, 8, 4, 0);
  if (!legacy) {
    part(20, 36, 8, 12, 4, 8);
    part(44, 36, aw, 12, 4 - aw, 8);
    part(52, 52, aw, 12, 12, 8);
    part(4, 36, 4, 12, 4, 20);
    part(4, 52, 4, 12, 8, 20);
  }
}

// ------------------------------------------------------------------ aperçu MiniMessage (rendu par le serveur)
async function renderPreviews(items) {
  if (!items.length) return [];
  try {
    const res = await api("POST", "preview", { items: items.map(({ type, text, npc }) => ({ type, text, npc })) });
    return res.html;
  } catch { return items.map(() => ""); }
}

const refreshEditorPreviews = debounce(async () => {
  if (!draft) return;
  const targets = [];
  const nameBox = $("#name-preview");
  if (nameBox) targets.push({ type: "name", text: draft.displayName || draft.id, box: nameBox });
  document.querySelectorAll("#editor-content .line").forEach((row) => {
    const text = $(".line-text", row).value;
    const box = $(".line-preview", row);
    if (text.trim()) targets.push({ type: "line", text, npc: draft.displayName || draft.id, box });
    else { box.innerHTML = ""; box.classList.add("empty"); }
  });
  const html = await renderPreviews(targets);
  targets.forEach((t, i) => { t.box.innerHTML = html[i] || ""; t.box.classList.toggle("empty", !html[i]); });
}, 250);

// ------------------------------------------------------------------ connexion
function showLogin() {
  $("#app").classList.add("hidden");
  $("#login").classList.remove("hidden");
  $("#login-password").focus();
}

function logout() {
  token = null;
  store.set("mt-token", null);
  showLogin();
}

$("#login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("#login-error").textContent = "";
  try {
    const res = await api("POST", "login", { password: $("#login-password").value });
    token = res.token;
    store.set("mt-token", token);
    $("#login-password").value = "";
    await start();
  } catch (err) {
    $("#login-error").textContent = err.message;
  }
});

$("#logout").addEventListener("click", logout);

async function start() {
  await refresh();
  $("#login").classList.add("hidden");
  $("#app").classList.remove("hidden");
}

async function refresh() {
  state = await api("GET", "state");
  renderPackStatus();
  renderMerchantList();
  renderVoices();
  if (selectedId && state.merchants.some((m) => m.id === selectedId)) {
    if (!dirty) openMerchant(selectedId);
  } else {
    selectedId = null;
    draft = null;
    renderEditor();
  }
}

// Mise à jour légère toutes les 15 s (joueurs connectés, statistiques) sans toucher à l'édition en cours.
setInterval(async () => {
  if (!token || document.hidden || $("#app").classList.contains("hidden")) return;
  try {
    const fresh = await api("GET", "state");
    state.players = fresh.players;
    state.pack = fresh.pack;
    for (const m of fresh.merchants) {
      const local = state.merchants.find((x) => x.id === m.id);
      if (local) { local.stats = m.stats; local.placed = m.placed; local.now = m.now; }
    }
    renderPackStatus();
    renderPlayersSelect();
    renderStats();
    renderNow();
    document.querySelectorAll("#editor-content .line").forEach((row) => row.dispatchEvent(new Event("refresh-now")));
  } catch { /* hors ligne : on réessaiera */ }
}, 15000);

// ------------------------------------------------------------------ onglets
document.querySelectorAll(".tab").forEach((btn) => btn.addEventListener("click", () => {
  document.querySelectorAll(".tab").forEach((b) => b.classList.toggle("active", b === btn));
  $("#tab-merchants").classList.toggle("hidden", btn.dataset.tab !== "merchants");
  $("#tab-voices").classList.toggle("hidden", btn.dataset.tab !== "voices");
}));

function renderPackStatus() {
  const p = $("#pack-status");
  const n = state.sounds.length;
  const online = (state.players || []).length;
  p.textContent = (state.pack.enabled ? (n ? `Pack prêt · ${n} voix` : "Pack vide") : "Pack désactivé") + ` · ${online} en ligne`;
  p.className = "pill" + (n && state.pack.enabled ? " ok" : "");
  p.title = state.pack.url || "";
}

// ------------------------------------------------------------------ liste des marchands
$("#merchant-search").addEventListener("input", () => renderMerchantList());

function renderMerchantList() {
  const ul = $("#merchant-list");
  const q = $("#merchant-search").value.trim().toLowerCase();
  $("#merchant-search").classList.toggle("hidden", state.merchants.length < 6);
  ul.replaceChildren();
  const list = state.merchants.filter((m) => !q || m.id.includes(q) || stripTags(m.displayName).toLowerCase().includes(q));
  if (!list.length) {
    ul.append(el("li", { class: "muted", text: state.merchants.length ? "Aucun résultat" : "Aucun marchand" }));
    return;
  }
  for (const m of list) {
    const face = el("canvas", { class: "face", width: 32, height: 32 });
    drawFace(face, m.skinFile ? skinUrl(m) : null);
    const li = el("li", { class: m.id === selectedId ? "active" : "", onclick: () => selectMerchant(m.id) },
      face,
      el("div", { class: "meta" },
        el("b", { text: stripTags(m.displayName) || m.id }),
        el("small", {}, el("span", { class: "dot " + (m.placed ? "on" : "off") }), m.placed ? m.id : m.id + " · pas placé")));
    ul.append(li);
  }
}

function selectMerchant(id) {
  if (dirty && id !== selectedId && !confirm("Des modifications ne sont pas enregistrées. Les abandonner ?")) return;
  dirty = false;
  openMerchant(id);
}

function openMerchant(id) {
  selectedId = id;
  store.set("mt-selected", id);
  const m = state.merchants.find((x) => x.id === id);
  draft = m ? JSON.parse(JSON.stringify(m)) : null;
  if (draft) {
    draft.lines = draft.lines || {};
    for (const t of state.triggers) draft.lines[t.id] = draft.lines[t.id] || [];
  }
  renderMerchantList();
  renderEditor();
}

// Création d'un marchand (formulaire dans la barre latérale)
const newNamePreview = debounce(async () => {
  const [html] = await renderPreviews([{ type: "name", text: $("#new-name").value }]);
  $("#new-name-preview").innerHTML = html || "";
}, 250);
$("#new-merchant").addEventListener("click", () => {
  $("#new-form").classList.toggle("hidden");
  $("#new-id").focus();
});
$("#new-cancel").addEventListener("click", () => $("#new-form").classList.add("hidden"));
$("#new-name").addEventListener("input", newNamePreview);
$("#new-id").addEventListener("input", () => {
  $("#new-id").value = $("#new-id").value.toLowerCase().replace(/[^a-z0-9_-]/g, "");
});
async function createMerchant() {
  const id = $("#new-id").value.trim();
  if (!id) return toast("Choisis un identifiant", true);
  if (dirty && !confirm("Des modifications ne sont pas enregistrées. Les abandonner ?")) return;
  try {
    await api("POST", "merchants", { id, displayName: $("#new-name").value });
    dirty = false;
    selectedId = id;
    $("#new-form").classList.add("hidden");
    $("#new-id").value = ""; $("#new-name").value = ""; $("#new-name-preview").innerHTML = "";
    await refresh();
    toast("Marchand créé ! Place-le en jeu avec /mt place " + id);
  } catch (err) { toast(err.message, true); }
}
$("#new-create").addEventListener("click", createMerchant);
["#new-id", "#new-name"].forEach((s) => $(s).addEventListener("keydown", (e) => { if (e.key === "Enter") createMerchant(); }));

// ------------------------------------------------------------------ éditeur
function markDirty() {
  dirty = true;
  const btn = $("#save-btn");
  if (btn) btn.disabled = false;
  const bar = $(".save-bar");
  if (bar) bar.classList.add("dirty");
}

function field(label, input, hint) {
  return el("label", { class: "field" }, label, input, hint ? el("span", { class: "hint", text: hint }) : null);
}

function renderEditor() {
  const content = $("#editor-content");
  const empty = $("#editor-empty");
  if (!draft) { content.classList.add("hidden"); empty.classList.remove("hidden"); return; }
  empty.classList.add("hidden");
  content.classList.remove("hidden");
  content.replaceChildren();
  const m = draft;

  // En-tête
  const preview = el("canvas", { class: "skin-preview", width: 96, height: 96 });
  drawFace(preview, m.skinFile ? skinUrl(m) : null);
  content.append(el("div", { class: "card editor-head" },
    preview,
    el("div", { class: "title" },
      el("h2", { id: "name-preview", class: "mm-title" }),
      el("div", { class: "muted" }, "ID : ", el("code", { text: m.id }),
        m.placed ? el("span", { class: "tag ok", text: "Placé dans le monde" }) : el("span", { class: "tag warn", text: "Pas placé" }))),
    el("div", { class: "head-actions" },
      el("button", { class: "btn", onclick: duplicateMerchant, title: "Copier ce marchand (réglages, skin, répliques)" }, "Dupliquer"),
      el("button", { class: "btn danger", onclick: deleteMerchant }, "Supprimer"))));

  if (!m.placed) {
    content.append(el("div", { class: "notice" },
      "Ce marchand n'est pas encore placé dans le monde. En jeu, va à l'endroit voulu et tape ",
      el("code", { text: "/mt place " + m.id }), "."));
  }
  if (!state.npcReady) {
    content.append(el("div", { class: "notice", text: "FancyNpcs n'a pas encore fini de charger sur le serveur." }));
  }

  // Statistiques
  content.append(el("div", { class: "card", id: "stats-card" }));
  renderStats();

  // Réglages généraux
  const nameInput = el("input", { type: "text", value: m.displayName || "", maxlength: 200, placeholder: "<gold>Marchand" });
  nameInput.addEventListener("input", () => { m.displayName = nameInput.value; markDirty(); refreshEditorPreviews(); });

  const sectionList = el("datalist", { id: "sections-list" }, ...state.sections.map((s) => el("option", { value: s })));
  const sectionInput = el("input", { type: "text", value: m.section || "", list: "sections-list", placeholder: "Vide = menu principal du shop" });
  sectionInput.addEventListener("input", () => { m.section = sectionInput.value; markDirty(); });

  const permission = el("input", { type: "text", value: m.permission || "", maxlength: 100, placeholder: "Vide = tout le monde (ex : rang.vip)" });
  permission.addEventListener("input", () => { m.permission = permission.value.replace(/\s/g, ""); markDirty(); });

  const radius = el("input", { type: "number", min: 0, max: 32, step: 0.5, value: m.greetRadius });
  radius.addEventListener("input", () => { m.greetRadius = parseFloat(radius.value) || 0; markDirty(); });
  const cooldown = el("input", { type: "number", min: 0, max: 86400, step: 1, value: m.greetCooldownSeconds });
  cooldown.addEventListener("input", () => { m.greetCooldownSeconds = parseInt(cooldown.value) || 0; markDirty(); });

  const look = el("input", { type: "checkbox", checked: m.lookAtPlayer });
  look.addEventListener("change", () => { m.lookAtPlayer = look.checked; markDirty(); });
  const particles = el("input", { type: "checkbox", checked: m.talkParticles !== false });
  particles.addEventListener("change", () => { m.talkParticles = particles.checked; markDirty(); });

  content.append(el("div", { class: "card" },
    el("h2", { text: "Réglages" }),
    el("div", { class: "grid-2" },
      field("Nom affiché", nameInput, "Couleurs MiniMessage : <gold>, <bold>, <#ff8800>, <gradient:gold:red>…"),
      field("Section du shop EconomyShopGUI", el("span", {}, sectionInput, sectionList), state.sections.length ? "Choisis dans la liste ou laisse vide" : null),
      field("Permission requise", permission, "Les joueurs sans cette permission entendent la réplique « sans permission »"),
      field("Distance de salut (blocs, 0 = jamais)", radius),
      field("Délai avant de resaluer le même joueur (secondes)", cooldown)),
    el("div", { class: "checks" },
      el("label", { class: "check" }, look, "Le PNJ tourne la tête vers les joueurs"),
      el("label", { class: "check" }, particles, "Notes de musique au-dessus de sa tête quand il parle"))));

  // Skin
  const fileInput = el("input", { type: "file", accept: "image/png", hidden: true });
  const slim = el("input", { type: "checkbox", checked: m.slimSkin });
  const body = el("canvas", { class: "skin-body", width: 128, height: 256 });
  drawBody(body, m.skinFile ? skinUrl(m) : null, m.slimSkin);
  slim.addEventListener("change", () => { m.slimSkin = slim.checked; markDirty(); drawBody(body, m.skinFile ? skinUrl(m) : null, m.slimSkin); });
  fileInput.addEventListener("change", () => uploadSkin(fileInput.files[0], slim.checked));
  const skinCard = el("div", { class: "card" },
    el("h2", { text: "Skin" }),
    el("div", { class: "skin-box" },
      body,
      el("div", { class: "skin-actions" },
        el("button", { class: "btn", onclick: () => fileInput.click() }, m.skinFile ? "Changer le skin (PNG)" : "Envoyer un skin (PNG)"),
        m.skinFile ? el("button", { class: "btn ghost small", onclick: removeSkin }, "Retirer le skin") : null,
        el("label", { class: "check" }, slim, "Bras fins (modèle « Alex »)"),
        el("p", { class: "muted small-text" },
          "Fichier PNG 64×64 (tu peux aussi le glisser sur cette carte). Il est signé par MineSkin via FancyNpcs : le skin met quelques secondes à apparaître en jeu."),
        fileInput)));
  skinCard.addEventListener("dragover", (e) => { e.preventDefault(); skinCard.classList.add("over"); });
  skinCard.addEventListener("dragleave", () => skinCard.classList.remove("over"));
  skinCard.addEventListener("drop", (e) => { e.preventDefault(); skinCard.classList.remove("over"); uploadSkin(e.dataTransfer.files[0], slim.checked); });
  content.append(skinCard);

  // Dialogues
  const dialogues = el("div", { class: "card" },
    el("div", { class: "row-between wrap" },
      el("h2", { text: "Répliques" }),
      el("label", { class: "inline-field" }, "Tester sur :", el("select", { id: "test-player" }))),
    el("p", { class: "muted", style: "margin-top:0" },
      "À chaque moment, le marchand dit une réplique tirée au hasard (jamais deux fois la même d'affilée). Chaque réplique peut avoir un texte (chat) et/ou une voix. ",
      el("code", { text: "{player}" }), " = pseudo du joueur. L'aperçu montre le message tel qu'il apparaîtra dans le chat."),
    el("p", { class: "muted small-text", style: "margin-bottom:10px" },
      "🕐 / 🌦️ : une réplique peut être réservée à un moment de la journée (matin, jour, soir, nuit) ou à une météo. ",
      "Quand une réplique spéciale correspond au moment, le marchand la choisit en priorité. ",
      "Les répliques surlignées sont celles qui seraient dites en ce moment."),
    el("div", { id: "now-box", class: "now-box" }));
  for (const t of state.triggers) dialogues.append(renderTrigger(t));
  content.append(dialogues);
  renderPlayersSelect();
  renderNow();

  // Barre d'enregistrement
  const save = el("button", { id: "save-btn", class: "btn primary", onclick: saveMerchant, disabled: !dirty }, "Enregistrer");
  const cancel = el("button", { class: "btn ghost", onclick: () => { dirty = false; openMerchant(m.id); } }, "Annuler");
  content.append(el("div", { class: "save-bar" + (dirty ? " dirty" : "") },
    el("span", { class: "unsaved", text: "Modifications non enregistrées · Ctrl+S" }), cancel, save));

  refreshEditorPreviews();
}

function renderStats() {
  const card = $("#stats-card");
  if (!card || !draft) return;
  const m = state.merchants.find((x) => x.id === draft.id);
  const s = (m && m.stats) || {};
  const stat = (label, value, cls = "") => el("div", { class: "stat" }, el("b", { class: cls, text: value }), el("span", { text: label }));
  card.replaceChildren(
    el("div", { class: "row-between" },
      el("h2", { text: "Activité" }),
      el("button", { class: "btn ghost small", onclick: resetStats }, "Remettre à zéro")),
    el("div", { class: "stats" },
      stat("ouvertures", String(s.opens || 0)),
      stat("achats", String(s.buys || 0)),
      stat("dépensé par les joueurs", fmtMoney(s.spent), "money"),
      stat("ventes", String(s.sells || 0)),
      stat("gagné par les joueurs", fmtMoney(s.earned), "money"),
      stat("refus faute d'argent", String(s.noMoney || 0))),
    el("p", { class: "muted small-text", text: "Dernière activité : " + fmtDate(s.lastActivity) }));
}

function renderPlayersSelect() {
  const sel = $("#test-player");
  if (!sel) return;
  const players = state.players || [];
  if (testPlayer && !players.includes(testPlayer)) testPlayer = "";
  sel.replaceChildren(
    el("option", { value: "", text: players.length ? "le joueur le plus proche" : "personne n'est connecté" }),
    ...players.map((p) => el("option", { value: p, text: p, selected: p === testPlayer })));
  sel.onchange = () => { testPlayer = sel.value; };
}

function soundOptions(selected) {
  const opts = [el("option", { value: "", text: "— pas de voix —" })];
  for (const s of state.sounds) opts.push(el("option", { value: s.id, text: `${s.name} (${fmtDur(s.duration)})`, selected: s.id === selected }));
  return opts;
}

const TIME_ICONS = { ANY: "🕐", MORNING: "🌅", DAY: "☀️", EVENING: "🌇", NIGHT: "🌙" };
const WEATHER_ICONS = { ANY: "🌦️", CLEAR: "🌤️", RAIN: "🌧️", THUNDER: "⛈️" };

function conditionOptions(list, selected, icons) {
  return (list || []).map((c) => el("option", {
    value: c.id, selected: c.id === selected,
    text: `${icons[c.id] || ""} ${c.label}${c.range ? ` (${c.range.replace("-", "h → ")}h)` : ""}`,
  }));
}

// La réplique peut-elle être dite en ce moment (heure et météo actuelles du monde du marchand) ?
function lineActiveNow(line) {
  const m = state.merchants.find((x) => x.id === draft?.id);
  const now = m && m.now;
  if (!now) return false;
  const timeOk = !line.time || line.time === "ANY" || now.times.includes(line.time);
  const w = line.weather || "ANY";
  const weatherOk = w === "ANY" || (w === "CLEAR" && now.weather === "CLEAR")
    || (w === "RAIN" && now.weather !== "CLEAR") || (w === "THUNDER" && now.weather === "THUNDER");
  return timeOk && weatherOk && (line.time !== "ANY" || w !== "ANY");
}

function renderNow() {
  const box = $("#now-box");
  if (!box || !draft) return;
  const m = state.merchants.find((x) => x.id === draft.id);
  const now = m && m.now;
  if (!now) { box.textContent = "Place le marchand en jeu pour voir l'heure et la météo de son monde."; return; }
  const label = (list, id) => (list.find((c) => c.id === id) || {}).label || id;
  const times = now.times.map((t) => `${TIME_ICONS[t]} ${label(state.conditions.times, t)}`).join(" + ") || "—";
  box.replaceChildren("En ce moment en jeu : ", el("b", { text: now.clock }), ` · ${times} · ${WEATHER_ICONS[now.weather]} ${label(state.conditions.weathers, now.weather)}`);
}

function renderTrigger(t) {
  const lines = draft.lines[t.id];
  const box = el("div", { class: "lines" });
  const count = el("span", { class: "count" });
  const redraw = () => {
    box.replaceChildren();
    count.textContent = lines.length ? ` · ${lines.length} réplique${lines.length > 1 ? "s" : ""}` : "";
    if (!lines.length) box.append(el("div", { class: "empty-lines", text: "Rien à dire pour ce moment." }));
    lines.forEach((line, i) => {
      const row = $("#tpl-line").content.firstElementChild.cloneNode(true);
      const text = $(".line-text", row);
      const sound = $(".line-sound", row);
      const time = $(".line-time", row);
      const weather = $(".line-weather", row);
      text.value = line.text || "";
      sound.replaceChildren(...soundOptions(line.sound));
      line.time = line.time || "ANY";
      line.weather = line.weather || "ANY";
      time.replaceChildren(...conditionOptions(state.conditions.times, line.time, TIME_ICONS));
      weather.replaceChildren(...conditionOptions(state.conditions.weathers, line.weather, WEATHER_ICONS));
      const flag = () => {
        time.classList.toggle("set", line.time !== "ANY");
        weather.classList.toggle("set", line.weather !== "ANY");
        row.classList.toggle("now", lineActiveNow(line));
      };
      flag();
      row.addEventListener("refresh-now", flag);
      text.addEventListener("input", () => { line.text = text.value; markDirty(); refreshEditorPreviews(); });
      sound.addEventListener("change", () => { line.sound = sound.value || null; markDirty(); });
      time.addEventListener("change", () => { line.time = time.value; markDirty(); flag(); });
      weather.addEventListener("change", () => { line.weather = weather.value; markDirty(); flag(); });
      $(".play", row).addEventListener("click", () => line.sound ? play(line.sound) : toast("Aucune voix choisie"));
      $(".remove", row).addEventListener("click", () => { lines.splice(i, 1); markDirty(); redraw(); });
      box.append(row);
    });
    refreshEditorPreviews();
  };
  redraw();
  return el("div", { class: "trigger" },
    el("div", { class: "trigger-head" },
      el("h3", {}, t.label, count),
      el("div", { class: "row-gap" },
        el("button", { class: "btn small ghost", title: "Le marchand dit une de ces répliques en jeu", onclick: () => testTrigger(t.id) }, "🔊 Tester en jeu"),
        el("button", { class: "btn small", onclick: () => { lines.push({ text: "", sound: null }); markDirty(); redraw(); $(".line:last-child .line-text", box)?.focus(); } }, "+ Réplique"))),
    box);
}

async function testTrigger(trigger) {
  if (dirty) {
    toast("Enregistre d'abord tes modifications pour les tester", true);
    return;
  }
  try {
    const res = await api("POST", `merchants/${encodeURIComponent(draft.id)}/test`, { trigger, player: testPlayer });
    toast(`${res.player} a entendu : ${stripTags(res.text) || "(voix seule)"}`);
  } catch (err) { toast(err.message, true); }
}

async function saveMerchant() {
  const m = draft;
  if (!m) return;
  const lines = {};
  for (const [k, list] of Object.entries(m.lines)) {
    lines[k] = list.filter((l) => (l.text && l.text.trim()) || l.sound)
      .map((l) => ({ text: l.text || "", sound: l.sound || null, time: l.time || "ANY", weather: l.weather || "ANY" }));
  }
  try {
    await api("PUT", "merchants/" + encodeURIComponent(m.id), {
      displayName: m.displayName, section: m.section, lookAtPlayer: m.lookAtPlayer, slimSkin: m.slimSkin,
      talkParticles: m.talkParticles !== false, permission: m.permission || "",
      greetRadius: m.greetRadius, greetCooldownSeconds: m.greetCooldownSeconds, lines,
    });
    dirty = false;
    await refresh();
    toast("Enregistré ✔ Le PNJ a été mis à jour en jeu.");
  } catch (err) { toast(err.message, true); }
}

document.addEventListener("keydown", (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "s") {
    e.preventDefault();
    if (dirty) saveMerchant();
  }
});

async function deleteMerchant() {
  if (!confirm(`Supprimer définitivement le marchand « ${draft.id} » et son PNJ ?`)) return;
  try {
    await api("DELETE", "merchants/" + encodeURIComponent(draft.id));
    dirty = false; selectedId = null; draft = null;
    store.set("mt-selected", null);
    await refresh();
    toast("Marchand supprimé");
  } catch (err) { toast(err.message, true); }
}

async function duplicateMerchant() {
  if (dirty) return toast("Enregistre d'abord tes modifications", true);
  const id = prompt("Identifiant de la copie :", draft.id + "_2");
  if (!id) return;
  try {
    const newId = id.trim().toLowerCase();
    await api("POST", `merchants/${encodeURIComponent(draft.id)}/duplicate`, { id: newId });
    selectedId = newId;
    await refresh();
    toast("Copie créée ! Place-la en jeu avec /mt place " + newId);
  } catch (err) { toast(err.message, true); }
}

async function resetStats() {
  if (!confirm("Remettre les statistiques de ce marchand à zéro ?")) return;
  try {
    await api("POST", `merchants/${encodeURIComponent(draft.id)}/reset-stats`);
    const m = state.merchants.find((x) => x.id === draft.id);
    if (m) m.stats = {};
    renderStats();
    toast("Statistiques remises à zéro");
  } catch (err) { toast(err.message, true); }
}

async function uploadSkin(file, slim) {
  if (!file) return;
  if (file.type && file.type !== "image/png") return toast("Le skin doit être un fichier PNG", true);
  if (dirty && !confirm("Envoyer le skin enregistrera aussi tes modifications en cours. Continuer ?")) return;
  try {
    if (dirty) await saveMerchant();
    await api("POST", `merchants/${encodeURIComponent(draft.id)}/skin?slim=${slim}`, file, { "Content-Type": "image/png" });
    dirty = false;
    await refresh();
    toast("Skin envoyé ! Il apparaîtra en jeu d'ici quelques secondes.");
  } catch (err) { toast(err.message, true); }
}

async function removeSkin() {
  try {
    await api("DELETE", `merchants/${encodeURIComponent(draft.id)}/skin`);
    await refresh();
    toast("Skin retiré");
  } catch (err) { toast(err.message, true); }
}

window.addEventListener("beforeunload", (e) => { if (dirty) { e.preventDefault(); e.returnValue = ""; } });

// ------------------------------------------------------------------ voix
function usage(soundId) {
  const names = [];
  for (const m of state.merchants) {
    if (Object.values(m.lines || {}).some((list) => list.some((l) => l.sound === soundId))) names.push(stripTags(m.displayName) || m.id);
  }
  return names;
}

// Forme d'onde (calculée une fois par voix, puis mise en cache).
const waveforms = new Map();
async function drawWaveform(canvas, soundId) {
  try {
    if (!waveforms.has(soundId)) {
      waveforms.set(soundId, (async () => {
        const buf = await fetch(soundUrl(soundId)).then((r) => r.arrayBuffer());
        const AC = window.AudioContext || window.webkitAudioContext;
        const ctx = new AC();
        try {
          const audio = await ctx.decodeAudioData(buf);
          const data = audio.getChannelData(0);
          const bars = 60, step = Math.max(1, Math.floor(data.length / bars)), peaks = [];
          for (let b = 0; b < bars; b++) {
            let max = 0;
            for (let i = b * step; i < Math.min(data.length, (b + 1) * step); i++) max = Math.max(max, Math.abs(data[i]));
            peaks.push(max);
          }
          return peaks;
        } finally { ctx.close(); }
      })());
    }
    const peaks = await waveforms.get(soundId);
    const g = canvas.getContext("2d");
    const w = canvas.width, h = canvas.height, bw = w / peaks.length;
    g.clearRect(0, 0, w, h);
    g.fillStyle = getComputedStyle(document.documentElement).getPropertyValue("--accent").trim() || "#f0b232";
    peaks.forEach((p, i) => { const bh = Math.max(1, p * h); g.fillRect(i * bw + 0.5, (h - bh) / 2, Math.max(1, bw - 1), bh); });
  } catch { /* forme d'onde facultative */ }
}

function renderVoices() {
  const tbody = $("#voice-list");
  tbody.replaceChildren();
  $("#voice-empty").classList.toggle("hidden", state.sounds.length > 0);
  $("#voice-count").textContent = state.sounds.length ? state.sounds.length + " voix" : "";
  for (const s of state.sounds) {
    const name = el("input", { type: "text", value: s.name, maxlength: 80 });
    name.addEventListener("change", async () => {
      try { await api("PATCH", "sounds/" + encodeURIComponent(s.id), { name: name.value }); await refresh(); toast("Renommée"); }
      catch (err) { toast(err.message, true); }
    });
    const wave = el("canvas", { class: "wave", width: 180, height: 32 });
    drawWaveform(wave, s.id);
    const used = usage(s.id);
    tbody.append(el("tr", {},
      el("td", {}, el("button", { class: "btn icon play", title: "Écouter", onclick: () => play(s.id) }, "▶")),
      el("td", { class: "name" }, name),
      el("td", {}, wave),
      el("td", { text: fmtDur(s.duration) }),
      el("td", { class: "muted", text: used.length ? used.join(", ") : "—" }),
      el("td", {}, el("button", { class: "btn icon danger", title: "Supprimer", onclick: () => deleteVoice(s, used) }, "✕"))));
  }
}

async function deleteVoice(s, used) {
  const warn = used.length ? `\nElle est utilisée par : ${used.join(", ")}. Les répliques garderont leur texte.` : "";
  if (!confirm(`Supprimer la voix « ${s.name} » ?${warn}`)) return;
  try {
    await api("DELETE", "sounds/" + encodeURIComponent(s.id));
    await refresh();
    toast("Voix supprimée");
  } catch (err) { toast(err.message, true); }
}

// Conversion de n'importe quel fichier audio en OGG Vorbis MONO, dans le navigateur.
let encoderPromise = null;
function getEncoder() {
  if (!encoderPromise) encoderPromise = WasmMediaEncoder.createEncoder("audio/ogg", "vendor/ogg.wasm");
  return encoderPromise;
}

async function toMonoOgg(arrayBuffer, { trim = true, normalize = true } = {}) {
  const AC = window.AudioContext || window.webkitAudioContext;
  const ctx = new AC();
  let audio;
  try { audio = await ctx.decodeAudioData(arrayBuffer); }
  finally { ctx.close(); }
  if (audio.duration > 120) throw new Error("Fichier trop long (2 minutes max)");

  // Mélange de tous les canaux en un seul (mono).
  let mono = new Float32Array(audio.length);
  for (let c = 0; c < audio.numberOfChannels; c++) {
    const data = audio.getChannelData(c);
    for (let i = 0; i < data.length; i++) mono[i] += data[i] / audio.numberOfChannels;
  }

  // Coupe les silences au début et à la fin (en gardant 60 ms de marge).
  if (trim) {
    const threshold = 0.01, pad = Math.floor(audio.sampleRate * 0.06);
    let start = 0, end = mono.length - 1;
    while (start < mono.length && Math.abs(mono[start]) < threshold) start++;
    while (end > start && Math.abs(mono[end]) < threshold) end--;
    if (end > start) mono = mono.slice(Math.max(0, start - pad), Math.min(mono.length, end + pad));
  }
  if (mono.length < audio.sampleRate * 0.05) throw new Error("Le fichier semble vide ou silencieux");

  // Égalise le volume : le pic est amené à 90 %.
  if (normalize) {
    let peak = 0;
    for (let i = 0; i < mono.length; i++) peak = Math.max(peak, Math.abs(mono[i]));
    if (peak > 0.001) {
      const gain = Math.min(8, 0.9 / peak);
      for (let i = 0; i < mono.length; i++) mono[i] *= gain;
    }
  }

  const encoder = await getEncoder();
  encoder.configure({ channels: 1, sampleRate: audio.sampleRate, vbrQuality: 5 });
  const parts = [];
  const chunk = audio.sampleRate; // 1 seconde à la fois
  for (let off = 0; off < mono.length; off += chunk) {
    const out = encoder.encode([mono.subarray(off, Math.min(off + chunk, mono.length))]);
    if (out.length) parts.push(out.slice());
  }
  const last = encoder.finalize();
  if (last.length) parts.push(last.slice());
  return new Blob(parts, { type: "audio/ogg" });
}

async function addVoice(blobOrFile, name) {
  const item = el("li", {}, el("span", { text: name }), el("span", { class: "muted", text: "Conversion en OGG mono…" }));
  $("#upload-queue").prepend(item);
  const status = item.lastChild;
  try {
    const ogg = await toMonoOgg(await blobOrFile.arrayBuffer(), {
      trim: $("#opt-trim").checked, normalize: $("#opt-normalize").checked,
    });
    status.textContent = "Envoi…";
    await api("POST", "sounds", ogg, { "Content-Type": "audio/ogg", "X-Name": encodeURIComponent(name) });
    status.textContent = "Ajoutée ✔";
    status.className = "ok";
  } catch (err) {
    status.textContent = err.message || "Échec";
    status.className = "err";
  }
}

async function addFiles(files) {
  for (const f of files) await addVoice(f, f.name.replace(/\.[^.]+$/, ""));
  await refresh();
}

const drop = $("#dropzone");
$("#voice-input").addEventListener("change", (e) => { addFiles([...e.target.files]); e.target.value = ""; });
drop.addEventListener("dragover", (e) => { e.preventDefault(); drop.classList.add("over"); });
drop.addEventListener("dragleave", () => drop.classList.remove("over"));
drop.addEventListener("drop", (e) => { e.preventDefault(); drop.classList.remove("over"); addFiles([...e.dataTransfer.files]); });

// Enregistrement au micro
let recorder = null;
$("#record-btn").addEventListener("click", async () => {
  const btn = $("#record-btn");
  if (recorder) { recorder.stop(); return; }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const chunks = [];
    recorder = new MediaRecorder(stream);
    recorder.ondataavailable = (e) => chunks.push(e.data);
    recorder.onstop = async () => {
      stream.getTracks().forEach((t) => t.stop());
      recorder = null;
      btn.textContent = "⏺ Enregistrer au micro";
      btn.classList.remove("record-btn-active");
      $("#record-status").textContent = "";
      const name = prompt("Nom de cette voix :", "Enregistrement " + new Date().toLocaleTimeString("fr-FR"));
      if (name === null) return;
      await addVoice(new Blob(chunks), name || "Enregistrement");
      await refresh();
    };
    recorder.start();
    btn.textContent = "⏹ Arrêter";
    btn.classList.add("record-btn-active");
    $("#record-status").textContent = "Enregistrement en cours…";
  } catch (err) {
    toast("Micro indisponible (le site doit être en HTTPS ou en localhost pour utiliser le micro)", true);
  }
});

// ------------------------------------------------------------------ démarrage
(async () => {
  if (!token) return showLogin();
  try { await start(); } catch { showLogin(); }
})();
