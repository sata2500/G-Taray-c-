# G-Tarayıcı

**Gizli. Hızlı. Sade.** — Android için gizlilik odaklı, açık kaynak bir internet tarayıcısı.

G-Tarayıcı, Mozilla'nın **GeckoView** motoru üzerine kuruludur. Amacı, kurduğunuz anda hiçbir ayar yapmadan
sizi koruyan, reklamsız ve hızlı bir tarama deneyimi sunmaktır.

> Durum: **Aşama 1 (temel tarayıcı)** tamamlandı. Proje aktif geliştirme aşamasındadır.

## Özellikler

### Şu an çalışanlar (v0.1.0)

- GeckoView (Firefox) motoru: modern web uyumluluğu, Firefox güvenlik güncellemeleri
- **Varsayılan olarak açık gizlilik:**
  - Sıkı izleyici koruması (izleyiciler, parmak izi toplayıcılar, kripto madencileri, sosyal izleyiciler)
  - Yalnızca HTTPS modu
  - Global Privacy Control (GPC)
  - URL'lerdeki izleme parametrelerinin temizlenmesi
  - Gizlilik dostu varsayılan arama motoru (DuckDuckGo)
- Tek elle kullanım için **alttan adres çubuğu**
- Sekmeler ve **gizli sekmeler** (kapatınca iz bırakmaz, mor tema ile ayırt edilir)
- Başlangıç sayfası ve yer imi kısayolları
- Geçmiş ve yer imleri (arama, silme)
- Yer imleri ve geçmişten anlık adres önerileri
- İndirme yöneticisi (bildirimli, arka planda indirme)
- Site izinleri (kamera, mikrofon, konum, bildirim) için soran diyaloglar
- Uzun basma menüsü (yeni sekmede aç, bağlantıyı kopyala, resmi indir…)
- Dosya yükleme, giriş/onay diyalogları ve diğer web istemleri
- Tam ekran video
- Masaüstü sitesi modu
- Açık, koyu ve sistem teması (Android 12+ cihazlarda dinamik renkler)
- Varsayılan tarayıcı olarak ayarlanabilme
- Uygulama yeniden açıldığında sekmelerin geri yüklenmesi

## Yol haritası

| Aşama | İçerik | Durum |
|---|---|---|
| 1. Temel | GeckoView, sekmeler, alttan adres çubuğu, geçmiş, yer imleri, gizli mod, indirmeler | ✅ Tamamlandı |
| 2. Profiller | Ayrı çerez, depolama ve geçmişe sahip kullanıcı profilleri; biyometrik kilitli profiller ve gizli sekmeler | ⏳ Sırada |
| 3. Gizlilik | Hazır kurulu uBlock Origin, eklenti (Firefox Add-ons) desteği, çerez onay pencerelerini otomatik reddetme, özel DNS (DNS-over-HTTPS) | ⏳ |
| 4. Medya ve YouTube | Resim içinde resim, arka planda oynatma, YouTube video ve ses indirme, SponsorBlock | ⏳ |
| 5. PWA | Web uygulamalarını yükleme: önce kısayol, sonra cihaz üzerinde üretilen gerçek APK | ⏳ |
| 6. VPN | Tarayıcıya ve profile özel VPN (sing-box: WireGuard, Shadowsocks, VLESS, VMess, Trojan, Hysteria) | ⏳ |
| 7. Kullanışlılık | Okuma modu, cihaz üzerinde çeviri, QR okuyucu, kullanıcı betikleri, sekme grupları, kendi kendini güncelleme | ⏳ |

## Teknoloji

| Bileşen | Sürüm |
|---|---|
| Tarayıcı motoru | GeckoView 160 |
| Mozilla Android Components | 160.0.1 |
| Dil | Kotlin 2.4 |
| Arayüz | Jetpack Compose (BOM 2026.09) + Material 3 |
| Veritabanı | Room 2.8 |
| Derleme | Gradle 9.9, Android Gradle Plugin 9.5 |
| Android | En az Android 8.0 (API 26), hedef Android 17 (API 37) |

## Proje yapısı

```
app/src/main/java/com/gtarayici/browser/
├── GTarayiciApplication.kt   # Uygulama başlangıcı, sekmelerin geri yüklenmesi
├── Components.kt             # Motor, durum deposu ve kullanım senaryoları (tek örnek)
├── MainActivity.kt           # Tarayıcı özellikleri (oturum, indirme, istemler, izinler…)
├── data/                     # Room veritabanı: geçmiş ve yer imleri
├── downloads/                # Arka plan indirme servisi
├── search/                   # Arama motorları ve adres çubuğu girdisi
├── settings/                 # Uygulama ayarları
└── ui/                       # Compose arayüzü (araç çubuğu, menü, ekranlar, tema)
```

## Derleme

Gereksinimler: JDK 21 ve Android SDK (platform 37.2, build-tools 37).

```bash
# Hata ayıklama sürümü
./gradlew assembleDebug

# Optimize edilmiş sürüm
./gradlew assembleRelease
```

Her işlemci mimarisi için ayrı APK üretilir (`app/build/outputs/apk/`). Günümüz telefonlarının
neredeyse tamamı için doğru dosya `app-arm64-v8a-release.apk` dosyasıdır.

### İmzalama

Release sürümü, proje kök dizinindeki `keystore.properties` dosyasından veya ortam değişkenlerinden
okunan anahtarla imzalanır. Bu dosya ve anahtarlar **asla repoya eklenmez**.

```properties
storeFile=gtarayici.jks
storePassword=...
keyAlias=gtarayici
keyPassword=...
```

Ortam değişkenleri: `GT_KEYSTORE_FILE`, `GT_KEYSTORE_PASSWORD`, `GT_KEY_ALIAS`, `GT_KEY_PASSWORD`.
Anahtar bulunamazsa release sürümü yalnızca deneme amaçlı olarak debug anahtarıyla imzalanır.

## Lisans

G-Tarayıcı, [GNU Genel Kamu Lisansı v3.0](LICENSE) (GPL-3.0-or-later) ile lisanslanmış özgür bir yazılımdır.
GeckoView ve Mozilla Android Components, Mozilla Public License 2.0 ile lisanslıdır.
