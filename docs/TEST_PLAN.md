# G-Tarayıcı cihaz test planı

Telefon bilgisayara USB ile bağlıyken, uygulamanın hata ayıklama sürümü kurulup tüm özellikler
ekran görüntüleri ve `logcat` kayıtlarıyla baştan sona denenir. Bulunan her sorun bu belgenin sonundaki
tabloya yazılır ve düzeltilir.

## Hazırlık

1. Telefonda: **Ayarlar → Telefon hakkında → Yazılım bilgisi → Yapım numarası**'na 7 kez dokunun
   (geliştirici seçenekleri açılır), ardından **Geliştirici seçenekleri → USB hata ayıklama**'yı açın.
   vivo'da ayrıca **"USB ile yükleme"** ve **"USB hata ayıklama (Güvenlik ayarları)"** açık olmalıdır.
2. Bilgisayarda: JDK 21 ve Android SDK (platform-tools, platform 37.2, build-tools 37) kurulu olmalı.
3. Bağlantıyı doğrulayın:

   ```bash
   adb devices            # cihaz "device" olarak görünmeli (telefonda izin penceresini onaylayın)
   ```

4. Hata ayıklama sürümünü kurun. Bu sürüm `com.gtarayici.browser.debug` paket adıyla, **normal uygulamanızın
   yanına** kurulur; verilerinize dokunmaz.

   ```bash
   ./gradlew :app:installDebug
   adb shell am start -n com.gtarayici.browser.debug/com.gtarayici.browser.MainActivity
   ```

5. Kayıt ve ekran görüntüsü:

   ```bash
   adb logcat -c && adb logcat -v time | grep -iE "gtarayici|GeckoView|Gecko|AndroidRuntime|FATAL" > test.log &
   adb exec-out screencap -p > ekran.png
   ```

## Denenecekler

Her adımda: ekran görüntüsü (açık tema, koyu tema, gizli sekme), `logcat`'te hata/çökme yok, beklenen davranış.

### 1. Kurulum ve ilk açılış
- [ ] İlk açılış rehberi (3 sayfa): Atla, İleri, "Varsayılan tarayıcı yap", Başla
- [ ] Bildirim izni bir kez sorulur
- [ ] uBlock Origin kendiliğinden kurulur (Menü → Eklentiler)
- [ ] Açılış süresi: `adb shell am start -W ...` ile soğuk açılış ölçümü (TotalTime)

### 2. Temel tarama ve arayüz
- [ ] Ana sayfa: arama kutusu, QR düğmesi, sık ziyaret edilenler (birkaç site gezdikten sonra), yer imleri;
      uzun basma → yeni sekmede aç / kaldır
- [ ] Adres çubuğu: yazınca öneriler (yer imi, geçmiş), adres/arama ayrımı, temizle
- [ ] **Kaydırınca gizlenen adres çubuğu**: uzun bir sayfada aşağı/yukarı; harita sitesinde (çubuk kıpırdamamalı);
      YouTube'un alt menüsü çubukla birlikte hareket etmeli; kısa sayfada en alttaki öğe erişilebilir olmalı
- [ ] Aşağı çekerek yenile; tam ekran videoda devre dışı
- [ ] Adres çubuğunu sağa/sola kaydırarak sekme değiştirme (hassasiyet)
- [ ] Sekme sayacına uzun basma (yeni / gizli / kapat), adres çubuğuna uzun basma (kopyala / yapıştır ve git)
- [ ] Menü ızgarası ve sayfa işlemleri; menünün tek ekrana sığması
- [ ] Kilit simgesi → site bilgisi: engellenen izleyici sayısı, siteye özel korumayı kapat/aç, verileri temizle,
      izinleri sıfırla
- [ ] Sayfada bul, masaüstü sitesi, bağlantıyı kopyala, paylaş, yer imi ekle/kaldır

### 3. Sekmeler
- [ ] Sekme ekranı: önizlemeler, normal/gizli sekmeler, tümünü kapat
- [ ] Kartı yana kaydırarak kapatma + "Geri al" çubuğu
- [ ] Sekme grupları: uzun bas → gruba ekle, yeni grup, renk, ad, daralt, gruba yeni sekme, grubu dağıt;
      gruptaki sekmeden açılan bağlantı gruba katılmalı; uygulama yeniden açılınca gruplar korunmalı
