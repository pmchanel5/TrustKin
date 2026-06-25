const app = document.getElementById("app");
const toast = document.getElementById("toast");

let bootstrap = null;
let circle = null;
let activeTab = "feed";
let setupMode = "host";
let setupAvatar = "";
let setupDraft = { nickname: "", relay_url: "", invite_token: "" };
let postImage = "";
let lastError = "";
let refreshTimer = null;
const SAFE_IMAGE_RE = /^data:image\/(?:jpeg|jpg|png|webp);base64,[A-Za-z0-9+/]+={0,2}$/;

const noteStarters = [
  "You can do it. One clean step now.",
  "Stay steady. I believe in you.",
  "Do the next right thing. No need to answer.",
];

async function api(path, body) {
  const options = body === undefined
    ? { headers: { Accept: "application/json" } }
    : {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(body),
      };
  const response = await fetch(path, options);
  let payload = {};
  try {
    payload = await response.json();
  } catch {
    payload = {};
  }
  if (!response.ok || payload.ok === false) {
    throw new Error(payload.error || "Something did not work.");
  }
  return payload;
}

async function loadBootstrap() {
  bootstrap = await api("/api/bootstrap");
  setupDraft.relay_url = bootstrap.settings.relay_url || "";
  setupDraft.invite_token = bootstrap.settings.invite_token || "";
  if (bootstrap.settings.connection_mode) setupMode = bootstrap.settings.connection_mode;
}

async function refreshCircle(renderAfter = true) {
  if (!bootstrap?.settings?.user_id) return;
  try {
    if (bootstrap.settings.is_host || bootstrap.settings.hosting_enabled || ["starting", "checking"].includes(bootstrap.settings.tunnel?.status)) {
      await loadBootstrap();
    }
    if (!bootstrap.settings.connection_mode) {
      if (renderAfter && shouldRenderNow()) render();
      return;
    }
    circle = await api("/api/state");
    lastError = "";
  } catch (error) {
    lastError = error.message;
  }
  if (renderAfter && shouldRenderNow()) render();
}

function shouldRenderNow() {
  const el = document.activeElement;
  if (!el || el === document.body) return true;
  if (["BUTTON", "A"].includes(el.tagName)) return true;
  if (el.tagName === "INPUT" && (el.readOnly || el.disabled)) return true;
  return false;
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 2600);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function safeImageSrc(value) {
  const src = String(value || "");
  return SAFE_IMAGE_RE.test(src) ? src : "";
}

