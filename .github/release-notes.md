## G-Tarayıcı 0.7.1 — Cihaz testi düzeltmeleri

Uygulama bir telefonda baştan sona test edildi; bulunan 29 sorun düzeltildi.

**Öne çıkanlar**
- Paket yaklaşık 10 MB daha küçüldü (kullanılmayan iki Mozilla kütüphanesi çıkarıldı)
- Sayfa yüklenemeyince (adres bulunamadı, bağlantı yok) artık Türkçe hata sayfası ve "Yeniden dene" çıkıyor
- "Arka planda oynatma" kapalıyken uygulamadan çıkınca video gerçekten duruyor; bu ve SponsorBlock ayarları
  artık doğru uygulanıyor
- Adres çubuğunda Türkçe karakterli adresler okunur görünüyor; önerilerde ve aramalarda "istanbul" yazınca
  "İstanbul" da bulunuyor (ayarlarda da: "cubuk" → "çubuğu", "pip")
- Geçmişte başlıksız kayıtlar ve çevrilmiş başlıklar düzeltildi

**İndirmeler**
- YouTube indirme seçeneklerinde yaklaşık dosya boyutu görünüyor; aynı adlı ikinci dosya doğru açılıyor
- İndirilenler'de "Dosyayı sil" ile "Yalnızca listeden kaldır" ayrıldı; silme yalnızca seçilen dosyayı siliyor

**Diğer**
- Koyu temada ilk açılış rehberi okunur; bildirim izni rehberden sonra soruluyor
- Okuma görünümünde güvenli sitelerde kilit simgesi doğru görünüyor
- Eklenti kurarken izinler Türkçe ve anlaşılır biçimde listeleniyor
- Uygulama yükleme izni verilip dönülünce web uygulaması kurulumu kendiliğinden sürüyor
- Ekran okuyucu (TalkBack) için etiketler ve birçok küçük düzeltme
