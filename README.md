# 🥫 Envanter — Mutfak Son Kullanma Tarihi Takibi

Marketten alınan gıdaların son kullanma tarihlerini takip ederek israfı önleyen,
sade ve pratik bir Android uygulaması.

## Özellikler

- **Üç yolla ürün ekleme:** elle, 📷 fotoğrafla (etiket/tarih otomatik taranır), 🎤 sesle
  ("3 adet süt son kullanma 12 ağustos" demen yeterli)
- **Renk kodlu envanter:** tarihi çok yaklaşan/geçen ürünler 🔴 kırmızı, yaklaşanlar 🟡 sarı,
  sorunsuzlar normal görünür
- **Kategoriye özel eşikler:** konservede 6 ay kala sarı, süt ürünlerinde 5 gün kala sarı,
  çikolatada 1 ay kala sarı… her kategori kendi ömrüne göre değerlendirilir
- **Akıllı arama:** yazım hatalarını tolere eden geniş arama (ör. "yogurt" → "Yoğurt")
- **Sade dashboard:** özet sayaçlar + tarihi en yakın ürünler + hızlı ekleme kısayolları
- **Ana ekran widget'ı:** tarihi yaklaşanları evde ekranından gör, tek dokunuşla ekle
- **Bildirimler:** tarihi kritikleşen ürünler için günde 2 kez otomatik uyarı
- **Gemini asistan:** API anahtarını girince model listesi otomatik çekilir; "ne pişirsem",
  "önce ne tüketeyim" gibi analizler yapılır. Fotoğraf ve ses tanıma da anahtar girilince
  Gemini ile daha isabetli çalışır (anahtar yoksa cihaz-içi OCR kullanılır).
- **Firebase senkron:** yapılandırılınca envanter cihazlar arasında otomatik eşitlenir
  (yapılandırılmazsa uygulama tamamen çevrimdışı çalışır)
- **Sabit imza:** APK'lar hep aynı anahtarla imzalanır, her yeni sürüm veri kaybı olmadan
  üstüne kurulur

## APK indirme

Her derleme [Releases](../../releases) sayfasına imzalı APK olarak eklenir.

- **Elle tetikleme:** GitHub → Actions → "APK Derle ve Yayınla" → *Run workflow*.
  Derleme bitince release oluşur.
- Her push da otomatik derleme başlatır.

### E-posta bildirimi (isteğe bağlı, 1 kez kurulum)

Her APK hazır olduğunda mail gelmesi için repo **Settings → Secrets and variables → Actions**
altına iki secret ekle:

| Secret | Değer |
|---|---|
| `MAIL_USERNAME` | Gmail adresin |
| `MAIL_PASSWORD` | Gmail **uygulama şifresi** (myaccount.google.com/apppasswords) |

Secrets eklenmezse derleme yine çalışır, sadece mail adımı atlanır.
Ayrıca repoyu **Watch → Custom → Releases** yaparsan GitHub da her release'te mail atar.

## Firebase senkron kurulumu (isteğe bağlı, 1 kez)

1. [console.firebase.google.com](https://console.firebase.google.com) → yeni proje oluştur
2. Android uygulaması ekle, paket adı: `com.envanter.app`
3. İndirilen `google-services.json` dosyasını `app/google-services.json` üzerine yaz
4. Firebase Console'da **Authentication → Anonymous** girişini ve **Cloud Firestore**'u etkinleştir
5. Firestore kuralları (her kullanıcı yalnız kendi verisini görür):
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```
6. Yeni APK derlet (Actions'tan) — senkron otomatik devreye girer

## Gemini asistan kurulumu (isteğe bağlı)

1. [aistudio.google.com/apikey](https://aistudio.google.com/apikey) → ücretsiz anahtar al
2. Uygulamada **Ayarlar → Gemini API** bölümüne yapıştır → "Kaydet ve Modelleri Getir"
3. Listeden istediğin modeli seç (varsayılan: gemini-2.0-flash)

## Teknik

- Kotlin + Jetpack Compose (Material 3), tek modül
- Room (yerel veritabanı, çevrimdışı-öncelikli) + Firestore (senkron)
- ML Kit cihaz-içi OCR, Android ses tanıma, Gemini REST API
- Glance ana ekran widget'ı, WorkManager bildirimleri
- İmza: `app/keystore/envanter-release.keystore` (sabit; debug ve release aynı imzayı kullanır)

> Not: Keystore repo içindedir; bu, "her derlemede aynı imza" pratikliği için bilinçli bir
> tercihtir. Repo herkese açıksa keystore'u gizli tutmak istersen dosyayı secret'a taşıyabilirsin.
