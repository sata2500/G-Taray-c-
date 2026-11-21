# G-Tarayıcı arayüz ve deneyim incelemesi

Telefonda (vivo V2244, Android 15, hareketle gezinme) hata ayıklama sürümüyle yapılan inceleme. Ekran görüntüleri
`test-results/ui/once/` altında (git'e girmez); dosya adları aşağıda **kanıt** olarak geçer. Önem: **Y** yüksek
(okunmuyor, yanlış çalışıyor, gizlilik), **O** orta (belirgin deneyim sorunu), **D** düşük (cila).

## 1. Başlangıç durumu

- Derleme, `:app:lintDebug` ("No issues found") ve 27 birim testi temiz. Kod denetiminde yalnızca Türkçe yazım
  denetimi ve bağımlılık sürümü uyarıları kapalı (`app/lint.xml`) — makul.
- Bağımlılıklar: Compose BOM 2026.09.00 → **Material 3 1.4.0**, Compose UI 1.12.1.
- Kod ölçüleri: 25 `Toast` (12 dosya), 21 `AlertDialog` (9 dosya), 0 `@Preview`. En uzun dosyalar:
  `SettingsScreen.kt` 614, `TabsScreen.kt` 505, `LibraryScreens.kt` 464, `BrowserApp.kt` 459,
  `BrowserController.kt` 433, `MainActivity.kt` 396 satır.

### Ölçümler (`test-results/ui/olcum-once.txt`)

| Ölçüm | Sonuç |
|---|---|
| Soğuk açılış (8 sekme geri yüklenirken, 5 deneme) | 1463–1681 ms, ortanca **1638 ms** |
| Sekme değiştirme (adres çubuğu kaydırma, 645 kare) | %3,9 takılan, p90 25 ms, p99 48 ms |
| Menü aç/kapa (579 kare) | **%12,3 takılan**, p95 46 ms, **p99 300 ms**, 70 yavaş UI karesi |
| Sekme ekranı aç + kaydır (575 kare) | **%11,5 takılan**, p95 53 ms, **p99 250 ms**, 60 yavaş UI karesi |
| Bellek | ana süreç PSS 312 MB, Java yığını 24 MB; 7 süreç RSS toplamı 1,7 GB |

Ekran 120 Hz destekliyor, ölçüm sırasında 60 Hz'deydi. Sayfa içeriği Gecko'nun yüzeyinde çizilir; `gfxinfo` yalnızca
uygulama arayüzünün (Compose) karelerini ölçer.

Notlar: pil ölçüm boyunca %3'teydi (USB'den şarjda, güç tasarrufu kapalı, ısı durumu 1). %200 yazı boyutu ve yatay
mod sistem ayarı olduğu için henüz çekilmedi (bkz. "Açık konular").

## 2. Bulgular

### Görsel tutarlılık

| # | Ekran | Sorun | Önem | Kanıt |
|---|---|---|---|---|
| G1 | Ana sayfa | Koyu temada yer imi ve sık ziyaret edilen etiketleri **siyah** çiziliyor, okunmuyor (ekran `Surface` değil düz `background`; metne içerik rengi gelmiyor — önceki rehber hatasıyla aynı kök) | Y | `02-ana-sayfa-koyu` |
| G2 | Kilit ekranı | "Gizli sekmeler kilitli" başlığı koyu mor zemin üzerinde siyah | Y | `24-kilit-ekrani-gizli` |
| G3 | Ayarlar alt sayfaları | Simgeli satırlar (Arama motoru, DNS, parola) ile simgesiz anahtarlı satırlar karışık; metinler iki farklı hizada başlıyor | O | `15a-ayarlar-genel-koyu`, `15c-ayarlar-gizlilik-koyu` |
| G4 | Ayarlar | Bölümler gruplanmamış düz uzun liste; bölüm başlığı, kart veya ayraç yok | O | `15c-*`, `15d-*` |
| G5 | Genel | Aynı iş için farklı bileşenler: onay için `AlertDialog`, seçim için hem `AlertDialog` hem `DropdownMenu` hem alt sayfa; mesaj için `Toast` ve `Snackbar` birlikte | O | kod |
| G6 | Köşe yuvarlaklıkları | Sekme kartı 14–22 dp, ana sayfa arama 28 dp, menü ızgarası daire, ayar arama 28 dp, kartlar 12–16 dp — ortak bir şekil ölçeği yok | D | `07-*`, `02-*`, `05-*` |
| G7 | Hata sayfası | Açık temada da koyu (Mozilla şablonu kendi stilini kullanıyor), uygulamanın diliyle uyumsuz | D | `22-hata-sayfasi-acik` |
| G8 | Profil ekle | Varsayılan seçili renk soluk gri; "Biyometrik kilit" açıklaması dar sütunda 5 satıra sarıyor | D | `16b-profil-ekle-koyu` |
| G9 | Ana sayfa → Sık ziyaret edilenler | Yüklenemeyen adresler (hata sayfası) de geçmişe yazılıyor ve kısayol olarak çıkıyor ("Olmayan-alan-adi-gt"); `tr.wikipedia.org` ve `en.wikipedia.org` ikisi de "Wikipedia" adıyla, ayırt edilemiyor | O | `sonra/02-ana-sayfa-*` |
| G10 | Kod | Material 3 1.5.0'da eski `ListItem`, `rememberModalBottomSheetState` ve `PasswordVisualTransformation` kullanımları eskimiş (deprecated) sayılıyor (40+ uyarı); tasarım sistemi bileşenlerine geçişle kalkacak | D | derleme çıktısı |

