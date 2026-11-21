// Aracının denetimleri için küçük deneme: `node test.mjs` (GitHub isteği taklit edilir).
import worker from "./worker.js";

const calls = [];
globalThis.fetch = async (url, init) => {
  calls.push({ url, init });
  return new Response(JSON.stringify({ number: 42, html_url: "https://github.com/x/y/issues/42" }), { status: 201 });
};
const store = new Map();
const env = {
  GITHUB_TOKEN: "t", REPO: "sata2500/G-Taray-c-", SALT: "s",
  RATE: { get: async (k) => store.get(k) ?? null, put: async (k, v) => store.set(k, v) },
};
const post = (body, headers = {}) => worker.fetch(new Request("https://relay.example/report", {
  method: "POST",
  headers: { "X-GT-Client": "G-Tarayici/0.9.2", "CF-Connecting-IP": "203.0.113.7", ...headers },
  body: typeof body === "string" ? body : JSON.stringify(body),
}), env);
const assert = (ok, msg) => { if (!ok) { console.error("BAŞARISIZ:", msg); process.exit(1); } };

const good = { title: "[Video] YouTube indirilemiyor", body: "### Ne oldu?\nindirme çalışmıyor", category: "Video", version: "0.9.2" };
let r = await post(good);
assert(r.status === 201 && (await r.json()).number === 42, "geçerli rapor kayıt açmalı");
const sent = JSON.parse(calls[0].init.body);
assert(sent.labels.includes("Video") && sent.body.includes("anonim"), "etiket ve not eklenmeli");
assert(!calls[0].init.body.includes("203.0.113.7"), "IP adresi GitHub'a gitmemeli");
assert((await worker.fetch(new Request("https://relay.example/report", { method: "POST", body: "{}" }), env)).status === 403, "uygulama dışı istek reddedilmeli");
assert((await post("{bozuk")).status === 400, "bozuk JSON reddedilmeli");
assert((await post({ title: "a", body: "" })).status === 400, "boş rapor reddedilmeli");
assert((await post("x".repeat(70000))).status === 413, "çok büyük rapor reddedilmeli");
for (let i = 0; i < 4; i++) await post(good);
assert((await post(good)).status === 429, "saatte 5 bildirimden sonra sınırlanmalı");
console.log("aracı denetimleri: hepsi geçti");