function parseInviteValue(value) {
  const raw = String(value || "").trim();
  if (!raw) return { relayUrl: "", token: "" };
  try {
    const url = new URL(raw.includes("://") ? raw : `http://${raw}`);
    const hash = new URLSearchParams(url.hash.replace(/^#/, ""));
    const query = new URLSearchParams(url.search);
    const token = hash.get("token") || query.get("token") || "";
    if (url.pathname.replace(/\/+$/, "") === "/join") url.pathname = "";
    url.hash = "";
    url.search = "";
    return { relayUrl: url.toString().replace(/\/$/, ""), token };
  } catch {
    return { relayUrl: raw, token: "" };
  }
}

function tunnelLabel(settings) {
  const tunnel = settings?.tunnel || {};
  if (settings?.public_url) return "Internet invite ready";
  if (!settings?.hosting_enabled) return "Start hosting to create a public invite.";
  if (!tunnel.available) return "Internet invite unavailable";
  if (tunnel.status === "checking") return "Internet invite checking";
  if (tunnel.status === "failed") return "Internet invite failed";
  if (tunnel.status === "missing") return "Cloudflare tunnel helper is missing";
  return `Internet invite ${tunnel.status || "starting"}`;
}

function initials(name) {
  const parts = String(name || "B").trim().split(/\s+/).filter(Boolean);
  return (parts[0]?.[0] || "B").toUpperCase() + (parts[1]?.[0] || "").toUpperCase();
}

function avatarHtml(profile, className = "avatar") {
  const nick = profile?.nickname || "Brother";
  const avatar = safeImageSrc(profile?.avatar);
  if (avatar) {
    return `<span class="${className}"><img src="${escapeHtml(avatar)}" alt=""></span>`;
  }
  return `<span class="${className}">${escapeHtml(initials(nick))}</span>`;
}

function profileMap() {
  const map = new Map();
  for (const profile of circle?.profiles || []) map.set(profile.id, profile);
  return map;
}

function currentProfile() {
  const id = bootstrap?.settings?.user_id;
  return profileMap().get(id) || { id, nickname: "You", avatar: "" };
}

function timeAgo(value) {
  if (!value) return "";
  const millis = typeof value === "number" ? value * 1000 : Date.parse(value);
  if (!Number.isFinite(millis)) return "";
  const seconds = Math.max(1, Math.round((Date.now() - millis) / 1000));
  if (seconds < 60) return "now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

function render() {
  if (!bootstrap) {
    app.innerHTML = `<div class="setup-wrap"><div class="empty">Starting Brotherhood...</div></div>`;
    return;
  }
  if (!bootstrap.settings.has_profile || bootstrap.settings.needs_connection) {
    renderSetup();
    return;
  }
  renderDashboard();
}

function renderSetup() {
  const settings = bootstrap.settings;
  const inviteUrl = settings.host_invite_url || "";
  const inviteToken = settings.host_invite_token || "";
  const hasProfile = Boolean(settings.has_profile);
  const hostActive = settings.connection_mode === "host" && settings.hosting_enabled;
  const tunnel = settings.tunnel || {};
  const hostStatus = `${tunnelLabel(settings)}${tunnel.error ? `: ${tunnel.error}` : ""}`;
  const profileName = settings.nickname || setupDraft.nickname || "";
  const profileAvatar = safeImageSrc(setupAvatar || settings.avatar || "");
  app.innerHTML = `
    <main class="setup-wrap">
      <section class="setup">
        <div class="setup-side">
          <div>
            <div class="setup-mark">BH</div>
            <h1>Brotherhood</h1>
            <p>Private accountability for men who want their friends close enough to cheer, not close enough to spy.</p>
          </div>
        </div>
        <div class="setup-main">
          <h2>${hasProfile ? "Choose Circle" : "Create Profile"}</h2>
          <p>${hasProfile ? "Your profile is ready. Pick Host or Join for this session." : "Your nickname is required. The profile image can wait."}</p>
          <form id="setupForm" class="setup-form">
            ${hasProfile ? `
              <div class="profile-card">
                ${avatarHtml({ nickname: profileName, avatar: profileAvatar }, "avatar-large")}
                <div>
                  <strong>${escapeHtml(profileName || "Brother")}</strong>
                  <span class="muted small">Profile kept from this computer</span>
                </div>
              </div>
            ` : `
              <div class="avatar-row">
                <span id="setupAvatarPreview">${profileAvatar ? `<span class="avatar-large"><img src="${escapeHtml(profileAvatar)}" alt=""></span>` : `<span class="avatar-large">BH</span>`}</span>
                <label>
                  Profile image
                  <input id="setupAvatarInput" type="file" accept="image/*">
                </label>
              </div>
              <label>
                Nickname
                <input id="setupNickname" required maxlength="32" autocomplete="nickname" value="${escapeHtml(setupDraft.nickname)}">
              </label>
            `}
            <div>
              <div class="mode-toggle" role="group" aria-label="Connection mode">
                <button type="button" data-setup-mode="host" class="${setupMode === "host" ? "active" : ""}">Host circle</button>
                <button type="button" data-setup-mode="join" class="${setupMode === "join" ? "active" : ""}">Join circle</button>
              </div>
            </div>
            <div id="hostFields" class="${setupMode === "host" ? "" : "hidden"}">
              <div class="copy-row">
                <label>
                  Invite URL
                  <input value="${escapeHtml(inviteUrl)}" placeholder="${hostActive ? "Waiting for trycloudflare.com..." : "Continue to start hosting"}" readonly>
                </label>
                <button type="button" data-copy="${escapeHtml(inviteUrl)}" ${inviteUrl ? "" : "disabled"}>Copy URL</button>
              </div>
              <div class="copy-row">
                <label>
                  Invite token
                  <input value="${escapeHtml(inviteToken)}" placeholder="${hostActive ? "Creating token..." : "Available after hosting starts"}" readonly>
                </label>
                <button type="button" data-copy="${escapeHtml(inviteToken)}" ${inviteToken ? "" : "disabled"}>Copy token</button>
              </div>
              <p class="muted small">${escapeHtml(hostStatus)}</p>
            </div>
            <div id="joinFields" class="join-fields ${setupMode === "join" ? "" : "hidden"}">
              <label>
                Invite URL
                <input id="setupRelay" placeholder="https://example.trycloudflare.com/join#token=..." value="${escapeHtml(setupDraft.relay_url)}">
              </label>
              <label>
                Invite token
                <input id="setupToken" maxlength="256" value="${escapeHtml(setupDraft.invite_token)}">
              </label>
            </div>
            <button class="primary" type="submit">${hasProfile ? (setupMode === "host" ? "Start hosting" : "Join circle") : "Enter"}</button>
          </form>
        </div>
      </section>
    </main>
  `;
  wireSetup();
}

function wireSetup() {
  document.querySelectorAll("[data-setup-mode]").forEach((button) => {
    button.addEventListener("click", () => {
      setupDraft.nickname = document.getElementById("setupNickname")?.value || "";
      setupDraft.relay_url = document.getElementById("setupRelay")?.value || setupDraft.relay_url;
      setupDraft.invite_token = document.getElementById("setupToken")?.value || setupDraft.invite_token;
      setupMode = button.dataset.setupMode;
      renderSetup();
    });
  });

  document.querySelectorAll("[data-copy]").forEach((button) => {
    button.addEventListener("click", () => copyText(button.dataset.copy || ""));
  });

  document.getElementById("setupAvatarInput")?.addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setupAvatar = await resizeImage(file, 300, 0.82);
    const avatar = safeImageSrc(setupAvatar);
    if (avatar) document.getElementById("setupAvatarPreview").innerHTML = `<span class="avatar-large"><img src="${escapeHtml(avatar)}" alt=""></span>`;
  });

  document.getElementById("setupForm")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const nickname = (document.getElementById("setupNickname")?.value || bootstrap.settings.nickname || "").trim();
    const avatar = setupAvatar || bootstrap.settings.avatar || "";
    const inviteValue = document.getElementById("setupRelay")?.value.trim() || "";
    const parsedInvite = parseInviteValue(inviteValue);
    const inviteToken = document.getElementById("setupToken")?.value.trim() || parsedInvite.token;
    try {
      await api("/api/connect", {
        mode: setupMode,
        invite_url: inviteValue,
        relay_url: parsedInvite.relayUrl,
        invite_token: inviteToken,
        share_activity: true,
      });
      const state = await api("/api/profile", { nickname, avatar });
      circle = state;
      await loadBootstrap();
      renderDashboard();
    } catch (error) {
      showToast(error.message);
    }
  });
}

