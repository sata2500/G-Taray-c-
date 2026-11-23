## G-Tarayıcı 0.16.0

**Ekran kapalıyken sıradaki video**
- YouTube ve YouTube Music'te ekran kapalıyken sıradaki video açılıyor ama başlamıyordu. Motor sıradaki video için
  otomatik oynatma izni istiyor; izinleri yanıtlayan bileşen ekran görünmezken çalışmadığı için istek yanıtsız
  kalıyordu. Artık ekran kapalıyken otomatik oynatma istekleri site izinlerine göre yanıtlanır.
- Ek güvence: bir video bittikten sonra açılan yeni video birkaç saniye içinde başlamazsa oynatılır.

**Resim içinde resim**
- Video küçük pencereye bazen geç kopyalanıyordu (özellikle YouTube'da video alttaki küçük oynatıcıdayken); erken
  vazgeçildiği için küçük pencerede sayfa görünüyordu. Artık daha uzun beklenir ve bir kez yeniden denenir; bu
  sürede ve kopyalanamazsa sayfa yerine videonun son karesi gösterilir.

**Tam ekran yönü**
- Tam ekran videoda ekran videonun yönüne döner: yatay video yatay, dikey video (Shorts gibi) dikey açılır.
  Yüklü web uygulamalarında da geçerli.

**Web uygulaması simgeleri**
- Ana ekrana yüklenen web uygulamalarının simgeleri artık Android'in uyarlanabilir simgesiyle oluşturulur;
  başlatıcının kutusunu doldurur, küçük görünmez. Var olan web uygulamalarında yeni simge için uygulamayı
  menüden ("Uygulama olarak yükle") yeniden yükleyin.

**Diğer**
- Sekme grubu başlığındaki düğmeler sağ kenara hizalandı.
