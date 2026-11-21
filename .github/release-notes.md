## G-Tarayıcı 0.8.0 — Yeni görünüm, anlaşılır mesajlar, sorun bildirme (TASLAK)

Arayüz baştan ele alındı: tek bir tasarım dili, yumuşak geçişler ve her durumda ne olduğunu ve ne yapılacağını söyleyen
mesajlar.

**Yeni görünüm**
- Material 3 Expressive: yaylı, akıcı geçişler; tam ekran sayfalar kayarak açılıp kapanıyor, geri hareketi (destekleyen
  telefonlarda) sayfayı parmakla birlikte küçültüyor
- Ana sayfa yenilendi: tek arama kutusu, koruma durumu çipi, okunur kısayollar
- Menü ve Ayarlar gruplanmış kartlar hâlinde; ayarlarda arama tüm satırları buluyor
- Yeni marka rengi (dinamik renk kapalıysa camgöbeği–yeşil) ve Android 13+ temalı uygulama simgesi
- Koyu temada ana sayfa ve kilit ekranı yazıları artık okunuyor

**Anlaşılır mesajlar**
- Tüm bildirimler tek, tutarlı bir mesaj çubuğunda; kaydırarak kapatılabiliyor
- Hatalar Türkçe ve ne yapılacağını söylüyor ("Sunucuya ulaşılamadı. Bağlantı bilgilerini kontrol edin veya başka bir
  sunucu deneyin"); teknik ayrıntı "Ayrıntılar"da
- Sekme, yer imi ve geçmiş silindiğinde "Geri al"
- İnternet kesilince ve gelince haber veriliyor
- İndirmelerde ilerleme ve kalan süre; bitince "Aç"
- DNS adresi, yer imi adresi ve VPN bağlantısı yazarken denetleniyor
- Bir özelliği ilk kullandığınızda tek seferlik kısa ipuçları (Ayarlar › Genel'den kapatılabilir)

**Sorun bildirme**
- Menü › Sorun bildir: tür seçin, isterseniz açıklama yazın; site alan adı, ekran görüntüsü ve tanılama kaydı isteğe
  bağlı. Gönderilecek metni önce görürsünüz; GitHub'da hazır bildirim açılır ya da dosya olarak paylaşılır
- Tanılama kaydı yalnızca telefonda tutulur, adresler alan adına indirgenir, e-posta/IP/kimlikler gizlenir; Ayarlar ›
  Tanılama ve sorun bildirme'den kapatılabilir veya temizlenebilir
- Uygulama beklenmedik şekilde kapanırsa bir sonraki açılışta bildirmek ister misiniz diye sorulur; hiçbir şey kendiliğinden
  gönderilmez

**Düzeltmeler**
- "Gizli sekmelerde ekran görüntüsünü engelle" ayarı artık uygulamayı yeniden başlatmadan uygulanıyor
- Açılamayan adresler geçmişe ve sık ziyaret edilenlere girmiyor; aynı adlı siteler ayırt ediliyor ("Wikipedia (tr)")
- Eklenti kaldırırken onay soruluyor (geri alınamaz)
- Renk seçicilerde daha büyük dokunma alanı; kilit ekranı açıkken arkadaki sayfa ekran okuyucuya kapalı
- Büyük yazı boyutunda sekme sayacı, kutucuk etiketleri, ayar açıklamaları ve profil penceresi düzgün görünüyor;
  yatay ekranda sayfalar okunur genişlikte
- "hurriyet.com.tr" gibi siteler sık ziyaret edilenlerde doğru adla ("Com" değil) görünüyor
- VPN'de "Bağlantıyı kes" uygulamayı kapatmıyor
- Sekme önizlemeleri ana iş parçacığını yormadan yükleniyor; açılış için önceden derlenmiş kod profili (Baseline Profile)