function renderDashboard() {
  const me = currentProfile();
  const settings = bootstrap.settings;
  app.innerHTML = `
    <div class="shell">
      <header class="topbar">
        <div class="brand">
          ${avatarHtml(me)}
          <div>
            <h1>Brotherhood</h1>
            <p>${escapeHtml(me.nickname)}${lastError ? ` - ${escapeHtml(lastError)}` : ""}</p>
          </div>
        </div>
        <nav class="tabs" aria-label="Main views">
          ${tabButton("feed", "Feed")}
          ${tabButton("activity", "Activity")}
          ${tabButton("notes", "Notes")}
          ${tabButton("circle", "Circle")}
          <button type="button" class="warn" data-action="close-app">Close app</button>
        </nav>
      </header>
      <div class="layout">
        <main class="main">${renderMain()}</main>
        <aside class="side">${renderSidebar(settings, me)}</aside>
      </div>
    </div>
  `;
  wireDashboard();
}

function tabButton(id, label) {
  return `<button type="button" data-tab="${id}" class="${activeTab === id ? "active" : ""}">${label}</button>`;
}

function renderMain() {
  if (activeTab === "activity") return renderActivity();
  if (activeTab === "notes") return renderNotes();
  if (activeTab === "circle") return renderCircle();
  return renderFeed();
}

