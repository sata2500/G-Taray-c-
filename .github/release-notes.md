## G-Tarayıcı 0.15.2

**Daha sade menü**
- Ana menüde yalnızca: geri, ileri, yenile, yer imi, paylaş; o an işe yarıyorsa "Videoyu indir" ve "Çevir";
  sayfada bul; masaüstü görünümü; "Tüm seçenekler"; ve yer imleri, geçmiş, indirmeler, ayarlar kısayolları.
- Diğer her şey "Tüm seçenekler" sayfasında, iki başlık altında sade bir liste: Bu sayfa (sonra oku, sesli oku,
  sayfadaki indirilebilir öğeler, ekran görüntüsü, PDF, bağlantıyı kopyala, uygulama olarak yükle) ve Uygulama
  (yeni sekme, gizli sekme, okuma listesi, VPN, eklentiler, profiller, QR kod, sorun bildir).

**Düzeltmeler**
- "PDF olarak kaydet" ile oluşan PDF açılmıyordu: dosyanın başına sayfanın HTML'i yazılıyordu. İndirme yöneticisi
  artık hazır PDF'i adresten yeniden indirmeye kalkmaz, aynı indirmeyi iki kez başlatmaz ve adres dosya yerine web
  sayfası döndürürse kaydetmeden durur. PDF'in başlığı yazılırken doğrulanır.
- "PDF olarak kaydet" PDF hazır olduğunda uygulamayı kapatıyordu (0.15.1'de düzeltildi).