### Modernlik ("klasik" görünümün somut nedenleri)

| # | Neden | Kanıt |
|---|---|---|
| M1 | Bildirimler 25 yerde düz Android `Toast` (sistem stilinde gri kutu, eylem yok, uygulamanın renklerini almıyor) | kod |
| M2 | 21 `AlertDialog`; geri alınabilir işler (yer imi/geçmiş silme, eklenti kaldırma) bile onay penceresiyle ya da onaysız ve geri alınamaz | kod |
| M3 | Tam ekran sayfalar (ayarlar, geçmiş, yer imleri, sekmeler, VPN…) `when (overlay)` ile **animasyonsuz** açılıp kapanıyor; öngörülü geri hareketi (predictive back) yok | `BrowserApp.kt` |
| M4 | Boş durumlar yalnızca ortalanmış düz metin (ör. "Henüz indirilen dosya yok") | `13-indirilenler-bos-koyu` |
| M5 | Ana sayfa: ad + slogan + **iki ayrı arama kutusu** (sayfadaki ve alttaki); kişilik, saat/selamlama, gizlilik özeti yok | `02-ana-sayfa-*` |
| M6 | Material 3 hareket şeması (`MotionScheme`) kullanılmıyor; geçişler varsayılan | kod |
| M7 | Hiçbir bileşenin `@Preview`'u yok; görsel değişiklik ancak cihazda görülebiliyor | kod |
| M8 | Menü: üstte simge satırı, altında 8'li ızgara, altında uzun liste — üç farklı düzen tek sayfada | `05-menu-sayfada-koyu` |

### Kullanım akışları (adım sayısı ve takılma noktaları)

| Akış | Adım | Gözlem |
|---|---|---|
| İlk kurulum | 3 sayfa + izin | İyi; başlık okunurluğu önceki turda düzeltildi |
| Arama | 3 (çubuk → yaz → Enter) | Yeni sekme doğrudan aramaya açılıyor (iyi) |
| Sekmeler arası gezinme | 1 kaydırma | Hareket keşfedilemiyor; hiçbir ipucu yok |
| YouTube indirme | Menü → Videoyu indir → **~6 sn bekleme** → seçim | Bekleme sırasında yalnızca dönen çember; başladığında sistem Toast'u, bitince yalnızca bildirim |
| Küçük pencere (PiP) | Otomatik | İlk kez kullanana anlatılmıyor |
| Gizli sekme | 2 (Menü → Gizli sekme veya sayaçta uzun basma) | İyi |
| Profil değiştirme | Tek profilde rozet görünmüyor; ikinci profil için Menü → Profiller → Profil ekle → form (4+) | Kabul edilebilir |
| VPN kurulumu | Menü → VPN → İndir ve kur → sistem izni → geri → kurulum onayı → geri → Sunucu ekle → yapıştır → Kaydet → Bağlan (**10+**) | Uzun; her adımda ne olacağı söylenmiyor |
| Eklenti kurma | Menü → Eklentiler → Daha fazla eklenti bul → AMO → ara → Firefox'a ekle → izin → Ekle (**7+**) | AMO yeni sekmede açılıyor, geri dönüş belirsiz |
| Yer imi düzenleme | Menü → Yer imleri → ⋮ → Düzenle → kaydet (5) | Klasör adı serbest metin; mevcut klasörler önerilmiyor |
| Sorun bildirme | **Yok** | Uygulamada geri bildirim yolu yok; yalnızca "PiP tanılama kaydı" var |

