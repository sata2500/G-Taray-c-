## G-Tarayıcı 0.16.6

**Ekran kapalıyken sıradaki şarkı (asıl neden)**
- Tanılama kaydı nedeni gösterdi: ekran kapalıyken sıradaki şarkının otomatik oynatma isteği uygulama tarafından
  reddediliyordu ("otomatik oynatma engellendi"). Kayıtlı site izni bulunamayınca varsayılan kurala düşülüyor,
  veri tasarrufu açıkken de engelleniyordu. Artık medya oynatan sekmenin sıradaki şarkı/video için istediği izin,
  siteyi açıkça engellemediyseniz verilir. 0.16.5'teki arka plan oynatma hizmetiyle birlikte YouTube Music ekran
  kapalıyken sıradaki şarkıya geçmelidir.

**İndirmeler iki kez iniyordu**
- Uygulamanın indirme yöneticisi bir indirmeyi başlatınca Mozilla'nın kendi indirme hizmeti de aynı dosyayı ikinci kez
  indiriyordu (gizli sekmede ve normal sekmede; ikinci kopya "(1)" ekiyle kaydediliyordu). Düzeltildi; her indirme
  bir kez iner.
