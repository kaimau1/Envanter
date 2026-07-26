# 🥫 Envanter — Mutfak Son Kullanma Tarihi Takibi

Marketten alınan gıdaların son kullanma tarihlerini takip ederek israfı önleyen,
sade ve pratik bir Android uygulaması.

## Özellikler

- **🎤 Basılı tut, konuş (en pratik yol):** ana sayfanın sağ alt köşesindeki mikrofon
  tuşuna basar basmaz dinlemeye başlar (sistem diyaloğu açılmaz), parmağını çekince
  konuşmayı analiz eder. **Parmak basılı olduğu sürece kesilmez:** cihazın ses
  tanıyıcısı sessizlikte oturumu kapatsa bile metin biriktirilip dinleme sürdürülür,
  yani cümleler arasında rahatça durabilirsin. Tek üründe de, "2 litre süt, 3 adet yumurta ve bir paket ekmek"
  gibi çok üründe de çalışır. Onay ekranı **ancak analiz bittikten sonra** açılır,
  böylece konuşurken araya ekran girmez. Konuştuğun metin tuşun üstünde canlı görünür.
- **Tek satırda dört ekleme yolu:** ✏️ elle, 📷 fotoğrafla, 🎥 videoyla, 🎤 sesle.
  Ayrı "çoklu ekleme" tuşu yok — **kaç ürün olduğunu sistem kendisi ayırt eder:**
  tek ürün çıkarsa doğrudan ekleme formu, birden fazla çıkarsa kontrol listesi açılır.
  📷 ve 🎥 tuşlarını **basılı tutarsan** galeriden seçer.
- **Çoklu ürün desteği:**
  - 🎥 **Video:** markette/mutfakta çektiğin videoyu Gemini baştan sona izler, gördüğü
    tüm ürünleri çıkarır (aynı ürün birçok karede görünse bile tek kayıt olur)
  - 📷 **Fotoğraf:** tek fotoğrafta birden çok ürün olabilir; galeriden aynı anda
    20 fotoğrafa kadar seçip hepsini analiz ettirebilirsin
  - 🎤 **Ses:** "2 litre süt, 3 adet yumurta ve bir paket ekmek" dersen üçü de ayrı ayrı eklenir
  - Bulunanlar önce bir **kontrol listesinde** gösterilir: adını/kategorisini/miktarını/tarihini
    düzeltip istemediklerinin işaretini kaldırabilir, sonra hepsini tek dokunuşla eklersin
  - Video ya da fotoğrafta tarih okunamazsa Gemini o ürüne makul bir raf ömrü tahmini yazar
    (tahmini tarihler `~` ile işaretlenir)
- **Çekerken sesli anlat:** video kaydı sırasında kameranın okuyamadığı bilgiyi
  (silik son kullanma tarihi, poşetin içindeki ürün, adet/ağırlık) yüksek sesle
  söyleyebilirsin. Gemini videonun sesini de dinler ve söylenen bilgiyi görüntüye
  tercih eder; sadece sesli sayılan ürünler de listeye girer.
- **Uygulama içi 720p kayıt:** video artık sistem kamerası yerine uygulamanın kendi
  kayıt ekranında çekilir. Sistem kamerası çözünürlük seçtirmediği için dosyalar
  1080p/4K çıkıp gereksiz büyüyordu; 720p'de dosya birkaç kat küçülüyor, yükleme
  hızlanıyor ve etiketler hâlâ okunur kalıyor.
  - **Duraklat / devam et:** dolabın başka rafına geçerken kaydı duraklatıp kaldığın
    yerden sürdürebilirsin; parçalar tek video olarak birleşir. Duraklatılan saniyeler
    videoya girmediği için 60 sn sınırından ve token maliyetinden de düşmez.
- **Video token tüketimi kontrol altında:** Gemini video token'ını videonun **süresinden**
  hesaplar (dosya boyutundan ya da çözünürlükten değil): saniyede 1 kare × 258 token +
  32 token/sn ses ≈ **290 token/sn**. Bu yüzden:
  - Kayıt **en fazla 60 sn**
  - **Ayarlar → Video Analiz Kalitesi** ile kare örnekleme sıklığı seçilir:
    Yüksek (1 kare/sn, ~290 token/sn) · **Dengeli** (0,5 kare/sn, ~161 token/sn, varsayılan) ·
    Tasarruf (0,5 kare/sn + düşük kare çözünürlüğü, ~65 token/sn)
  - Analiz sırasında videonun süresi ve **tahmini token tüketimi** ekranda gösterilir
  - Not: 720p'ye inmek dosyayı küçültür ama token'ı azaltmaz — Gemini kareleri
    zaten kendi içinde küçültüyor. Token'ı azaltan şey kısa video ve seyrek kare örnekleme.
- **Tarihi olmayan ürünler de ana sayfada görünür** (tarih sıralamalarında en sona düşerler);
  başlıkta kaç ürünün tarihi eksik olduğu yazar, dokununca tarih ekleyebilirsin
- **Renk kodlu envanter:** tarihi çok yaklaşan/geçen ürünler 🔴 kırmızı, yaklaşanlar 🟡 sarı,
  sorunsuzlar normal görünür
- **Kategoriye özel eşikler:** konservede 6 ay kala sarı, süt ürünlerinde 5 gün kala sarı,
  çikolatada 1 ay kala sarı… her kategori kendi ömrüne göre değerlendirilir
- **Akıllı arama:** yazım hatalarını tolere eden geniş arama (ör. "yogurt" → "Yoğurt")
- **Sade dashboard:** özet sayaçlar + tarihi en yakın ürünler + hızlı ekleme kısayolları
- **Ana ekran widget'ları:** (1) tarihi yaklaşanları gösteren + hızlı ekleme yapan widget,
  (2) sadece sesle eklemek için boyutlandırılabilir (1x1 / 1x2) "🎤 Sesle Ekle" widget'ı
- **Tema seçimi:** Açık / Koyu / Sistem (Ayarlar'dan)
- **Dashboard kartları filtreler:** "Süresi geçti / Çok yakın / Yaklaşıyor" kartına dokununca
  envanter otomatik o gruba filtrelenir
- **Bildirimler:** tarihi kritikleşen ürünler için günde 2 kez otomatik uyarı
- **Dinamik kategoriler:** kategoriler artık sabit değil, veritabanında tutulur. Gemini
  "kategorileri düzelt" ile yeni kategori ekleyebilir (kendi kırmızı/sarı gün eşikleriyle),
  mevcut kategorilerin eşiklerini düzenleyebilir ve ürünleri yeniden atayabilir — hepsi tek istekte
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
  (çoklu görsel `inline_data`, video için Files API resumable upload,
  `videoMetadata.fps` + `generationConfig.mediaResolution` ile token ayarı)
- Android Photo Picker (izin gerektirmez), kamera foto çekimi (FileProvider),
  CameraX ile uygulama içi 720p video kaydı (ses açık)
- Glance ana ekran widget'ı, WorkManager bildirimleri
- İmza: `app/keystore/envanter-release.keystore` (sabit; debug ve release aynı imzayı kullanır)

> Not: Keystore repo içindedir; bu, "her derlemede aynı imza" pratikliği için bilinçli bir
> tercihtir. Repo herkese açıksa keystore'u gizli tutmak istersen dosyayı secret'a taşıyabilirsin.
