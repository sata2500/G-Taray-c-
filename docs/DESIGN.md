# G-Tarayıcı tasarım dili

Bu belge, `docs/UI_REVIEW.md`'deki bulgulara dayanan tasarım dilidir. 2026-11-21'de ana sayfa, menü ve ayarlar
üzerindeki önizlemeyle onaylandı ve uygulamanın tamamına yayılıyor.

## 1. Ses

**Sade, güvenli, sıcak, modern.** Tarayıcıda içerik ön plandadır; arayüz geri çekilir:

- Ekranda aynı anda tek bir "ana eylem" vurgulanır; geri kalan her şey sakin yüzey tonlarındadır.
- Renk, durumu anlatmak için kullanılır (gizli mod moru, güvenli/güvensiz, açık/kapalı), süs için değil.
- Metinler kısa, doğal Türkçe; teknik terim yalnızca ayrıntıda.
- Güven hissi: gizlilik korumasının açık olduğu sakin ama görünür biçimde hatırlatılır.

## 2. Material 3 Expressive — doğrulanmış durum

| Özellik | Material 3 1.4.0 (şu an, BOM 2026.09.00) | Material 3 1.5.0 (BOM 2026.11.01) |
|---|---|---|
| `MaterialTheme(motionScheme = …)` parametresi | var | var |
| `MotionScheme.standard()` / `expressive()` | **dahili** (`$material3`), uygulamadan çağrılamaz | **genel** API |
| `ButtonGroup`, `FloatingToolbar`, `WavyProgressIndicator`, `ToggleButton`, `SplitButton`, `MaterialShapes` | yok | var, **deneysel değil** |
| `LoadingIndicator` | yok | var, `@ExperimentalMaterial3ExpressiveApi` |
| Ayrı `MaterialExpressiveTheme` | yok | yok (Expressive, `MaterialTheme` + hareket şemasıyla gelir) |
| `PredictiveBackHandler` (activity-compose 1.13.0) | var | var |

(Doğrulama: ilgili AAR'ların `classes.jar`'ı `javap` ile incelendi.)

**Öneri:** Compose BOM'u **2026.11.01**'e (Material 3 1.5.0, Compose UI 1.13.0) yükseltmek ve:
- `MotionScheme.expressive()` — yaylı, hafif sekmeli geçişler (tüm uygulama)
- `WavyProgressIndicator` — sayfa yükleme ve uzun işler (indirme, bileşen kurulumu)
- `ButtonGroup` — menüdeki sayfa eylemleri satırı
- `MaterialShapes` — boş durum ve ana sayfa amblemi gibi az sayıdaki "karakter" anı

Deneysel `LoadingIndicator` yalnızca tek bir yerde (YouTube video bilgisi alınırken) ve tek bir sarmalayıcı bileşen
üzerinden kullanılır; deneysel API ileride değişirse tek bir dosya güncellenir.

## 3. Tasarım belirteçleri (`ui/designsystem/Tokens.kt`)

| Belirteç | Değerler |
|---|---|
| **Boşluk** | `xxs 2` · `xs 4` · `sm 8` · `md 12` · `lg 16` · `xl 24` · `xxl 32` · `huge 48` dp; ekran kenar boşluğu `lg` |
| **Şekil** | `xs 8` (çip, rozet) · `sm 12` (küçük kart, menü öğesi) · `md 16` (kart, kutucuk) · `lg 24` (sayfa kartı, bölüm) · `xl 28` (alt sayfa, iletişim penceresi) · `full` (arama kutusu, düğme) |
| **Yazı** | Material 3 yazı ölçeği; başlıklar `titleLarge`/`headlineSmall`, satır başlığı `bodyLarge`, açıklama `bodyMedium` + `onSurfaceVariant`, bölüm başlığı `labelLarge` + `primary` |
| **Yükseklik** | gölge yerine **ton**: zemin `surface`, bölüm kartı `surfaceContainerLow`, kutucuk `surfaceContainerHigh`, seçili `secondaryContainer` |
| **Dokunma alanı** | en az 48 dp (renk daireleri dahil) |
| **Simge** | Material Symbols "Rounded" ailesi (şu an Filled/Outlined karışık); satır simgeleri 24 dp, ton dairesi içinde 40 dp |
| **Hareket** | `MotionScheme.expressive()`: konum için yay (spatial), renk/opaklık için sönümlü yay (effects); tam ekran sayfalar 300–350 ms eşdeğeri; dokunsal geri bildirim: uzun basma, anahtar değişimi, kaydırarak kapatma eşiği |

## 4. Renk

- **Dinamik renk** (Android 12+) korunur: tarayıcı kullanıcının duvar kâğıdı tonunu alır.
- **Yedek marka paleti** (Android 9–11 ve dinamik renk kapalıyken): şu anki Google mavisine çok benziyor. Öneri:
  derin camgöbeği–yeşil bir marka tonu (güven + sakinlik; tohum `#00696D`). **Onaylandı.**
