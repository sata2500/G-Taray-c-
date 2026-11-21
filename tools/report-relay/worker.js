/**
 * G-Tarayıcı anonim sorun bildirimi aracısı (Cloudflare Worker).
 *
 * Uygulama raporu buraya gönderir; aracı raporu denetler ve GitHub'da bir hata kaydı (issue) açar. Kullanıcının
 * GitHub hesabına ihtiyacı yoktur. GitHub anahtarı yalnızca burada, Cloudflare'in gizli değişkenlerinde durur;
 * uygulamanın içinde hiçbir anahtar yoktur.
 *
 * Gizli değişkenler / ayarlar:
 *   GITHUB_TOKEN  (gizli)  Yalnızca bu depoda "Issues: Read and write" izni olan ince ayarlı GitHub anahtarı
 *   REPO          (düz)    sahip/depo, ör. sata2500/G-Taray-c-
 *   SALT          (gizli)  Rastgele bir metin; IP adresinin tek yönlü özetinde kullanılır
 *   RATE          (isteğe bağlı KV bağlaması)  Kötüye kullanıma karşı sınır: aynı bağlantıdan saatte 5 bildirim
 *
 * Gizlilik: IP adresi saklanmaz ve GitHub'a gönderilmez; sınır için yalnızca saatlik, tuzlanmış özeti KV'de 1 saat tutulur.
 */

const CATEGORIES = ["Çökme", "Sayfa", "Video", "İndirme", "VPN", "Eklenti", "Diğer", "Öneri"];
const MAX_BYTES = 64 * 1024;
const MAX_BODY = 60000;
const PER_HOUR = 5;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/") return json({ ok: true, service: "gtarayici-report-relay" });
    if (request.method !== "POST" || url.pathname !== "/report") return json({ error: "not_found" }, 404);

    // Yalnızca uygulamadan gelen istekler (tarayıcı formları veya rastgele istekler değil).
    if (!(request.headers.get("X-GT-Client") || "").startsWith("G-Tarayici/")) return json({ error: "forbidden" }, 403);
    const length = Number(request.headers.get("Content-Length") || 0);
    if (length > MAX_BYTES) return json({ error: "too_large" }, 413);

    let data;
    try {
      const text = await request.text();
      if (text.length > MAX_BYTES) return json({ error: "too_large" }, 413);
      data = JSON.parse(text);
    } catch (e) {
      return json({ error: "bad_request" }, 400);
    }
    const title = clean(data.title, 200);
    const body = clean(data.body, MAX_BODY);
    const category = CATEGORIES.includes(data.category) ? data.category : "Diğer";
    const version = clean(data.version, 40);
    if (title.length < 3 || body.length < 1) return json({ error: "bad_request" }, 400);

    if (env.RATE) {
      const ip = request.headers.get("CF-Connecting-IP") || "unknown";
      const hour = Math.floor(Date.now() / 3600000);
      const key = `r:${await sha256(`${env.SALT || ""}:${ip}:${hour}`)}`;
      const count = Number((await env.RATE.get(key)) || 0);
      if (count >= PER_HOUR) return json({ error: "rate_limited" }, 429);
      await env.RATE.put(key, String(count + 1), { expirationTtl: 3700 });
    }

    const issue = {
      title,
      body: `${body}\n\n---\n_G-Tarayıcı ${version || "?"} içinden anonim olarak gönderildi._`,
      labels: ["kullanıcı-bildirimi", category],
    };
    let response = await createIssue(env, issue);
    // Etiketler oluşturulamazsa (ör. izin), etiketsiz yeniden dene.
    if (response.status === 422) response = await createIssue(env, { title: issue.title, body: issue.body });
    if (!response.ok) return json({ error: "github_failed", status: response.status }, 502);
    const created = await response.json();
    return json({ number: created.number, url: created.html_url }, 201);
  },
};

function createIssue(env, payload) {
  return fetch(`https://api.github.com/repos/${env.REPO}/issues`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.GITHUB_TOKEN}`,
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      "User-Agent": "gtarayici-report-relay",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

/** Metni kırpar ve görünmez denetim karakterlerini atar (satır sonları ve sekme kalır). */
function clean(value, max) {
  if (typeof value !== "string") return "";
  return value.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim().slice(0, max);
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8" } });
}
