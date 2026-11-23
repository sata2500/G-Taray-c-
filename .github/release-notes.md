## G-Tarayıcı 0.16.1

**Reklam engelleyici açılışta**
- Uygulama açılırken önceki sekmeler artık reklam engelleyici (uBlock Origin) yüklendikten sonra açılır. Önceden
  ilk sayfa bazen engelleyici hazır olmadan açılıyor, ancak yenileyince reklamlar engelleniyordu.

**VPN açılışta**
- VPN açık bırakıldıysa sekmeler VPN bağlanınca açılır (en fazla 8 sn beklenir); ilk sayfa artık "bağlantı hatası"
  ile açılmaz.
- Açılışta bağlantı kurulamazsa kendiliğinden iki kez daha denenir.
- VPN sonradan bağlanırsa, bağlantı hatası gösteren sayfalar kendiliğinden yenilenir.

**VPN bildirimi**
- VPN açıkken bildirimlerde "G-Tarayıcı VPN açık" bildirimi durur: bağlı sunucu ve "Bağlantıyı kes" düğmesi.
  Yüklü web uygulamalarından ve diğer uygulamalardan VPN'i kapatmak için tarayıcıya dönmek gerekmez. Bildirime
  dokununca VPN ekranı açılır.
