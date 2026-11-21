# Anonim sorun bildirimi aracısı — kurulum

Bu küçük sunucu, uygulamanın içinden gönderilen sorun bildirimlerini alır ve GitHub'da hata kaydı açar. Kullanıcıların
GitHub hesabı gerekmez. GitHub anahtarı yalnızca bu sunucuda durur; uygulamada anahtar yoktur.

Bir kez kurulur. Cloudflare'in ücretsiz planı yeterlidir (günde 100.000 istek). Kurulum tarayıcıdan yaklaşık
15 dakika sürer.

## 1. GitHub anahtarı oluşturun

1. GitHub → sağ üstte profil resmi → **Settings** → en altta **Developer settings** → **Personal access tokens** →
   **Fine-grained tokens** → **Generate new token**.
2. **Token name:** `G-Tarayici bildirimleri`. **Expiration:** en uzun süreyi seçin (süre dolunca yenilemeniz gerekir).
3. **Repository access:** *Only select repositories* → `sata2500/G-Taray-c-`.
4. **Permissions → Repository permissions → Issues:** *Read and write*. Başka izin vermeyin.
5. **Generate token**'a basın ve çıkan `github_pat_…` ile başlayan anahtarı bir yere kopyalayın. Anahtar bir daha
   gösterilmez.

## 2. Cloudflare'de sunucuyu oluşturun

1. <https://dash.cloudflare.com/sign-up> adresinden ücretsiz hesap açın (kart gerekmez).
2. Sol menü → **Workers & Pages** → **Create** → **Create Worker** (Hello World şablonu) → ad: `gtarayici-report-relay`
   → **Deploy**.
3. **Edit code**'a basın. Açılan düzenleyicide her şeyi silin, bu klasördeki `worker.js` dosyasının **tamamını**
   yapıştırın ve **Deploy**'a basın.

## 3. Ayarları girin

Worker sayfası → **Settings** → **Variables and Secrets** → **Add**:

| Tür | Ad | Değer |
|---|---|---|
| Text | `REPO` | `sata2500/G-Taray-c-` |
| Secret | `GITHUB_TOKEN` | 1. adımdaki `github_pat_…` anahtarı |
| Secret | `SALT` | Rastgele uzun bir metin (ör. 30 karakterlik herhangi bir şey) |

**Deploy**'a basın.

## 4. Kötüye kullanıma karşı sınır (önerilir)

1. Sol menü → **Storage & Databases** → **KV** → **Create a namespace** → ad: `gtarayici-rate`.
2. Worker → **Settings** → **Bindings** → **Add** → **KV namespace** → Variable name: `RATE`, namespace:
   `gtarayici-rate` → **Deploy**.

Böylece aynı bağlantıdan saatte en fazla 5 bildirim kabul edilir. IP adresleri saklanmaz; yalnızca saatlik,
tuzlanmış bir özet 1 saat tutulur.

## 5. Adresi uygulamaya tanıtın

1. Worker sayfasının üstündeki adresi kopyalayın (ör. `https://gtarayici-report-relay.KULLANICI.workers.dev`).
   Tarayıcıda açınca `{"ok":true,...}` görmelisiniz.
2. GitHub → depo → **Settings** → **Secrets and variables** → **Actions** → **Variables** sekmesi →
   **New repository variable**:
   - **Name:** `GT_REPORT_RELAY_URL`
   - **Value:** kopyaladığınız adres (sonunda `/` olmadan)

Bir sonraki sürümden itibaren uygulamanın "Sorun bildir" ekranında **"Gönder (hesap gerekmez)"** düğmesi çıkar. Gelen
bildirimler GitHub'da `kullanıcı-bildirimi` etiketiyle açılır.

## Deneme

`node test.mjs` aracının denetimlerini (geçersiz istekler, boyut sınırı, saatlik sınır, IP'nin GitHub'a gitmemesi)
GitHub'a bağlanmadan dener.