- [ ] Sekme uyutma: 30 dk beklemeden test için `adb shell dumpsys meminfo com.gtarayici.browser.debug` ile
      çok sekmede bellek; uyutulan sekmeye dönünce sayfa yeniden yüklenmeli

### 4. Gizlilik ve güvenlik
- [ ] Reklam/izleyici engelleme (ör. bir haber sitesi), çerez penceresinin otomatik reddi
- [ ] Yalnızca HTTPS (http:// adresi), Global Privacy Control
- [ ] Güvenli DNS: sağlayıcı seçimi, özel adres, katı mod
- [ ] Uygulama parolası; kilitli gizli sekmeler; kilitli profil; gizli sekmelerde ekran görüntüsü engeli
- [ ] Tarama verilerini temizle → "Geri al" kapanan sekmeleri getirmemeli
- [ ] WebRTC sızıntı testi (ör. browserleaks.com/webrtc) VPN açık ve kapalıyken

### 5. Profiller
- [ ] Profil oluştur, renk, kilit, geçiş, sil; profiller arasında çerez/oturum ayrımı (aynı siteye iki farklı hesap)

### 6. Eklentiler
- [ ] addons.mozilla.org'dan eklenti kurma (ör. Dark Reader), izin penceresi, gizli sekmede çalıştırma, kaldırma
- [ ] uBlock Origin paneli (menüdeki eklenti simgesi)

### 7. YouTube ve medya
- [ ] Reklamsız oynatma; SponsorBlock atlaması
- [ ] Arka planda oynatma (uygulamadan çıkınca, ekran kapalıyken); bildirim ve kilit ekranı kontrolleri,
      önceki/sonraki; oynatma listesinde ekran kapalıyken sonraki video
- [ ] Resim içinde resim: ana ekran hareketi, **alt çubuğu yana kaydırarak uygulama değiştirme**, hareketi yarıda
      bırakma (PiP açılmamalı), PiP düğmeleri, PiP'i kapatma (oynatma durmalı), büyütüp geri dönme;
      Ayarlar → Gelişmiş → PiP tanılama kaydı
- [ ] Video indirme: 1080p MP4, M4A, Opus; bildirimde ilerleme; dosyanın İndirilenler'de görünmesi

### 8. Web uygulamaları (WebAPK)
- [ ] YouTube'u uygulama olarak yükle; simge ve ad; açılış; kapsam dışı bağlantıda üst çubuk
- [ ] Web uygulamasında arka planda oynatma, PiP, **kenardaki indirme düğmesi** (taşıma, soluklaşma)

### 9. Okuma, çeviri, QR
- [ ] Okuma görünümü: simge, aç/kapat, yazı tipi/boyut/renk ayarları
- [ ] Sayfa çevirisi: öneri çubuğu, dil seçimi, ilk model indirmesi, orijinali göster
- [ ] QR tarayıcı: kamera izni, adres içeren kod, düz metin (ara/kopyala)

### 10. İndirilenler, geçmiş, yer imleri
- [ ] Dosya indirme, İndirilenler ekranı
- [ ] Geçmiş: gün grupları, arama, tek tek silme, tümünü temizle
- [ ] Yer imleri: düzenle (ad, adres, klasör), klasöre taşı, klasör yeniden adlandır/kaldır, arama

### 11. Ayarlar
- [ ] Kategoriler ve arama ("dns", "pip", "çubuk" gibi)
- [ ] Her anahtarın gerçekten etkili olması (özellikle hareketler, sekme uyutma, otomatik PiP)

### 12. VPN
- [ ] VPN ekranı → "VPN bileşeni gerekli" → İndir ve kur (GitHub sürümünden, ~20 MB) → kurulum onayı
- [ ] sing-box bağlantısı (VLESS/Trojan vb. paylaşım bağlantısı), bağlantı testi (çıkış IP'si)
- [ ] OpenVPN (.ovpn + kullanıcı adı/parola)
- [ ] VPN koparsa trafik dışarı sızmamalı (sunucuyu kapatıp deneme)

### 13. Güncelleme
- [ ] Ayarlar → Güncellemeler → denetle (hata ayıklama sürümünde otomatik denetim kapalıdır, elle denenir)

### 14. Boyut ve hız deneyleri
- [ ] `libmegazord.so` (8 MB) ve `libcrashtools.so` (2,4 MB) paketten çıkarılıp uygulamanın açıldığı ve
      tüm özelliklerin çalıştığı doğrulanır (`packaging.jniLibs.excludes`); çalışıyorsa kalıcı yapılır
- [ ] Başlangıç profili (Baseline Profile) üretimi: açılış ve kaydırma akıcılığı için
- [ ] Uzun kullanımda bellek ve pil (birkaç saat YouTube + 10 sekme)

## Bulunan sorunlar

| # | Ekran / özellik | Sorun | Durum |
|---|---|---|---|
| 1 | İlk açılış rehberi | Koyu temada sayfa başlıkları siyah çiziliyor, okunmuyor (ekran `Surface` yerine düz `background` kullanıyordu, metne içerik rengi gelmiyordu) | Düzeltildi (`OnboardingScreen.kt`) |
| 2 | İlk açılış / bildirim izni | Bildirim izni penceresi, rehberin ilk sayfasının üstünde, kullanıcı rehberi görmeden açılıyor | Düzeltildi: izin rehber bitince isteniyor (`MainActivity.kt`, `BrowserController.kt`) |
| 3 | Erişilebilirlik | Rehber veya tam ekran pencere açıkken arkadaki adres çubuğu ve menü düğmesi ekran okuyucuya görünüyor | Düzeltildi: kapalı arayüz erişilebilirlik ağacından gizleniyor (`BrowserApp.kt`) |
| 4 | Eklentiler | Motorun yerleşik "Mozilla Android Components - ReaderView" eklentisi listede görünüyor ve kapatılabiliyor; kapatılırsa okuma görünümü bozulur | Düzeltildi: tüm yerleşik eklentiler listeden gizleniyor (`ExtensionManager.kt`) |
| 5 | Yardımcı eklenti ↔ uygulama | logcat'te her 2 sn'de `Native manifests are not supported on android`. Yardımcı eklentide `geckoViewAddons` izni olmadığı için Gecko `connectNative`/`sendNativeMessage`'ı uygulamaya değil masaüstü yoluna yolluyor. Sonuç: arka planda oynatma/SponsorBlock ayarları eklentiye hiç ulaşmıyor (hep "açık"), PiP için video konumu uygulamaya gelmiyor | Düzeltildi: izin eklendi, eklenti sürümü 2.0.1 (`helper/manifest.json`) |
| 6 | Adres çubuğu | Türkçe karakterli adresler kodlu görünüyor (`tr.wikipedia.org/wiki/%C4%B0stanbul`) | Düzeltildi: yol kısmı gösterimde çözülüyor; alan adı, ayraçlar ve görünmez/yön karakterleri kodlu kalıyor (`SearchEngine.kt`, `DisplayUrlTest`) |
| 7 | Geçmiş kaydı | Geçmişte başlıksız kayıtlar. İki ayrı neden: (a) adres yazılınca sekmenin adresi yükleme başlamadan değişiyor ve o an kaydediliyordu; (b) ikinci testte görüldü: sayfa kendi adresini `history.replaceState` ile yeniden yazınca (Vikipedi `/wiki/Istanbul` → `/wiki/%C4%B0stanbul`) sekme başlığı sıfırlanıyor, belge değişmediği için başlık yeniden gelmiyor ve yeni adres başlıksız yazılıyor | Düzeltildi: (a) yalnızca yüklemesi tamamlanan sayfalar kaydediliyor; (b) aynı sekmede aynı sitede başlık boş gelirse önceki başlık taşınıyor (`HistoryRecorder.kt`). İlk düzeltme (a) tek başına yetmedi; ikinci cihaz testinde (b) bulundu |
| 8 | Adres çubuğu önerileri | "ista" yazınca ziyaret edilen "İstanbul - Vikipedi" önerilmiyor: SQLite `LIKE` yalnızca ASCII harfleri katlıyor, adres de kodlu saklanıyor | Düzeltildi: eşleştirme Türkçe harfleri tanıyan `TextMatch` ile, başlık ve okunur adres üzerinde (öneriler, Geçmiş ve Yer imleri araması; `TextMatchTest`) |
| 9 | Menü → yer imi ekle/kaldır | Menü kapanıyor ama işlemin yapıldığına dair hiçbir geri bildirim yok ("Bağlantıyı kopyala" ise bildirim gösteriyor) | Düzeltildi: "Yer imlerine eklendi / kaldırıldı" bildirimi (`BrowserController.kt`, `strings.xml`) |
| 10 | Erişilebilirlik: "Yeni sekme", "Profil ekle", "VPN sunucusu ekle" düğmeleri | Genişletilmiş FAB'ın metni erişilebilirlik ağacına aktarılmıyor; ekran okuyucu düğmeyi etiketsiz okuyor (uiautomator: `NAF="true"`, içi boş) | Düzeltildi: ortak `AddFab` bileşeni, etiket düğmenin kendisinde (`OverlayScaffold.kt`, üç ekran) |
| 11 | Erişilebilirlik: menüdeki eklenti düğmesi (uBlock Origin) | Simge düğmesinin etiketi yok | Düzeltildi: eklentinin düğme başlığı etiket olarak veriliyor (`MenuSheet.kt`) |
| 12 | Erişilebilirlik: sekme grubu ve profil renk seçicileri | Renk daireleri erişilebilirlik ağacında yok (etiketsiz `clickable`), seçili renk bildirilmiyor | Düzeltildi: "Renk 3 / 8" etiketi ve radyo düğmesi seçili durumu (`ColorOption.kt`). Not: profil renk daireleri 28 dp, önerilen 48 dp dokunma alanının altında; düzen değişikliği gerektirdiği için açık bırakıldı |
| 13 | Ayarlarda arama | "pip" yalnızca "PiP tanılama kaydı"nı buluyor, asıl "Otomatik resim içinde resim" ayarını bulmuyor; Türkçe harfsiz yazınca ("cubuk") hiçbir şey bulunmuyor | Düzeltildi: aksan duyarsız eşleştirme (ç/ğ/ı/ö/ş/ü), son ünsüz yumuşaması ("çubuk" → "çubuğu") ve kısaltmalar (pip, doh, reklam) (`SettingsScreen.kt`, `TextMatch.kt`); aynı katlama adres çubuğu önerilerinde de geçerli ("turkiye" → "Türkiye") |
| 14 | Ayarlar → Gizli sekmeleri kilitle | Açıklama "biyometrik doğrulama istenir" diyor; uygulama parolası ayarlıyken kilit parolayla da açılıyor | Düzeltildi: "parmak izi, yüz veya uygulama parolası istenir" (`strings.xml`) |
| 15 | Erişilebilirlik: araç çubuğundaki profil rozeti | Ekran okuyucu yalnızca baş harfi ("I") okuyor; hangi profil olduğu ve düğmenin işi anlaşılmıyor | Düzeltildi: "Profil değiştir: Is" etiketi (`Toolbar.kt`) |
| 16 | Eklenti kurma izin penceresi | İzinler ham teknik adlarıyla listeleniyor ("• tabs", "• <all_urls>"); kullanıcı eklentinin neye erişeceğini anlayamıyor | Düzeltildi: Firefox'taki gibi Türkçe açıklamalar ("Tüm web sitelerindeki verilerinize erişme", "Tarayıcı sekmelerine erişme"); uyarı gerektirmeyen izinler gizli, siteler alan adıyla (`ExtensionPermissions.kt`, `ExtensionPermissionsTest`) |
| 17 | Eklentiler → eklentiye dokununca açılan menü | Menü dokunulan satırın yanında değil, ekranın en altında açılıyor (menü satırla aynı kutuda değildi) | Düzeltildi (`ExtensionsScreen.kt`) |
| 18 | Ayarlar → Arka planda oynatma (kapalı) | Ayar kapalıyken de YouTube uygulamadan çıkınca çalmaya devam ediyor (cihazda: medya oturumu PLAYING, ses akışı `started`). Olası neden: uygulama arka plana geçince motor oturumu etkin bırakılıyor (`setActive(false)` yok), sayfa hiçbir zaman "gizli" olmuyor; eklentinin ayara göre gerçek görünürlüğü döndürmesi bu yüzden etkisiz; PiP'in yaşam döngüsü gözlemcisi de ayara bakmıyor | Düzeltildi: ayar uygulama tarafında uygulanıyor — PiP'e geçilmeden uygulamadan çıkılınca (ekran kapanınca da) oynayan medya duraklatılıyor; tarayıcıda ve yüklü web uygulamalarında (`BackgroundPlayGuard.kt`). Cihazda doğrulandı: kapalıyken PLAYING → ana ekran → PAUSED; açıkken ana ekranda PLAYING sürüyor |
| 19 | YouTube indirme ve profil değiştirme pencereleri | "Geri" ilk basışta pencereyi kapatmıyor, yarı açık konuma indiriyor (diğer pencerelerde `skipPartiallyExpanded` var, bu ikisinde yok). Testte pencere açık kalınca sonraki tuşlar bir indirme seçeneğini tetikledi ve istenmeden indirme başladı | Düzeltildi: tek "Geri" ile kapanıyor (`FindAndDownload.kt`, `ProfilesScreen.kt`). Test sırasında yanlışlıkla inen iki dosya telefondan silindi |
| 20 | YouTube indirme penceresi | Seçeneklerde dosya boyutu yok; 25 dakikalık bir videoda 1080p yüzlerce MB olabiliyor | Düzeltildi: bilindiğinde yaklaşık boyut gösteriliyor ("M4A · 128 kbps · ~25 MB") (`YouTubeDownloader.kt`, `YouTubeExtractTest`) |
| 21 | İndirilenler → "Sil" | Kayıt listeden kalkıyor ama dosya telefonda kalıyor; "Sil" adı yanıltıcı, onay da sorulmuyor | Düzeltildi: tamamlanan indirmede "Dosyayı sil / Yalnızca listeden kaldır" sorusu; dosya MediaStore kaydıyla birlikte siliniyor (`DownloadedFiles.kt`, `LibraryScreens.kt`) |
| 22 | YouTube indirme → aynı adlı ikinci dosya | Sistem ikinci dosyaya "(1)" ekliyor ama listeye istenen ad yazılıyor; ikinci kayıt açılınca ilk dosya açılıyor | Düzeltildi: kaydedilen gerçek ad MediaStore'dan okunuyor (`YouTubeDownloadService.kt`) |
| 23 | Uygulamayı yükle (WebAPK) → izin | "Bilinmeyen uygulamaları yükle" izni verilip dönülünce yükleme sürmüyor; kullanıcı menüden baştan başlamak zorunda | Düzeltildi: dönüşte izin verildiyse yükleme kendiliğinden sürüyor, verilmediyse istek bırakılıyor (`BrowserController.kt`, `MainActivity.kt`) |
| 24 | Okuma görünümü → adres çubuğu | Güvenli (HTTPS) bir sitede okuma görünümüne geçince kilit yerine uyarı üçgeni çıkıyor (okuma sayfası yerel bir eklenti sayfası, motor onu "güvensiz" sayıyor) | Düzeltildi: gösterge içeriğin çekildiği özgün adrese göre veriliyor (`BrowserApp.kt`) |
| 25 | Sayfa yükleme hataları | Alan adı bulunamayınca, bağlantı yokken ya da HTTPS'e yükseltme başarısız olunca hiçbir hata sayfası çıkmıyor; sekme önceki sayfada kalıyor, kullanıcı hiçbir geri bildirim almıyor (logcat: `OnLoadError`). QR koduyla açılan bir adres bu yüzden sessizce başarısız oldu | Düzeltildi: Mozilla'nın yerelleştirilmiş hata sayfaları (`browser-errorpages`, `ErrorPageInterceptor.kt`, `Components.kt`). Hata sayfası şablonunun dili belirsiz olduğu için motor "Bu sayfa İngilizce" diye çeviri öneriyordu; hata sayfalarında çeviri önerisi ve okuma simgesi gizleniyor (`BrowserApp.kt`) |
| 26 | Geçmiş / Yer imleri araması | Aramaya uyan kayıt yoksa "Henüz geçmiş yok" / "Henüz yer imi yok" yazıyor; liste aslında boş değil | Düzeltildi: aramada "Aramanızla eşleşen bir şey bulunamadı" (`LibraryScreens.kt`) |
| 27 | Geçmiş + sayfa çevirisi | Çevrilen sayfanın belge başlığı da çevriliyor ve geçmişe bu başlık yazılıyor (`en.wikipedia.org/wiki/Istanbul` → "İstanbul - Vikipedi") | Düzeltildi: çevrilmiş sekmede başlık güncellenmiyor (`HistoryRecorder.kt`) |
| 28 | Boyut deneyi (14. bölüm) | `libmegazord.so` (açılmış 22,6 MB) ve `libcrashtools.so` (6 MB) pakette ama kullanılmıyor | Kalıcı yapıldı: `packaging.jniLibs.excludes`. Kütüphaneler olmadan YouTube, indirme, eklentiler, çeviri, okuma görünümü ve dosya indirme cihazda denendi; `UnsatisfiedLinkError` ve çökme yok (`app/build.gradle.kts`) |

### Bilinen davranışlar (hata sayılmadı)

- Vikipedi gibi sayfalar adreslerini `history.replaceState` ile yeniden yazınca geçmişte aynı sayfa için iki kayıt
  oluşur (ilk ve yeniden yazılmış adres); ikisi de artık doğru başlıkla görünür.
- `m.youtube.com`, Firefox kimliğiyle gelen sayfada web uygulaması bildirimi (manifest) vermiyor; bu yüzden
  YouTube "bildirimsiz site" olarak, kendi penceresinde açılan uygulama şeklinde yüklenir.
- PDF görüntüleyicinin "Download" etiketi Gecko'nun pdf.js bileşeninden gelir, İngilizcedir.
- Material 3'ün genişletilmiş FAB'ı metnini erişilebilirlik ağacına aktarmıyor (bkz. 10); uygulama tarafında aşıldı.
- Hata ayıklama sürümünde otomatik güncelleme denetimi kodda kapalıdır; ayardaki anahtar yalnızca bu sürümde etkisizdir.

## Test sonuçları (2026-11-19/20, vivo V2244, Android 15, hata ayıklama sürümü)

| Bölüm | Durum | Not |
|---|---|---|
| 1. Kurulum ve ilk açılış | ✅ | Soğuk açılış 2,6–4,3 sn (ilk açılış / sekme sayısına göre) |
| 2. Temel tarama ve arayüz | ✅ | Harita sitesi ve YouTube alt menüsüyle çubuk hareketi elle denenmeli |
| 3. Sekmeler | ✅ | Uyutma birim testiyle (`TabUnloaderTest`); 30 dk'lık cihaz denemesi yapılmadı |
| 4. Gizlilik ve güvenlik | ✅ | WebRTC sızıntı testi VPN gerektirir, yapılmadı |
| 5. Profiller | ✅ | Kilitli profilin parmak iziyle açılması elle denenmeli |
| 6. Eklentiler | ✅ | |
| 7. YouTube ve medya | ✅ kısmen | Arka planda oynatma, PiP, SponsorBlock, indirme denendi. Ekran kapalıyken oynatma, kilit ekranı kontrolleri, PiP hareketleri ve PiP düğmeleri elle denenmeli |
| 8. Web uygulamaları (WebAPK) | ⏳ | "Bilinmeyen uygulamaları yükleme" izni kullanıcı tarafından verilmeli |
| 9. Okuma, çeviri, QR | ✅ | QR: adres içeren gerçek bir kod okundu; düz metin kodu denenmedi |
| 10. İndirilenler, geçmiş, yer imleri | ✅ | |
| 11. Ayarlar | ✅ | Hareketler, arka planda oynatma, SponsorBlock, otomatik PiP anahtarlarının etkisi ölçüldü |
| 12. VPN | ⏳ | Arayüz denendi; bileşen kurulumu (izin) ve sunucular kullanıcıda |
| 13. Güncelleme | ✅ | Elle denetim: "G-Tarayıcı güncel (0.7.0)" |
| 14. Boyut ve hız | ✅ kısmen | Kütüphane deneyi kalıcı (28). Baseline Profile üretimi ve saatlerce pil/bellek ölçümü yapılmadı |
