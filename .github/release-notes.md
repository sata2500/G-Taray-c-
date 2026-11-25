## G-Tarayıcı 0.23.0 — Gizli kasa

### Gizli kasa (yeni)
- Gizli sekmelerde indirdiklerin istersen telefonun İndirilenler klasörü yerine **şifreli bir kasaya** kaydedilir. Açmak için: Ayarlar → Gizlilik → "Gizli indirmeleri şifrele" (ya da Gizli indirmeler ekranındaki anahtar).
- Kasa bir galeri ve dosya yöneticisidir: fotoğraflar, videolar, sesler, belgeler ayrı filtrelerle; küçük resimler, video süreleri.
- Dosyalar şifresi çözülmeden, uygulamanın içinde açılır:
  - Fotoğraflar: iki parmakla yakınlaştırma, çift dokunuşla büyütme, yana kaydırarak sonraki dosya.
  - Videolar ve sesler: ileri/geri sarmalı oynatıcı.
  - PDF'ler sayfa sayfa, metin dosyaları (txt, srt, json…) doğrudan.
- Telefondan dosya ekleyebilirsin ("Dosya ekle"); eklenen dosya kasaya şifreli kopyalanır.
- Uzun basınca: başka uygulamada aç, paylaş, şifresiz dışa aktar, yeniden adlandır, sil.
- Kasa, gizli sekmelerin kilidiyle (parola, parmak izi ya da ekran kilidi) açılır; ekran kapanınca yeniden kilitlenir. Menüye "Gizli kasa" kutucuğu da eklenebilir.

Nasıl korunuyor: her dosyanın kendi 256 bit anahtarı var ve dosya 64 KB'lık, ayrı ayrı doğrulanan AES-256-GCM parçaları halinde saklanıyor. Anahtarlar telefonun donanım destekli Anahtar Deposu'yla korunuyor. Dosya adları ve küçük resimler de şifreli. Açarken şifre yalnızca bellekte çözülüyor; video ileri sarılırken bile şifresiz kopya diske yazılmıyor. Kasa telefon yedeklerine girmiyor.

### VPN ve indirmeler
- "VPN yalnızca gizli sekmelerde" açıkken indirmeler de sekmeyi izler: gizli sekmeden başlatılan indirmeler (YouTube dahil) VPN'den, normal sekmeden başlatılanlar hız için doğrudan bağlantıyla iner.