- **Gizli mod:** mevcut mor palet korunur, ancak `surfaceContainer*` tonları tamamlanır ve tüm metinlere içerik
  rengi garanti edilir (G1/G2'nin kökü).
- Karşıtlık: metin/zemin en az 4.5:1; renkler yalnızca `MaterialTheme.colorScheme` üzerinden (ekranlarda `Color(0x…)`
  yasak).

## 5. Hareket ve geçişler

- **Tam ekran sayfalar:** tek bir gezinme katmanı (`ui/navigation/OverlayHost`) — `AnimatedContent` ile sağdan kayarak
  + solarak giriş, geri dönüşte ters yön; `PredictiveBackHandler` ile Android 14+ öngörülü geri hareketinde sayfa
  parmakla birlikte küçülür/kayar.
- **Sekme ↔ sayfa:** sekme kartına dokununca kart ölçeğinden sayfaya büyüme (paylaşılan öğe geçişi); sekme ekranına
  dönüşte ters.
- **Sayfa yükleme:** adres çubuğunun altında ince `WavyProgressIndicator` (şu anki düz çizginin yerine).
- **Dokunsal:** uzun basma, kaydırarak kapatma eşiği, anahtar açma/kapama.

## 6. Ana sayfa ve marka

- Tek arama noktası: sayfanın ortasındaki büyük arama kutusu; alttaki çubuk ana sayfada yalnızca sekme sayacı ve menüyü
  gösterir (iki arama kutusu sorunu, M5).
- Üstte küçük amblem + "G-Tarayıcı"; altında sakin bir **koruma özeti çipi** ("Koruma açık · İzleyiciler ve çerez
  pencereleri engelleniyor") — dokununca gizlilik ayarları.
- Kısayollar: 4'lü ızgara, ton kutucukları (`md` şekil), okunur etiketler; boşsa yol gösteren bir boş durum.
- **Uygulama simgesi:** uyarlanabilir simge korunur; ayrı bir **tek renkli (temalı) katman** eklenir (şu an renkli ön
  plan aynen kullanılıyor, Android 13+ temalı simgede bozuk görünebilir). **Karar:** simge korunur, yalnızca temalı
  katman eklenir.

## 7. Bileşenler (`ui/designsystem/`)

`AppScaffold` (sayfa iskeleti) · `SectionCard` + `SectionHeader` · `NavRow` (simge + başlık + açıklama + ok) ·
`SwitchRow` · `ChoiceRow` · `AppSheet` · `AppDialog` (yalnız geri alınamaz işler) · `EmptyState` (simge, başlık,
açıklama, eylem) · `LoadingState` · `ErrorState` (ne olduğu + ne yapılacağı + "Yeniden dene") · `AppButton` türleri ·
`AddFab` · `SearchField` · `Tile` (menü/ana sayfa kutucuğu) · `StatusChip`.

Kurallar: ekranlarda elle `dp`/`Color` yok; her bileşenin açık/koyu/gizli/büyük yazı `@Preview`'u var; listelerde
`key` verilir, durum seçicileri dar tutulur.

## 8. Geri bildirim

Tek mesaj kanalı (`ui/feedback/Messenger`): başarı / bilgi / uyarı / hata türleri, isteğe bağlı eylem ("Geri al",
"Aç", "Yeniden dene", "Ayarlar"), aynı anda tek mesaj, kuyrukta tekrarlar birleştirilir. Görünüm: alt araç çubuğunun
hemen üstünde, `lg` şekilli, türüne göre simgeli kart. Tüm `Toast`'lar kaldırılır.

## 9. Uygulama durumu (0.8.0)

| Konu | Durum |
|---|---|
| BOM 2026.11.01, `MotionScheme.expressive()`, yedek camgöbeği palet, temalı simge katmanı | Yapıldı |
| Belirteçler (`Tokens.kt`), `SectionCard`/`SectionHeader`/`NavRow`/`SwitchRow`, `Tile`/`TileGrid`/`StatusChip` | Yapıldı |
| `ListRow` (eskimiş `ListItem` yerine, 24 kullanım), `OverlayScaffold`, `AddFab`, `EmptyState` (simge + eylem), `LoadingState` (Expressive `LoadingIndicator`), `ErrorNotice` (ne oldu + ne yapılmalı + "Ayrıntılar"), `ColorSwatch`/`ColorPicker` (48 dp), `rememberFullSheetState` | Yapıldı |
| Tek gezinme katmanı `OverlayHost` (kayarak giriş/çıkış, `PredictiveBackHandler`) | Yapıldı. Not: test telefonu (vivo, OriginOS 6) geri hareketinin ilerlemesini uygulamaya iletmiyor; orada hareket tamamlanınca normal kapanış animasyonu oynar. Stok Android 14+ cihazlarda sayfa parmakla küçülür. |
| Ana sayfa, menü, ayarlar (tek veri modeli), sorun bildirme, tanılama ayarları | Yapıldı |
| Tek mesaj kanalı (`Messenger`): başarı/bilgi/uyarı/hata/ipucu, "Geri al", "Aç"; Android 13+ kopyalama onayı sisteme bırakılır | Yapıldı |
| `@Preview` | Tasarım sistemi bileşenleri, ana sayfa, mesaj kartı, hata bildirimi, ayar satırları, renk seçici, sorun bildirme önizlemesi (açık/koyu/%200 yazı). Uygulama bileşenlerine (`components`) doğrudan bağlı ekranlarda (sekmeler, VPN, eklentiler) önizleme yok — bunlar için önce durumun ekrandan ayrılması gerekiyor. |
| Ana sayfada tek arama noktası (alt çubukta arama kutusu yerine yalnızca sayaç ve menü) | **Yapılmadı** — alt çubuk hâlâ arama kutusunu gösteriyor |
| `WavyProgressIndicator` (sayfa yükleme), `ButtonGroup` (menü eylem satırı), sekme ↔ sayfa paylaşılan öğe geçişi | **Yapılmadı** — sonraki sürüme |
| `AppDialog`: yalnız geri alınamaz işler (veri temizleme, geçmişin tamamını silme, indirilen dosyayı silme, eklenti kaldırma) | Kural uygulandı; bileşen olarak ayrı sarmalayıcı yok (Material `AlertDialog`) |