function renderSidebar(settings, me) {
  const hostUrl = settings.host_invite_url || "";
  const hostToken = settings.host_invite_token || "";
  const relayField = settings.is_host ? hostUrl : settings.relay_url;
  const shownToken = settings.is_host ? "" : settings.invite_token;
  const hostActive = settings.connection_mode === "host";
  const tunnel = settings.tunnel || {};
  const tunnelText = tunnelLabel(settings);
  return `
    <section class="panel">
      <div class="profile-card">
        ${avatarHtml(me, "avatar-large")}
        <div>
          <strong>${escapeHtml(me.nickname)}</strong>
          <span class="muted small">${settings.is_host ? "Hosting circle" : "Joined circle"}</span>
        </div>
      </div>
    </section>
    <section class="panel connection">
      <div class="section-head">
        <h2>Activity</h2>
        <span class="pill ${settings.share_activity ? "green" : "amber"}">${settings.share_activity ? "Sharing on" : "Paused"}</span>
      </div>
      <label class="switch-row">
        <span>Share my activity</span>
        <input id="shareActivity" type="checkbox" ${settings.share_activity ? "checked" : ""}>
      </label>
      <p class="muted small">Visible only after you allow a brother to see it.</p>
    </section>
    <section class="panel connection">
      <div class="section-head">
        <h2>Connection</h2>
        <span class="pill ${hostActive ? "green" : "blue"}">${hostActive ? "Host" : "Join"}</span>
      </div>
      <div class="mode-toggle">
        <button type="button" data-action="switch-host" class="${hostActive ? "active" : ""}">Host</button>
        <button type="button" data-action="switch-join" class="${!hostActive ? "active" : ""}">Join</button>
      </div>
      ${hostActive ? `
        <div class="connection">
          <label>
            Invite URL
            <input value="${escapeHtml(relayField || "")}" placeholder="Waiting for trycloudflare.com..." readonly>
          </label>
          <label>
            Invite token
            <input value="${escapeHtml(hostToken || "")}" readonly>
          </label>
          <p class="muted small">${escapeHtml(tunnelText)}${tunnel.error ? `: ${escapeHtml(tunnel.error)}` : ""}</p>
          <button type="button" data-copy="${escapeHtml(hostUrl)}" ${hostUrl ? "" : "disabled"}>Copy invite URL</button>
          <button type="button" data-copy="${escapeHtml(hostToken)}" ${hostToken ? "" : "disabled"}>Copy token</button>
        </div>
      ` : `
        <form id="connectionForm" class="connection">
          <label>
            Invite URL
            <input id="relayUrl" value="${escapeHtml(relayField || "")}">
          </label>
          <label>
            Invite token
            <input id="inviteToken" maxlength="256" value="${escapeHtml(shownToken || "")}">
          </label>
          <button type="submit">Save join info</button>
        </form>
      `}
    </section>
  `;
}

function renderFeed() {
  const posts = [...(circle?.posts || [])].reverse();
  const draftImage = safeImageSrc(postImage);
  return `
    <section class="section">
      <div class="section-head">
        <h2>Wins And Plans</h2>
        <span class="pill blue">${posts.length} posts</span>
      </div>
      <form id="postForm" class="feed-form">
        <select id="postKind">
          <option value="win">Done</option>
          <option value="plan">To do</option>
        </select>
        <textarea id="postText" maxlength="600" placeholder="What did you finish, or what are you going to face next?"></textarea>
        <label>
          Picture
          <input id="postImage" type="file" accept="image/*">
        </label>
        <div id="postImagePreview" class="${draftImage ? "" : "hidden"}">
          ${draftImage ? `<img class="post-image" src="${escapeHtml(draftImage)}" alt="">` : ""}
        </div>
        <div class="form-actions">
          <button type="button" class="soft ${draftImage ? "" : "hidden"}" data-action="clear-post-image">Remove picture</button>
          <button class="primary" type="submit">Post</button>
        </div>
      </form>
    </section>
    <section class="section">
      <div class="post-list">
        ${posts.length ? posts.map(renderPost).join("") : `<div class="empty">No wins or plans yet.</div>`}
      </div>
    </section>
  `;
}

