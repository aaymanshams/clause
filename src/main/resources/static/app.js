// Minimal vanilla-JS client for the ClauseIQ REST API. The JWT lives in localStorage for demo purposes.
const state = { token: null, user: null, chatContractId: null, currentContractId: null, poll: null };

const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function storage(fn) { try { return fn(); } catch { return null; } }

async function api(path, options = {}) {
    const headers = options.headers || {};
    if (state.token) headers.Authorization = `Bearer ${state.token}`;
    if (options.json !== undefined) {
        headers["Content-Type"] = "application/json";
        options.body = JSON.stringify(options.json);
    }
    const res = await fetch(path, { ...options, headers });
    if (res.status === 401 && state.token) { logout(); throw new Error("Session expired, please log in again"); }
    if (res.status === 204) return null;
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.message || `Request failed (${res.status})`);
    return body;
}

function show(view) {
    document.querySelectorAll(".view").forEach((v) => (v.hidden = v.id !== `view-${view}`));
    document.querySelectorAll(".navlink").forEach((b) => b.classList.toggle("active", b.dataset.view === view));
    clearInterval(state.poll);
    if (view === "dashboard") { loadDashboard(); state.poll = setInterval(loadDashboard, 4000); }
    if (view === "chat") {
        $("#chat-scope").textContent = state.chatContractId
            ? "Scoped to the selected contract. Answers come only from that document, with sources."
            : "Answers come only from your organization's contracts, with sources.";
    }
}

function setSession(auth) {
    state.token = auth.token;
    state.user = auth.user;
    storage(() => localStorage.setItem("clauseiq", JSON.stringify(auth)));
    enterApp();
}

function enterApp() {
    $("#nav").hidden = false;
    $("#who").hidden = false;
    $("#who-text").textContent = `${state.user.email} · ${state.user.tenantName} (${state.user.role})`;
    show("dashboard");
}

function logout() {
    state.token = null;
    state.user = null;
    storage(() => localStorage.removeItem("clauseiq"));
    $("#nav").hidden = true;
    $("#who").hidden = true;
    show("auth");
}

// ---------- auth
document.querySelectorAll(".tab").forEach((tab) => tab.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t === tab));
    $("#form-login").hidden = tab.dataset.tab !== "login";
    $("#form-register").hidden = tab.dataset.tab !== "register";
    $("#auth-error").textContent = "";
}));

async function submitAuth(form, path) {
    $("#auth-error").textContent = "";
    try { setSession(await api(path, { method: "POST", json: Object.fromEntries(new FormData(form)) })); }
    catch (e) { $("#auth-error").textContent = e.message; }
}
$("#form-login").addEventListener("submit", (e) => { e.preventDefault(); submitAuth(e.target, "/api/auth/login"); });
$("#form-register").addEventListener("submit", (e) => { e.preventDefault(); submitAuth(e.target, "/api/auth/register"); });
$("#logout").addEventListener("click", logout);

document.querySelectorAll("[data-view]").forEach((b) => b.addEventListener("click", () => {
    if (b.dataset.view === "chat") state.chatContractId = null;
    show(b.dataset.view);
}));

// ---------- dashboard
const badge = (value) => `<span class="badge s-${esc(value)}">${esc(value)}</span>`;

async function loadDashboard() {
    try {
        const [stats, contracts] = await Promise.all([api("/api/dashboard"), api("/api/contracts")]);
        $("#s-contracts").textContent = stats.contracts;
        $("#s-processed").textContent = stats.processed;
        $("#s-processing").textContent = stats.processing;
        $("#s-high").textContent = stats.highRisks;
        $("#provider").textContent = `· AI provider: ${stats.aiProvider}`;
        $("#empty").hidden = contracts.length > 0;
        $("#contracts").innerHTML = contracts.map((c) => `
            <tr data-id="${c.id}">
                <td>${esc(c.name)}</td>
                <td>${badge(c.status)}</td>
                <td>${badge(c.extractionStatus)}</td>
                <td>${c.pageCount ?? "–"}</td>
                <td>${c.riskCount ? `<span class="sev-${esc(c.highestRisk)}">${c.riskCount} (${esc(c.highestRisk)})</span>` : "–"}</td>
                <td>${new Date(c.uploadedAt).toLocaleString()}</td>
            </tr>`).join("");
        document.querySelectorAll("#contracts tr").forEach((tr) => tr.addEventListener("click", () => openContract(tr.dataset.id)));
    } catch (e) { $("#upload-msg").textContent = e.message; }
}