### Durumlar ve geri bildirim

| # | Sorun | Önem |
|---|---|---|
| D1 | 9 hata metni ham istisna mesajını olduğu gibi ekliyor (`"… : %1$s"` — `yt_error`, `yt_failed`, `vpn_error`, `vpn_test_failed`, `vpn_invalid`, `webapk_failed`, `translate_error`, `update_install_failed`, `vpn_core_failed`); cihazda görülen örnek: "Bağlantı okunamadı: Bağlantı bulunamadı". Ne yapılacağı söylenmiyor | O |
| D2 | Uzun işlerde (YouTube çözümleme, VPN bileşeni indirme, güncelleme) kalan süre yok; bitince "Aç" gibi bir sonraki adım yok | O |
| D3 | Ağ bağlantısı kopunca / geri gelince uygulama bir şey söylemiyor (yalnızca hata sayfası) | O |
| D4 | Geri alınabilir silmelerde "Geri al" yok (geçmiş, yer imi, eklenti kaldırma onaysız ve kalıcı) | O |
| D5 | Satır içi doğrulama yok: özel DNS adresi, VPN bağlantısı, yer imi adresi kaydedildikten sonra hata veriyor | D |

### Gizlilik ve güvenlik (inceleme sırasında bulunan hatalar)

| # | Sorun | Önem | Kanıt |
|---|---|---|---|
| P1 | "Gizli sekmelerde ekran görüntüsünü engelle" ayarı **anında uygulanmıyor**: engel yalnızca etkinlik oluşturulurken bir kez kuruluyor. Ayar açılınca uygulama yeniden başlayana kadar gizli sekmelerin ekran görüntüsü alınabiliyor; kapatılınca da engel sürüyor | Y | `*-gizli` görüntülerinin siyah çıkması; `MainActivity.kt` |
| P2 | Kilit ekranının altında, gizli sekmenin arama alanı erişilebilirlik ağacında görünür kalıyor (ekran okuyucu kilitliyken içeriğe ulaşabilir) | O | uiautomator dökümü |

### Erişilebilirlik

| # | Sorun | Önem |
|---|---|---|
| E1 | Profil renk daireleri 28 dp (önerilen 48 dp); sekme kartı kapat düğmesi 40 dp | O |
| E2 | Siyah metin / koyu zemin karşıtlığı (G1, G2) | Y |
| E3 | %200 yazı boyutu henüz denenmedi (sistem ayarı; bkz. açık konular). Sabit yükseklikli öğeler (araç çubuğu 52 dp, sekme kartı en-boy oranı, menü ızgarası) taşma adayı | ? |

### Akıcılık

| # | Sorun | Önem |
|---|---|---|
| A1 | Menü ilk açılışta p99 300 ms, 70 yavaş UI karesi: alt sayfa her açılışta tüm içeriği (ızgara, liste, eklenti simgeleri) baştan oluşturuyor | O |
| A2 | Sekme ekranı p99 250 ms: önizlemelerin küçültülmesi (`TabThumbnails.load` → `downscale`) çağıranın iş parçacığında, yani Compose'dan çağrılınca ana iş parçacığında | O |
| A3 | Başlangıç profili (Baseline Profile) yok; StrictMode kapalı | D |

### Kod

| # | Sorun |
|---|---|
| K1 | Uzun dosyalar (yukarıda); `SettingsScreen.kt` 7 alt sayfa + 5 pencere + arama mantığını tek dosyada tutuyor |
| K2 | Tekrarlar: her ekranda elle `ListItem` + `Switch`, elle renkli `Box` daireler, elle `AlertDialog`, `Toast.makeText` |
| K3 | `BrowserController` 433 satır: gezinme, sekmeler, sayfa işlemleri, yer imi, profil, kilit, WebAPK hepsi bir arada |
| K4 | Tam ekran sayfa yönetimi `Overlay` enum + `when`; geçiş, geri yığını ve öngörülü geri yok |
| K5 | Elle yazılmış ölçüler ve renkler dağınık (`dp` sabitleri, `Color(0xFF…)` listeleri birden çok yerde) |

## 3. Açık konular

- %200 yazı boyutu ve yatay mod görüntüleri: sistem ayarı; kullanıcı tarafından ayarlanıp çekilecek.
- Web uygulaması penceresi ve VPN bileşeni: "Bilinmeyen uygulamaları yükleme" izni kullanıcı tarafından verilmeli.