function renderPost(post) {
  const profiles = profileMap();
  const author = profiles.get(post.author_id) || { nickname: "Brother" };
  const kind = post.kind === "plan" ? "To do" : "Done";
  const pillClass = post.kind === "plan" ? "amber" : "green";
  const comments = (circle?.comments || []).filter((comment) => comment.post_id === post.id);
  const image = safeImageSrc(post.image);
  return `
    <article class="post">
      <div class="post-head">
        <div class="person">
          ${avatarHtml(author)}
          <div>
            <strong>${escapeHtml(author.nickname)}</strong>
            <span class="muted small">${timeAgo(post.created_at)}</span>
          </div>
        </div>
        <span class="pill ${pillClass}">${kind}</span>
      </div>
      ${post.text ? `<p>${escapeHtml(post.text)}</p>` : ""}
      ${image ? `<img class="post-image" src="${escapeHtml(image)}" alt="">` : ""}
      <div class="comment-list">
        ${comments.length ? comments.map(renderComment).join("") : `<span class="muted small">No comments yet.</span>`}
      </div>
      <form class="comment-form" data-comment-form data-post-id="${escapeHtml(post.id)}">
        <input name="comment" maxlength="300" placeholder="Comment">
        <button type="submit">Send</button>
      </form>
    </article>
  `;
}

function renderComment(comment) {
  const profiles = profileMap();
  const author = profiles.get(comment.author_id) || { nickname: "Brother" };
  return `
    <div class="comment">
      ${avatarHtml(author)}
      <div>
        <div><strong>${escapeHtml(author.nickname)}</strong> <span class="muted small">${timeAgo(comment.created_at)}</span></div>
        <p>${escapeHtml(comment.text)}</p>
      </div>
    </div>
  `;
}

function renderActivity() {
  const profiles = [...(circle?.profiles || [])];
  const me = bootstrap.settings.user_id;
  profiles.sort((a, b) => (a.id === me ? -1 : b.id === me ? 1 : a.nickname.localeCompare(b.nickname)));
  return `
    <section class="section">
      <div class="section-head">
        <h2>Activity</h2>
        <span class="pill">Last hour</span>
      </div>
      <div class="activity-grid">
        ${profiles.length ? profiles.map(renderActivityCard).join("") : `<div class="empty">No profiles yet.</div>`}
      </div>
    </section>
  `;
}

function renderActivityCard(profile) {
  const me = bootstrap.settings.user_id;
  const isSelf = profile.id === me;
  const requests = circle?.requests || [];
  const permissions = circle?.permissions || {};
  const activity = isSelf ? (circle?.local_activity || circle?.activities?.[profile.id]) : circle?.activities?.[profile.id];
  const incoming = requests.find((item) => item.owner_id === me && item.requester_id === profile.id && item.status === "pending");
  const outgoing = requests.find((item) => item.owner_id === profile.id && item.requester_id === me && item.status === "pending");
  const canSee = Boolean(activity && !activity.paused);
  const theySeeMe = Boolean(permissions?.[me]?.[profile.id]);

  let body = "";
  if (canSee) {
    body = renderActivitySummary(activity);
  } else if (isSelf) {
    body = `<div class="empty">Waiting for the first activity sample.</div>`;
  } else {
    body = `
      <div class="empty">
        <div>
          <strong>No activity visible</strong>
          <div class="muted small">${outgoing ? "Request sent." : "Ask only if trust is already there."}</div>
        </div>
      </div>
    `;
  }

  return `
    <article class="activity-card">
      <div class="activity-head">
        <div class="person">
          ${avatarHtml(profile)}
          <div>
            <strong>${escapeHtml(isSelf ? `${profile.nickname} (you)` : profile.nickname)}</strong>
            <span class="muted small">${activity?.updated_at ? `Updated ${timeAgo(activity.updated_at)}` : "No update yet"}</span>
          </div>
        </div>
        ${isSelf ? `<span class="pill green">Mine</span>` : theySeeMe ? `<span class="pill blue">Allowed</span>` : ""}
      </div>
      ${body}
      <div class="inline-actions">
        ${!isSelf && !canSee && !outgoing ? `<button type="button" data-action="request" data-id="${escapeHtml(profile.id)}">Ask to see</button>` : ""}
        ${incoming ? `<button type="button" class="primary" data-action="allow" data-id="${escapeHtml(profile.id)}">Allow request</button>` : ""}
        ${!isSelf ? `<button type="button" data-action="note-to" data-id="${escapeHtml(profile.id)}">Send note</button>` : ""}
      </div>
    </article>
  `;
}

