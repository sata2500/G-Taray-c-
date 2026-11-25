## G-Tarayıcı 0.22.0 — Daha güçlü indirme yöneticisi

### İndirilenler ekranı
- YouTube indirmeleri artık başladığı anda İndirilenler'de görünür; ilerlemesini bildirim yerine buradan da izleyebilir, buradan iptal edebilirsin.
- Süren indirmeler üstte "Sürüyor" bölümünde, bitenler altta.
- Bir indirme başarısız olursa nedeni ve ne yapabileceğin yazılır (ör. "Bağlantının süresi dolmuş olabilir: sayfayı yenileyip tekrar deneyin", "Bu video korumalı (DRM)").
- Parça parça yayınlanan videolarda (HLS) ilerleme çubuğu doğru ilerler.

### Gizli indirmeler gizli kalır
- Gizli sekmelerde indirdiklerin yalnızca gizli sekmelerdeyken açılan "Gizli indirmeler" ekranında görünür; normal sekmelerin indirmeleri arasında görünmez (ve tersi).

### Daha fazla dosya, daha çok site
- "Sayfadaki dosyalar" çok daha fazla dosya türünü tanır (altyazılar, e-kitaplar, yazı tipleri, torrent, arşivler, uygulama paketleri, ofis belgeleri ve daha fazlası).
- Gömülü oynatıcılardaki dosyalar oynatıcının kendi adresiyle istenir; birçok sitede önceden "HTTP 403/404" veren indirmeler artık çalışır.
- Adı .m3u8 ile bitmeyen video akışları ve oynatıcının kendisinin yüklediği büyük medya dosyaları da bulunur.

### Daha hızlı
- Çok bağlantılı indirmede dosya, bağlantı sayısından fazla parçaya bölünür: işini bitiren bağlantı sıradaki parçayı alır, yavaş bir bağlantı indirmenin sonunu bekletmez.
- HLS videolarında parçalar daha fazla eşzamanlı bağlantıyla indirilir.
- Görüntüsü ve sesi ayrı akan videolar (birçok sitede yüksek kaliteler böyledir) ikisi birden indirilip tek bir MP4'te birleştirilir; artık sessiz video inmez.
- Bölüm bölüm (byte-range) yayınlanan akışlar desteklenir.

Not: Telif korumalı (DRM) yayınlar ve canlı yayınlar indirilemez.
