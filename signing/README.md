# İmzalama

G-Tarayıcı sürümleri kalıcı bir RSA 4096 anahtarıyla, yalnızca **APK İmza Şeması v3** kullanılarak imzalanır
(Android 9 ve üzeri).

`lineage.bin`, ilk deneme sürümlerinde kullanılan anahtardan kalıcı anahtara geçişi kanıtlayan
**anahtar döndürme zinciridir** (signing certificate lineage). Yalnızca sertifikalar ve imzalar içerir,
gizli anahtar içermez; bu yüzden depoda durabilir. Bu sayede eski sürümleri kullananlar da uygulamayı
kaldırmadan, verilerini kaybetmeden yeni sürüme geçebilir.

Kalıcı anahtarın kendisi **asla** depoya girmez. Otomatik derleme (GitHub Actions) onu depo gizli
değişkenlerinden okur:

| Gizli değişken          | İçerik                                                         |
|-------------------------|----------------------------------------------------------------|
| `GT_KEYSTORE_BASE64`    | `gtarayici-release.p12` dosyasının base64 hali                 |
| `GT_KEYSTORE_PASSWORD`  | Anahtar deposu parolası                                        |

Elle imzalamak için:

```bash
./gradlew :app:assembleRelease
GT_KEYSTORE_FILE=/yol/gtarayici-release.p12 GT_KEYSTORE_PASSWORD=... scripts/sign-release.sh dist
```

Kalıcı sertifikanın SHA-256 parmak izi:
`81:15:99:95:2D:2E:C3:94:C2:4C:F1:D9:83:6D:36:59:30:73:5A:81:F3:59:A9:17:C2:56:88:F5:40:72:D1:AC`
