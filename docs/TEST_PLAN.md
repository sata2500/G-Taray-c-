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
|   |   |   |   |
