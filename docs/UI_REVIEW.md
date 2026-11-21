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

## 4. Sonra (0.8.0 çalışması, 2026-11-21)

Ekran görüntüleri `test-results/ui/sonra/`, ölçüm `test-results/ui/olcum-sonra.txt`.

### Bulguların durumu

| # | Durum | Nasıl |
|---|---|---|
| G1, G2, E2 | ✅ | Ana sayfa ve kilit ekranı `Surface` içinde; metin içerik rengini alıyor (koyu temada okunur, cihazda doğrulandı) |
| G3, G4 | ✅ | Ayarlar tek veri modelinden: bölüm başlıkları, segmentli kartlar, her satırda ton dairesinde simge; arama aynı satırları gösteriyor |
| G5, M1, M2, K2 | ✅ | 25 `Toast` → tek mesaj kanalı; geri alınabilir silmeler "Geri al", onay penceresi yalnız geri alınamaz işlerde |
| G6, K5 | ✅ (kısmen) | `Tokens.kt` (boşluk, şekil, boyut); yeni/elden geçen ekranlarda elle `dp` yok. Eski ekranların (sekme kartı, VPN, profiller) iç ölçüleri henüz belirteçlere taşınmadı |
| G7 | ⏳ | Mozilla hata sayfası şablonu hâlâ kendi stilinde |
| G8 | ✅ | Renk seçici yeniden yazıldı (48 dp, okunur onay işareti) |
| G9 | ✅ | Açılamayan adresler geçmişe/sık ziyaret edilenlere girmiyor; aynı adlı siteler "Wikipedia (tr)" gibi ayırt ediliyor (birim testli) |
| G10 | ✅ (kısmen) | `ListItem` ve `rememberModalBottomSheetState` kalmadı; 3 `PasswordVisualTransformation` (parola alanları) kaldı — `SecureTextField`'a geçiş ayrı iş |
| M3, K4 | ✅ | `OverlayHost`: kayarak giriş/çıkış, `PredictiveBackHandler`. Test telefonu (vivo) geri hareketinin ilerlemesini iletmiyor; tamamlanınca kapanış animasyonu oynuyor |
| M4 | ✅ | Boş durumlarda simge (geçmiş, yer imleri, indirilenler, arama sonucu yok) |
| M5 | ✅ (kısmen) | Yeni ana sayfa (amblem, büyük arama kutusu, koruma çipi). Alt çubukta ikinci arama kutusu hâlâ var |
| M6 | ✅ | `MotionScheme.expressive()` |
| M7 | ✅ (kısmen) | 21 `@Preview` (tasarım sistemi, ana sayfa, mesaj, hata, ayar satırları, renk seçici, sorun bildirme; açık/koyu/%200 yazı). Uygulama bileşenlerine doğrudan bağlı ekranlarda yok |
| M8 | ✅ | Menü: eylem satırı + ızgara + segmentli kartlar, aynı dil |
| D1 | ✅ | `HumanErrors`: 9 ham metin kaldırıldı; hata türü → "ne oldu. ne yapılmalı"; ham metin "Ayrıntılar"da ve tanılama kaydında |
| D2 | ✅ | İndirmelerde bayt ve kalan süre, YouTube bildiriminde kalan süre, bitince "İndirildi · Aç" |
| D3 | ✅ | İnternet kesildi / yeniden kuruldu (2 sn'den kısa kopmalar yok sayılır) |
| D4 | ✅ | Sekme, yer imi (menü ve liste), geçmiş satırı, tanılama kaydı: "Geri al" (cihazda doğrulandı). Eklenti kaldırma: onay (geri alınamaz) |
| D5 | ✅ | DNS adresi, yer imi adresi, VPN bağlantısı yazarken denetleniyor |
| P1 | ✅ | Ayar değişince `FLAG_SECURE` anında açılıp kapanıyor |
| P2 | ✅ | Kilit ekranı açıkken alttaki arayüz erişilebilirlik ağacından gizli |
| E1 | ✅ | Renk daireleri 48 dp dokunma alanında |
| E3 | ✅ | Telefonun en büyük yazı boyutu (%154; bu vivo'da %200 seçilemiyor) ve yatay modda denendi; 6 sorun bulunup düzeltildi (aşağıda). %200, bileşen önizlemelerinde (fontScale = 2) duruyor |
| A1 | ⏳ | Menü ilk açılış p99 300 ms, hata ayıklama sürümünde değişmedi. Sürüm yapısında Baseline Profile menü yolunu da içeriyor; ölçüm için `StartupBenchmark.menuAndTabsFrames` hazır, çalıştırılmadı |
| A2 | ✅ | Önizleme okuma/küçültme `Dispatchers.Default`'ta; sekme ekranında takılan kare %11,5 → %7,8, p99 250 → 133 ms |
| A3 | ✅ | StrictMode (hata ayıklama; açılıştan sonra, günlüğe yazar); Baseline Profile üretildi (20 366 kural) ve pakete girdi: sürüm yapısında soğuk açılış 698 → 490 ms |
| K1, K3 | ✅ | `BrowserController` 4 parçaya; Settings/Library/Tabs/BrowserApp bölündü. En uzun dosya artık `MainActivity.kt` (410) |

### Yeni: sorun bildirme ve tanılama

"PiP tanılama kaydı" kaldırıldı. Yerine: genel tanılama kaydı (500 satırlık halka, dosyada kalıcı, kategori etiketli,
tekrarlar birleşir, gizlilik filtresinden geçer), çökme raporu (bir sonraki açılışta sorulur), "Sorun bildir" (tür,
açıklama, isteğe bağlı alan adı/ekran görüntüsü/kayıt/çökme → önizleme → GitHub'da hazır bildirim, dosya olarak paylaş
veya kopyala), `.github/ISSUE_TEMPLATE`. Cihazda uçtan uca denendi: önizleme maskeli, GitHub giriş sayfası hazır
bildirimi `return_to` ile taşıyor, ekran görüntüsü Resimler › G-Tarayıcı'ya kaydediliyor.

### Kod ölçüleri

| | Önce | Sonra |
|---|---|---|
| `Toast` | 25 | 0 |
| Ham `%1$s` hata metni | 9 | 0 |
| `@Preview` | 0 | 21 |
| Birim testi | 27 | 86 (2 atlanan, 0 hata) |
| En uzun dosya | `SettingsScreen.kt` 614 | `MainActivity.kt` 410 |

### Ölçümler (`test-results/ui/olcum-sonra.txt`)

| Ölçüm | Önce | Sonra |
|---|---|---|
| Sekme ekranı aç + kaydır | %11,5 takılan, p99 250 ms | **%6,4–7,8 takılan, p99 133–200 ms** |
| Sekme değiştirme (çubuk) | %3,9, p99 48 ms | %3,6, p99 69 ms (fark gürültü içinde) |
| Menü aç/kapa | %12,3, p99 300 ms | %12,1–12,4, p99 300 ms — **değişmedi** (A1 açık) |
| Soğuk açılış, sürüm yapısı | ölçülmemişti | profilsiz **698 ms** → Baseline Profile ile **490 ms** (ortanca, −%30) |
| Soğuk açılış, hata ayıklama | 1638 ms | 1425–3154 ms arası; cihaz durumuna bağlı, karşılaştırılabilir değil (aşağıda) |

Hata ayıklama sürümünde açılış süresi aynı kodla 1,4–3,1 sn arasında oynadı (seçili sekme, sekme sayısı, pil/ısı);
"önce" ölçümüyle doğrudan karşılaştırmak yanıltıcı olur. Zaman damgalı ölçümde uygulamanın kendi payında iki şey
bulunup düzeltildi: StrictMode'un açılışta 752 satır günlük yazması (~1 sn; artık açılıştan 5 sn sonra açılıyor) ve
tanılama kaydının dosyadan ana iş parçacığında okunması (~210 ms; artık arka planda). Asıl ölçüt sürüm yapısıdır.

Pil "önce" ölçümünde %3'teydi; "sonra" ölçümleri %43–50'de alındı.

### Büyük yazı, yatay mod, WebAPK ve VPN (telefonda, kullanıcıyla birlikte)

| Bulgu | Düzeltme |
|---|---|
| Sekme sayacında iki basamaklı sayı kutudan taşıyor | Rakam simgenin parçası, sabit boyutta |
| Kutucuk etiketleri kelime ortasından bölünüyor ("İndirilenl-er") | En uzun kelime sığmazsa yazı en çok %30 küçülür |
| "hurriyet.com.tr" sık ziyaret edilenlerde "Com" | İki parçalı ülke uzantıları tanınıyor (birim testli) |
| Ayar açıklamaları 4 satırda kesiliyor | Sınır kaldırıldı |
| Profil ekle: biyometrik kilit açıklaması dar sütunda | Açıklama anahtarın altında tam genişlikte |
| Yatayda tam ekran sayfalar ekran boyunca uzuyor | İçerik en fazla 720 dp, ortada |
| **"Bağlantıyı kes" uygulamayı çökertiyor** (VPN çekirdeği çıktı okuyucusu) | Okuma kapanışta beklenen hatayı yakalar; 3 bağlan/kes turunda çökme yok |

Doğrulananlar: WebAPK (Squoosh) kuruldu ve kendi penceresinde açıldı; VPN bileşeni indirilip kuruldu; geçersiz
bağlantıda yazarken uyarı; ulaşılamayan sunucuda "Bağlantı testi yapılamadı. Sunucuya ulaşılamadı. Bağlantı
bilgilerini kontrol edin veya başka bir sunucu deneyin."; çökme raporu bir sonraki açılışta soruldu ve önizlemede
maskeli yığın iziyle göründü. Görüntüler: `test-results/ui/sonra/200-*`, `yatay-*`, `webapk-*`, `vpn-*`, `cokme-*`.

Açık: VPN bileşeni indirilirken yalnızca yüzde gösteriliyor (kalan süre yok); eski bir hatalı adres
("Olmayan-alan-adi-gt") düzeltmeden önce geçmişe yazıldığı için sık ziyaret edilenlerde duruyor (uzun basıp
"Kaldır" ile silinebilir; otomatik silme, internet yokken gerçek geçmişi de silebileceği için yapılmadı).