function renderActivitySummary(activity) {
  if (activity.paused) return `<div class="empty">Activity sharing is paused.</div>`;
  const apps = activity.apps || [];
  return `
    <div>
      <span class="pill">${escapeHtml(activity.current_app || "Desktop")}</span>
      <div class="bar-list">
        ${apps.length ? apps.map((item) => `
          <div class="bar-row">
            <span class="bar-label">${escapeHtml(item.name)}</span>
            <span class="bar-track"><span class="bar-fill ${barWidthClass(item.share)}"></span></span>
            <span class="muted small">${escapeHtml(item.minutes)}m</span>
          </div>
        `).join("") : `<div class="muted small">No app samples yet.</div>`}
      </div>
    </div>
  `;
}

function barWidthClass(share) {
  const value = Number.isFinite(Number(share)) ? Number(share) : 0;
  const level = Math.max(1, Math.min(20, Math.ceil(value * 20)));
  return `w-${level}`;
}

function renderNotes() {
  const me = bootstrap.settings.user_id;
  const brothers = (circle?.profiles || []).filter((profile) => profile.id !== me);
  const notes = [...(circle?.notes || [])].reverse();
  return `
    <section class="section">
      <div class="section-head">
        <h2>Encouragement</h2>
        <span class="pill green">No reply needed</span>
      </div>
      <form id="noteForm" class="note-form">
        <select id="noteTo" ${brothers.length ? "" : "disabled"}>
          ${brothers.map((profile) => `<option value="${escapeHtml(profile.id)}">${escapeHtml(profile.nickname)}</option>`).join("")}
        </select>
        <textarea id="noteText" maxlength="500" placeholder="Write something you would say if he was about to give up."></textarea>
        <div class="quick-row">
          ${noteStarters.map((text) => `<button type="button" class="soft" data-fill-note="${escapeHtml(text)}">${escapeHtml(text)}</button>`).join("")}
        </div>
        <div class="form-actions">
          <button class="primary" type="submit" ${brothers.length ? "" : "disabled"}>Send</button>
        </div>
      </form>
    </section>
    <section class="section">
      <div class="notes-list">
        ${notes.length ? notes.map(renderNote).join("") : `<div class="empty">No notes yet.</div>`}
      </div>
    </section>
  `;
}

function renderNote(note) {
  const profiles = profileMap();
  const me = bootstrap.settings.user_id;
  const from = profiles.get(note.from_id) || { nickname: "Brother" };
  const to = profiles.get(note.to_id) || { nickname: "Brother" };
  const incoming = note.to_id === me;
  const unread = incoming && !note.read_at;
  return `
    <article class="note">
      <div class="note-head">
        <div class="person">
          ${avatarHtml(from)}
          <div>
            <strong>${escapeHtml(from.nickname)} to ${escapeHtml(to.nickname)}</strong>
            <span class="muted small">${timeAgo(note.created_at)}</span>
          </div>
        </div>
        <span class="pill ${unread ? "amber" : "green"}">${unread ? "New" : "No reply needed"}</span>
      </div>
      <p>${escapeHtml(note.text)}</p>
      ${unread ? `<div class="inline-actions"><button type="button" data-action="read-note" data-id="${escapeHtml(note.id)}">Mark seen</button></div>` : ""}
    </article>
  `;
}

function renderCircle() {
  const profiles = circle?.profiles || [];
  return `
    <section class="section">
      <div class="section-head">
        <h2>Circle</h2>
        <span class="pill blue">${profiles.length} profiles</span>
      </div>
      <div class="brother-list">
        ${profiles.map(renderBrother).join("")}
      </div>
    </section>
  `;
}

