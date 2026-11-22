## G-Tarayıcı 0.10.1

**Düzeltmeler**
- Mobil veride sayfaların ve resimlerin geç yüklenmesi giderildi. 0.10.0'daki veri tasarrufu modu, tarayıcının
  sunuculara önceden bağlanmasını kapatıyordu ve "Save-Data" başlığını her isteği bekleten bir yöntemle ekliyordu.
  Önceden bağlanma yeniden açık (neredeyse hiç veri harcamaz); başlık artık motorun kendi kurallarıyla, beklemeden eklenir.
- YouTube'un "çocuklar için" işaretli bazı videolarda indirmeyi engellemesi artık açıkça anlatılıyor. Bu, NewPipe
  tabanlı tüm uygulamaları etkileyen YouTube kaynaklı bir değişiklik; kütüphanenin düzeltmesi çıktığında uygulama
  her gece yapılan denetimle kendiliğinden güncellenir.
- Tanılama kaydı veri tasarrufunun ne zaman açılıp kapandığını da yazar.