$("#form-upload").addEventListener("submit", async (e) => {
    e.preventDefault();
    const button = e.target.querySelector("button");
    button.disabled = true;
    $("#upload-msg").textContent = "Uploading…";
    try {
        const c = await api("/api/contracts/upload", { method: "POST", body: new FormData(e.target) });
        $("#upload-msg").textContent = `Uploaded ${c.name}. Processing in the background…`;
        e.target.reset();
        loadDashboard();
    } catch (err) { $("#upload-msg").textContent = err.message; }
    finally { button.disabled = false; }
});

// ---------- contract detail
const FIELDS = [
    ["Parties", (d) => (d.parties || []).join(", ")],
    ["Effective date", (d) => d.effectiveDate],
    ["Expiration / renewal date", (d) => d.expirationDate],
    ["Renewal", (d) => d.renewalPeriod],
    ["Termination notice", (d) => (d.terminationNoticeDays != null ? `${d.terminationNoticeDays} days` : null)],
    ["Governing law", (d) => d.governingLaw],
    ["Liability cap", (d) => d.liabilityCap],
    ["Payment terms", (d) => d.paymentTerms],
];

async function openContract(id) {
    state.currentContractId = id;
    show("contract");
    try {
        const d = await api(`/api/contracts/${id}`);
        $("#c-name").textContent = d.contract.name;
        $("#c-status").innerHTML = `${badge(d.contract.status)} extraction ${badge(d.contract.extractionStatus)}
            · ${d.contract.pageCount ?? "?"} pages · ${d.contract.chunkCount ?? "?"} chunks`;
        $("#c-message").hidden = !d.message;
        $("#c-message").textContent = d.message || "";
        $("#c-fields").innerHTML = d.extractedData
            ? FIELDS.map(([label, get]) => `<dt>${label}</dt><dd>${esc(get(d.extractedData) || "Not stated")}</dd>`).join("")
            : `<dd class="muted">No extracted data yet.</dd>`;
        $("#c-risks").innerHTML = d.risks.length
            ? d.risks.map((r) => `<li><span class="badge sev-${esc(r.severity)}">${esc(r.severity)}</span><span>${esc(r.description)}</span></li>`).join("")
            : `<li class="muted">${d.extractedData ? "No risks detected by the current rules." : "Risk analysis runs after extraction."}</li>`;
    } catch (e) { $("#c-name").textContent = e.message; }
}

$("#c-ask").addEventListener("click", () => { state.chatContractId = state.currentContractId; show("chat"); });
$("#c-reprocess").addEventListener("click", async () => {
    try { await api(`/api/contracts/${state.currentContractId}/reprocess`, { method: "POST" }); show("dashboard"); }
    catch (e) { $("#c-message").hidden = false; $("#c-message").textContent = e.message; }
});

// ---------- chat + search
const sourceRef = (s) => `${esc(s.contractName)} · ${s.pageNumber != null ? `page ${s.pageNumber}` : `section ${s.chunkIndex + 1}`}`;

$("#form-chat").addEventListener("submit", async (e) => {
    e.preventDefault();
    const button = e.target.querySelector("button");
    button.disabled = true;
    $("#chat-result").hidden = false;
    $("#chat-answer").textContent = "Thinking…";
    $("#chat-sources").innerHTML = "";
    try {
        const body = { question: new FormData(e.target).get("question") };
        if (state.chatContractId) body.contractId = Number(state.chatContractId);
        const r = await api("/api/chat", { method: "POST", json: body });
        $("#chat-answer").textContent = r.answer;
        $("#chat-sources").innerHTML = r.sources.length
            ? r.sources.map((s) => `<li><div class="ref">[${esc(s.label)}] ${sourceRef(s)}</div><div class="excerpt">${esc(s.excerpt)}</div></li>`).join("")
            : `<li class="muted">No sources.</li>`;
    } catch (err) { $("#chat-answer").textContent = err.message; }
    finally { button.disabled = false; }
});

$("#form-search").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
        const r = await api("/api/search", { method: "POST", json: { query: new FormData(e.target).get("query") } });
        $("#search-results").innerHTML = r.results.length
            ? r.results.map((h) => `<li><div class="ref">${sourceRef(h)} · similarity ${h.similarity}</div><div class="excerpt">${esc(h.text)}</div></li>`).join("")
            : `<li class="muted">No relevant passages found.</li>`;
    } catch (err) { $("#search-results").innerHTML = `<li class="error">${esc(err.message)}</li>`; }
});

// ---------- boot
const saved = storage(() => JSON.parse(localStorage.getItem("clauseiq")));
if (saved && saved.token) { state.token = saved.token; state.user = saved.user; enterApp(); } else { show("auth"); }
