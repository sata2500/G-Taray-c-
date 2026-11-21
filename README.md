# G-Tarayıcı

**Gizli. Hızlı. Sade.** — Android için gizlilik odaklı, açık kaynak bir internet tarayıcısı.

G-Tarayıcı, Mozilla'nın **GeckoView** motoru üzerine kuruludur. Amacı, kurduğunuz anda hiçbir ayar yapmadan
sizi koruyan, reklamsız ve hızlı bir tarama deneyimi sunmaktır.

> Durum: **v0.7.0** — yol haritasındaki özelliklerin neredeyse tamamı hazır; sürümler kalıcı anahtarla imzalanır ve
> uygulama içinden güncellenir.

## Özellikler

### Gizlilik (kurduğunuz anda açık)
- **uBlock Origin hazır kurulu** gelir (internetsiz, paketle birlikte); gizli sekmelerde de çalışır
- Sıkı izleyici ve parmak izi koruması, yalnızca HTTPS, Global Privacy Control
- **Çerez onay pencerelerini otomatik reddetme**
- URL'lerdeki izleme parametrelerinin temizlenmesi
- **Güvenli DNS (DNS-over-HTTPS):** Cloudflare, Quad9, AdGuard, Mullvad, NextDNS veya özel adres; katı mod
- WebRTC IP sızıntısı koruması
- Gizlilik dostu varsayılan arama motoru (DuckDuckGo)

### Profiller ve kilit
- **Kullanıcı profilleri:** her profilin çerezleri, oturumları, sekmeleri, geçmişi ve yer imleri tamamen ayrıdır
- Araç çubuğundan tek dokunuşla profil değiştirme
- **Kilitli profiller:** parmak izi, yüz, cihaz ekran kilidi veya **uygulamaya özel parola/PIN**
  (PBKDF2 ile özetlenir; 5 hatalı denemede 30 sn bekleme)
- İsteğe bağlı **kilitli gizli sekmeler**; gizli sekmelerde ekran görüntüsü engeli

### Eklentiler
- Firefox Eklentileri (addons.mozilla.org) sitesinden doğrudan eklenti kurma
- Eklenti yönetimi: aç/kapat, gizli sekmelerde çalıştır, ayarlar, kaldır; günlük otomatik güncelleme
- Eklenti panelleri (ör. uBlock Origin) menüden alttan açılan sayfada

### Medya ve YouTube
- **Arka planda oynatma:** uygulamadan çıkınca veya ekran kapanınca YouTube çalmaya devam eder
- Bildirim ve kilit ekranında medya kontrolleri
- **Resim içinde resim:** video oynarken uygulamadan çıkınca (tam ekran gerekmeden) küçük pencerede yalnızca video
  gösterilir; pencerede önceki / oynat-duraklat / sonraki düğmeleri. Yüklü web uygulamalarında (WebAPK) da çalışır.
- Bildirim ve kilit ekranında YouTube için **önceki/sonraki** düğmeleri; oynatma listesinde ekran kapalıyken sonraki videoya geçiş
- **SponsorBlock:** sponsor ve tanıtım bölümlerini otomatik atlar (video kimliği gizlilik korumalı sorgulanır)
- **YouTube video/ses indirme:** 1080p'ye kadar MP4 (görüntü + ses cihazda birleştirilir), M4A veya Opus ses

### Web uygulamaları (PWA)
- **Gerçek uygulama olarak yükleme (WebAPK):** Chrome'daki gibi, site cihazda üretilen ve cihaza özel anahtarla
  imzalanan küçük bir APK olarak yüklenir. Uygulama çekmecesinde kendi adı ve simgesiyle görünür, ayarlardan
  kaldırılabilir, tarayıcı arayüzü olmadan kendi penceresinde açılır. Hiçbir sunucuya veri gönderilmez.
- İstenirse yalnızca ana ekran kısayolu