function renderBrother(profile) {
  const me = bootstrap.settings.user_id;
  const isSelf = profile.id === me;
  const permissions = circle?.permissions || {};
  const requests = circle?.requests || [];
  const seesMine = Boolean(permissions?.[me]?.[profile.id]);
  const iSeeHis = Boolean(permissions?.[profile.id]?.[me]);
  const incoming = requests.find((item) => item.owner_id === me && item.requester_id === profile.id && item.status === "pending");
  const outgoing = requests.find((item) => item.owner_id === profile.id && item.requester_id === me && item.status === "pending");
  return `
    <article class="brother">
      <div class="brother-head">
        <div class="person">
          ${avatarHtml(profile)}
          <div>
            <strong>${escapeHtml(isSelf ? `${profile.nickname} (you)` : profile.nickname)}</strong>
            <span class="muted small">${isSelf ? "Your profile" : iSeeHis ? "You can see activity" : "Activity hidden"}</span>
          </div>
        </div>
        ${seesMine && !isSelf ? `<span class="pill blue">Sees mine</span>` : ""}
      </div>
      ${isSelf ? "" : `
        <div class="inline-actions">
          ${iSeeHis ? `<span class="pill green">Access accepted</span>` : outgoing ? `<span class="pill amber">Request sent</span>` : `<button type="button" data-action="request" data-id="${escapeHtml(profile.id)}">Ask to see</button>`}
          ${seesMine ? `<button type="button" class="warn" data-action="revoke" data-id="${escapeHtml(profile.id)}">Stop sharing mine</button>` : `<button type="button" class="${incoming ? "primary" : ""}" data-action="allow" data-id="${escapeHtml(profile.id)}">${incoming ? "Allow request" : "Share mine"}</button>`}
          <button type="button" data-action="note-to" data-id="${escapeHtml(profile.id)}">Send note</button>
        </div>
      `}
    </article>
  `;
}

function wireDashboard() {
  document.querySelectorAll("[data-tab]").forEach((button) => {
    button.addEventListener("click", () => {
      activeTab = button.dataset.tab;
      renderDashboard();
    });
  });

  document.querySelectorAll("[data-copy]").forEach((button) => {
    button.addEventListener("click", () => copyText(button.dataset.copy || ""));
  });

  document.getElementById("shareActivity")?.addEventListener("change", async (event) => {
    try {
      await api("/api/activity-sharing", { share_activity: event.target.checked });
      await loadBootstrap();
      await refreshCircle(false);
      renderDashboard();
    } catch (error) {
      showToast(error.message);
    }
  });

  document.getElementById("connectionForm")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const inviteValue = document.getElementById("relayUrl").value;
    const parsedInvite = parseInviteValue(inviteValue);
    const inviteToken = document.getElementById("inviteToken").value.trim() || parsedInvite.token;
    try {
      await api("/api/connect", {
        mode: "join",
        invite_url: inviteValue,
        relay_url: parsedInvite.relayUrl,
        invite_token: inviteToken,
      });
      circle = await api("/api/profile", {
        nickname: bootstrap.settings.nickname,
        avatar: bootstrap.settings.avatar || "",
      });
      await loadBootstrap();
      renderDashboard();
      showToast("Connection saved.");
    } catch (error) {
      showToast(error.message);
    }
  });

  document.querySelector('[data-action="switch-host"]')?.addEventListener("click", async () => {
    try {
      await api("/api/connect", { mode: "host" });
      circle = await api("/api/profile", {
        nickname: bootstrap.settings.nickname,
        avatar: bootstrap.settings.avatar || "",
      });
      await loadBootstrap();
      renderDashboard();
      showToast("Hosting this circle.");
    } catch (error) {
      showToast(error.message);
    }
  });

  document.querySelector('[data-action="switch-join"]')?.addEventListener("click", async () => {
    try {
      await api("/api/session/reset", {});
      setupMode = "join";
      circle = null;
      await loadBootstrap();
      renderSetup();
      showToast("Hosting stopped. Enter the circle you want to join.");
    } catch (error) {
      showToast(error.message);
    }
  });

  document.querySelector('[data-action="close-app"]')?.addEventListener("click", closeApp);

  document.getElementById("postImage")?.addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    postImage = await resizeImage(file, 1100, 0.78);
    const image = safeImageSrc(postImage);
    if (!image) {
      postImage = "";
      showToast("Image could not be loaded.");
      return;
    }
    const preview = document.getElementById("postImagePreview");
    preview.classList.remove("hidden");
    preview.innerHTML = `<img class="post-image" src="${escapeHtml(image)}" alt="">`;
  });

  document.getElementById("postForm")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      circle = await api("/api/post", {
        kind: document.getElementById("postKind").value,
        text: document.getElementById("postText").value,
        image: postImage,
      });
      postImage = "";
      renderDashboard();
    } catch (error) {
      showToast(error.message);
    }
  });

  document.querySelectorAll("[data-comment-form]").forEach((form) => {
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const input = form.querySelector('input[name="comment"]');
      try {
        circle = await api("/api/comment", {
          post_id: form.dataset.postId,
          text: input?.value || "",
        });
        renderDashboard();
      } catch (error) {
        showToast(error.message);
      }
    });
  });

  document.querySelector('[data-action="clear-post-image"]')?.addEventListener("click", () => {
    postImage = "";
    renderDashboard();
  });

  document.getElementById("noteForm")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      circle = await api("/api/note", {
        to_id: document.getElementById("noteTo").value,
        text: document.getElementById("noteText").value,
      });
      renderDashboard();
    } catch (error) {
      showToast(error.message);
    }
  });

  document.querySelectorAll("[data-fill-note]").forEach((button) => {
    button.addEventListener("click", () => {
      const box = document.getElementById("noteText");
      if (box) {
        box.value = button.dataset.fillNote || "";
        box.focus();
      }
    });
  });

  document.querySelectorAll("[data-action]").forEach((button) => {
    const action = button.dataset.action;
    if (["switch-host", "switch-join", "close-app", "clear-post-image"].includes(action)) return;
    button.addEventListener("click", () => handleAction(action, button.dataset.id));
  });
}