### Tarayıcı VPN'i
- Yalnızca G-Tarayıcı'nın trafiği şifrelenir; cihaz VPN izni gerekmez, diğer uygulamalar ve VPN'ler etkilenmez
- Çekirdekler ayrı bir **VPN bileşeni** paketinde gelir (~20 MB); VPN'i kullanmayanlar indirmez. Bileşen ilk
  kullanımda GitHub sürümünden indirilir ve yalnızca G-Tarayıcı'nın anahtarıyla imzalıysa kabul edilir
- **OpenVPN** (.ovpn; kullanıcı adı/parola, tls-auth, tls-crypt, comp-lzo dahil): gerçek OpenVPN 2.7 istemcisi,
  telefonun VPN izni yerine kullanıcı alanı ağ yığınına (gVisor) bağlanır
- Dahili sing-box çekirdeği: **VLESS (Reality dahil), VMess, Trojan, Shadowsocks, Hysteria2, TUIC, WireGuard,
  SOCKS5 ve HTTP(S) vekil**
- Paylaşım bağlantısı veya abonelik içeriği yapıştırarak ya da `.ovpn` / `.conf` dosyasını içe aktararak sunucu ekleme
- Desteklenmeyenler: IKEv2/IPsec, L2TP, PPTP, OpenVPN TAP
- Bağlantı testi (çıkış IP'si, ülke, gecikme), günlük görüntüleme
- Bağlantı koparsa trafik doğrudan internete düşmez (sızıntı koruması)

### Temel tarama
- Tek elle kullanım için alttan adres çubuğu; yer imleri ve geçmişten anlık öneriler
- Hareketler: adres çubuğunu kaydırarak sekme değiştirme, aşağı çekerek yenileme, sekmeyi yana kaydırarak
  kapatma ("Geri al" ile), sekme sayacına ve adres çubuğuna uzun basınca hızlı menüler
- Kaydırınca gizlenen adres çubuğu; kullanılmayan sekmelerin uyutulması (bellek ve pil tasarrufu)
- Ana sayfada sık ziyaret edilen siteler ve yer imleri; klasörlü, düzenlenebilir yer imleri; gün gün gruplanmış geçmiş
- Kategorili ve aranabilir ayarlar, ilk açılışta kısa tanıtım
- **Site bilgisi:** bağlantı durumu, engellenen izleyiciler, siteye özel izleyici koruması, site verilerini ve
  izinlerini sıfırlama
- Önizlemeli sekme ekranı, gizli sekmeler, sayfada bul, masaüstü sitesi modu
- **Sekme grupları:** adlandırılmış, renkli, daraltılabilir gruplar (profile özel, kalıcı); gruptaki bir sekmeden
  açılan bağlantılar aynı gruba katılır
- **Okuma görünümü:** makaleleri reklamsız, sade bir sayfada okuma; yazı tipi, boyut ve açık/sepya/koyu renk
- **Sayfa çevirisi:** Firefox'un çeviri sistemi; tamamen cihazda yapılır, sayfa metni hiçbir sunucuya gönderilmez
- **QR kod tarayıcı:** adres çubuğundan ve menüden; görüntü cihazda işlenir
- **Uygulama içi güncelleme:** yeni sürümler GitHub Releases'tan denetlenir, imzası doğrulanır ve tek dokunuşla kurulur
- Geçmiş, yer imleri, indirme yöneticisi; site izinleri, dosya yükleme, tam ekran video
- Açık/koyu/sistem teması (Android 12+ dinamik renkler), varsayılan tarayıcı olabilme
- **Anlaşılır mesajlar:** hatalar Türkçe ve ne yapılacağını söyler; silmelerde "Geri al", indirmelerde kalan süre ve
  "Aç", internet kesilince/gelince bilgi, yazarken denetim; bir özelliği ilk kullanırken tek seferlik ipuçları
- **Sorun bildirme:** Menü › Sorun bildir — gönderilecek metni önce görürsünüz; GitHub'da hazır bildirim açılır ya
  da dosya olarak paylaşılır. Tanılama kaydı yalnızca cihazda tutulur ve kişisel bilgiler maskelenir; çökme sonrası
  bildirmek isteyip istemediğiniz sorulur, hiçbir şey kendiliğinden gönderilmez

## Yol haritası

| Aşama | İçerik | Durum |
|---|---|---|
| 1. Temel | GeckoView, sekmeler, adres çubuğu, geçmiş, yer imleri, gizli mod, indirmeler | ✅ |
| 2. Profiller | Ayrı veri alanlı profiller, biyometrik kilit | ✅ |
| 3. Gizlilik | uBlock Origin, eklentiler, çerez pencereleri, güvenli DNS | ✅ |
| 4. Medya | Arka planda oynatma, resim içinde resim, YouTube indirme, SponsorBlock | ✅ |
| 5. PWA | Bağımsız pencere ve cihazda üretilen gerçek APK (WebAPK) | ✅ |
| 6. VPN | sing-box ile tarayıcıya özel VPN | ✅ · Profile özel VPN ⏳ |
| 7. Kullanışlılık | Sayfada bul, okuma modu, çeviri, QR okuyucu, sekme grupları, uygulama içi güncelleme | ✅ |
| 8. Dağıtım | Kalıcı imza anahtarı (anahtar döndürmeli), GitHub Actions ile otomatik sürüm | ✅ |

**Bilinen sınırlamalar:** VPN ve DNS ayarları tüm profiller için ortaktır (GeckoView tek bir ağ yapılandırması kullanır).
WebAPK yüklemek için G-Tarayıcı'ya bir kez "bilinmeyen uygulamaları yükleme" izni verilmelidir.

## Teknoloji

| Bileşen | Sürüm |
|---|---|
| Tarayıcı motoru | GeckoView 160 |
| Mozilla Android Components | 160.0.1 |
| Dil | Kotlin 2.4 |
| Arayüz | Jetpack Compose (BOM 2026.11) + Material 3 Expressive (1.5) |
| Veritabanı | Room 2.8 |
| VPN çekirdeği | sing-box 1.14, OpenVPN 2.7.8 (OpenSSL 3.5 LTS) + Go/gVisor köprüsü |
| YouTube çözümleme | NewPipe Extractor 0.26 |
| Reklam engelleme | uBlock Origin (hazır kurulu) |
| Derleme | Gradle 9.9, Android Gradle Plugin 9.5 |
| Android | En az Android 9 (API 28), hedef Android 17 (API 37) |

## Proje yapısı

```
app/src/main/java/com/gtarayici/browser/
├── GTarayiciApplication.kt   # Uygulama başlangıcı, sekmelerin geri yüklenmesi
├── Components.kt             # Motor, durum deposu ve kullanım senaryoları (tek örnek)
├── MainActivity.kt           # Tarayıcı özellikleri (oturum, indirme, istemler, izinler…)
├── data/                     # Room veritabanı: profiller, geçmiş, yer imleri
├── diagnostics/              # Tanılama kaydı, gizlilik filtresi, çökme raporu, sorun bildirme
├── downloads/                # Arka plan indirme servisi
├── engine/                   # Gecko tercihleri
├── extensions/               # Eklenti yönetimi (uBlock Origin, AMO)
├── media/                    # Medya bildirimi ve arka planda oynatma servisi
├── profiles/                 # Profil yönetimi
├── qr/                       # QR kod tarayıcı (CameraX + ZXing)
├── reader/                   # Okuma görünümü ayarları
├── search/                   # Arama motorları ve adres çubuğu girdisi
├── security/                 # Biyometrik kilit
├── settings/                 # Uygulama ayarları
├── tabs/                     # Sekme grupları
├── thumbnails/               # Sekme önizlemeleri
├── translate/                # Sayfa çevirisi
├── update/                   # Uygulama içi güncelleme
├── vpn/                      # sing-box tabanlı tarayıcı VPN'i ve bağlantı ayrıştırıcı
├── webapp/                   # Yüklenen web uygulamaları (PWA) penceresi
├── webapk/                   # WebAPK üretimi: ikili manifest yamalama, imzalama, yükleme
├── youtube/                  # YouTube indirme ve birleştirme
└── ui/                       # Compose arayüzü: designsystem/ (ortak bileşenler), feedback/ (mesajlar,
                              #   hatalar, ipuçları), navigation/, controller/, screens/, theme/
app/src/main/assets/extensions/
├── ublock_origin.xpi         # Hazır kurulu reklam engelleyici (imzalı, AMO)
└── helper/                   # Arka planda oynatma, resim içinde resim, medya kontrolleri, SponsorBlock
webapk-shell/                 # Cihazda kişiselleştirilen 8 KB'lık WebAPK şablonu (saf Java)
vpn-core/                     # VPN bileşeni: yalnızca VPN çekirdeklerini taşıyan kodsuz paket
baselineprofile/              # Baseline Profile üreticisi ve açılış ölçümü (macrobenchmark)
docs/TEST_PLAN.md             # Cihaz üzerinde baştan sona test planı
native/ovpn-bridge/           # OpenVPN ↔ kullanıcı alanı ağ yığını ↔ SOCKS5 köprüsü (Go)
```

## Derleme

Gereksinimler: JDK 21, Android SDK (platform 37.2, build-tools 37) ve VPN çekirdeği için Go 1.26+.

```bash
# VPN çekirdeklerini bir kez derleyin (vpn-core/src/main/jniLibs); ikili dosyalar repoya eklenmez
./scripts/build-singbox.sh
ANDROID_NDK_ROOT=/yol/android-ndk ./scripts/build-openvpn.sh   # OpenVPN + köprü (NDK gerekir)
```

```bash
# Hata ayıklama sürümü
./gradlew assembleDebug

# Optimize edilmiş sürüm ve VPN bileşeni
./gradlew :app:assembleRelease :vpn-core:assembleRelease
```

### Baseline Profile (açılış hızı)

Uygulamayla birlikte önceden derlenmiş kod profili gelir (`app/src/release/generated/baselineProfiles/`). Yeniden
üretmek veya açılışı ölçmek için bağlı bir telefonda:

```bash
./gradlew :app:generateBaselineProfile
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

Ölçüm, ayrı paket adlı bir kopyayla (`com.gtarayici.browser.benchmark`) yapılır; telefonda kurulu asıl uygulamaya
dokunulmaz.

Her işlemci mimarisi için ayrı APK üretilir (`app/build/outputs/apk/`). Günümüz telefonlarının
neredeyse tamamı için doğru dosya `app-arm64-v8a-release.apk` dosyasıdır.

### İmzalama ve sürüm yayınlama

Sürümler kalıcı bir RSA 4096 anahtarıyla, APK İmza Şeması v3 ve **anahtar döndürme zinciri**
([`signing/lineage.bin`](signing/README.md)) kullanılarak imzalanır. Anahtarın kendisi **asla repoya eklenmez**.

```bash
./gradlew :app:assembleRelease
GT_KEYSTORE_FILE=/yol/gtarayici-release.p12 GT_KEYSTORE_PASSWORD=... scripts/sign-release.sh dist
```

`app/build.gradle.kts` içindeki `versionName` değiştiğinde GitHub Actions
([`.github/workflows/release.yml`](.github/workflows/release.yml)) yerel kütüphaneler dahil her şeyi derler, test eder,
imzalar ve GitHub Releases'ta yayınlar. Sürüm notları `.github/release-notes.md` dosyasından alınır.
Gerekli depo gizli değişkenleri: `GT_KEYSTORE_BASE64`, `GT_KEYSTORE_PASSWORD`.

## Lisans

G-Tarayıcı, [GNU Genel Kamu Lisansı v3.0](LICENSE) (GPL-3.0-or-later) ile lisanslanmış özgür bir yazılımdır.
GeckoView ve Mozilla Android Components MPL-2.0; sing-box, NewPipe Extractor ve uBlock Origin GPL-3.0;
OpenVPN GPL-2.0 (OpenSSL istisnasıyla; uygulamayla birleştirilmez, ayrı bir program olarak çalıştırılır), OpenSSL Apache-2.0, WireGuard-go/gVisor MIT/Apache-2.0, ZXing ve CameraX Apache-2.0 lisanslıdır.