async function closeApp() {
  try {
    if (refreshTimer) window.clearInterval(refreshTimer);
    await api("/api/close", {});
  } catch {
    // The server may shut down before the browser receives the final response.
  }
  app.innerHTML = `
    <main class="setup-wrap">
      <section class="setup">
        <div class="setup-side">
          <div>
            <div class="setup-mark">BH</div>
            <h1>Closed</h1>
            <p>Brotherhood has stopped on this computer.</p>
          </div>
        </div>
        <div class="setup-main">
          <h2>App Closed</h2>
          <p>You can close this browser tab. Next time you open Brotherhood, it will ask Host or Join again.</p>
        </div>
      </section>
    </main>
  `;
}

async function handleAction(action, id) {
  try {
    if (action === "request") circle = await api("/api/request-access", { owner_id: id });
    if (action === "allow") circle = await api("/api/permission", { viewer_id: id, allow: true });
    if (action === "revoke") circle = await api("/api/permission", { viewer_id: id, allow: false });
    if (action === "read-note") circle = await api("/api/note/read", { note_id: id });
    if (action === "note-to") {
      activeTab = "notes";
      renderDashboard();
      const select = document.getElementById("noteTo");
      if (select) select.value = id;
      document.getElementById("noteText")?.focus();
      return;
    }
    renderDashboard();
  } catch (error) {
    showToast(error.message);
  }
}

async function copyText(text) {
  if (!text) {
    showToast("Nothing to copy yet.");
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    showToast("Copied.");
  } catch {
    showToast(text);
  }
}

function resizeImage(file, maxSize, quality) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read image."));
    reader.onload = () => {
      const image = new Image();
      image.onerror = () => reject(new Error("Could not load image."));
      image.onload = () => {
        const scale = Math.min(1, maxSize / Math.max(image.width, image.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(image.width * scale));
        canvas.height = Math.max(1, Math.round(image.height * scale));
        const ctx = canvas.getContext("2d");
        ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/jpeg", quality));
      };
      image.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

async function boot() {
  try {
    await loadBootstrap();
    await refreshCircle(false);
  } catch (error) {
    lastError = error.message;
  }
  render();
  refreshTimer = window.setInterval(refreshCircle, 4000);
}

boot();
