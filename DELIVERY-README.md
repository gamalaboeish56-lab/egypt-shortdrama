# Egypt Short Drama — ملف التسليم

## محتويات المشروع

| المجلد | إيه هو |
|---|---|
| `android/` | تطبيق أندرويد (Kotlin + Android Studio) — WebView للموقع + Firebase + دفع يدوي + AdMob + إشعارات |
| `admin/` | لوحة التحكم (ويب) — بتترفع مع الموقع على Vercel وتفتح من `https://egypt-shortdrama.top/admin/` |
| `firebase/` | قواعد أمان Firestore والـ indexes |
| `paymob-later/` | كود Paymob جاهز (مش شغال لحد ما الـ Merchant يتفعل) |
| `.well-known/assetlinks.json` | علشان روابط الموقع تفتح جوه التطبيق مباشرة |
| `index.html`, `app.html`, ... | الموقع الحالي + تصليحات بسيطة (تحت) |

## اللي اتعمل في التطبيق

- **WebView** لـ `https://egypt-shortdrama.top` بكل مميزات الـ PWA (الـ 5 تبويبات بتاعة الموقع زي ما هي: الرئيسية، المبدعين، الاشتراكات، التحميلات، حسابي).
  - كاميرا ومايك جاهزين للمستقبل (بيطلب الإذن وقت الاستخدام بس، ولموقعنا بس).
  - يوتيوب بملء الشاشة، رفع ملفات، تحميل ملفات، زرار الرجوع، صفحة "مفيش إنترنت"، فتح واتساب/انستجرام/تليفون في تطبيقاتهم.
- **Firebase Auth**: Google + رقم الموبايل (SMS) + إيميل وباسورد (+ نسيت كلمة السر). المستخدم يقدر يكمل كضيف.
- **Firestore**: `users`, `subscriptions`, `coins`, `videos` (+ `admins` للصلاحيات).
- **الدفع اليدوي (مطلوب دلوقتي)**: لما المستخدم يدوس "اشترك في ..." في الموقع جوه التطبيق، بتفتح صفحة دفع حقيقية:
  - InstaPay `abonada5555@instapay` مع QR + زرار نسخ + فتح تطبيق InstaPay.
  - فودافون كاش `01090834756` + زرار يفتح كود التحويل `*9*7*الرقم*المبلغ#`.
  - الباقات: 50ج = 100 عملة، 100ج = 250 + 50 هدية، 200ج = 600 عملة.
  - المستخدم يرفع سكرين شوت ← الطلب يروح `subscriptions` بحالة `pending` ← الأدمن يوافق ← العملات تتضاف تلقائياً ويتسجل في `coins`.
  - المستخدم بيشوف حالة طلباته (قيد المراجعة / اتفعّل / مرفوض + السبب).
- **AdMob**: بانر تحت + إعلان بيني (مرة كل 3 دقايق كحد أقصى، عند التنقل بين التبويبات). المشتركين مبيشوفوش إعلانات.
  نسخة الـ debug بتستخدم إعلانات جوجل التجريبية (علشان الحساب ميتقفلش).
- **إشعارات FCM**: كل الأجهزة مشتركة في topic اسمه `all`. والـ token بيتحفظ في `users` للإرسال لشخص واحد.
- **حظر المستخدم**: لو الأدمن حظره، التطبيق بيقفل عليه فوراً.

## لوحة التحكم (admin/)

طلبات الدفع (صورة التحويل + موافقة/رفض) • المستخدمين (بحث، إضافة/خصم عملات، تغيير الباقة، حظر) • كل الاشتراكات • حركة العملات • الفيديوهات (إضافة/تعديل/حذف) • طريقة إرسال الإشعارات • إحصائيات (عدد المستخدمين، الطلبات، الإيراد).

الحماية في `firebase/firestore.rules`: المستخدم ميقدرش يغير رصيده ولا يفك حظره ولا يدي نفسه باقة، وسعر الطلب لازم يطابق الباقة.

---

## خطوات التشغيل (مرة واحدة)

### 1) Firebase — ✅ متجهز ومتجرب (مشروع `egypt-shortdrama`)
- Android app `egypt.shortdrama.top` + بصمات SHA، و `google-services.json` الحقيقي جوه `android/app/`.
- Auth: Email/Password + Google + Phone (رقم تجربة: `+20 1000000000` / كود `123456`).
- Firestore (eur3) + القواعد `firebase/firestore.rules` + 3 indexes.
- Web app، وإعداداتها في `admin/firebase-config.js`.
- اتجرب: تسجيل حساب ← طلب دفع ← موافقة الأدمن ← 300 عملة + باقة المحترف + سطر في سجل العملات. ومحاولات الغش (تزوير السعر/الرصيد/الباقة) اترفضت كلها.

**إضافة أدمن جديد:** يدخل لوحة التحكم بإيميله ← تظهرله رسالة فيها الـ UID ← Firestore ← كولكشن `admins` (في أول القاعدة) ← **Ajouter un document** ← ID = الـ UID ← حقل `email`.

**مهم بعد أول رفع على Google Play:** Play Console ← App integrity ← App signing ← انسخ SHA-1 و SHA-256 بتوع **App signing key** وضيفهم في Firebase (Project settings ← تطبيق الأندرويد ← Add fingerprint). من غيرهم الدخول بـ Google والموبايل مش هيشتغل في النسخة اللي من المتجر.

**SMS حقيقي للدخول بالموبايل:** محتاج ترقية المشروع لخطة **Blaze** (الدفع حسب الاستخدام — رخيصة جداً). لحد كده رقم التجربة بس هو اللي شغال.

البصمات المسجلة: Upload key SHA-1 `F2:D7:2A:B6:C5:E6:D3:12:21:A4:0E:33:F3:8A:A1:88:85:63:1B:71` — SHA-256 `A0:28:7C:FD:5A:DB:C2:73:8F:5F:6B:0F:D4:89:89:3B:D8:13:B3:9C:FF:31:3D:71:B3:5B:09:2C:AA:EC:E8:B1`.

### 2) AdMob
في `android/gradle.properties`:
- `ADMOB_APP_ID` = الـ App ID (شكله `ca-app-pub-2622507176826354~XXXXXXXXXX`)
- `ADMOB_BANNER_ID` = `ca-app-pub-2622507176826354/8850243528`
- `ADMOB_INTERSTITIAL_ID` = وحدة Interstitial منفصلة (لازم تتعمل في AdMob — الوحدة الواحدة ليها نوع واحد)
- ارفع ملف `app-ads.txt` على الدومين (AdMob بيديهولك).

### 3) البناء
افتح مجلد `android/` في Android Studio، أو من الترمينال:
```
cd android
gradlew assembleDebug        # APK للتجربة  -> app/build/outputs/apk/debug/
gradlew assembleRelease      # APK موقّع      -> app/build/outputs/apk/release/
gradlew bundleRelease        # AAB لجوجل بلاي -> app/build/outputs/bundle/release/
```
التوقيع: `android/upload-keystore.jks` + `android/keystore.properties` (فيه الباسورد).
**احتفظ بالملفين دول في مكان آمن ومترفعهمش على GitHub** (موجودين في .gitignore). لو ضاعوا مش هتقدر تحدث التطبيق (إلا عن طريق طلب reset للـ upload key من جوجل).

### 4) الموقع على Vercel
التعديلات مرفوعة على فرع `android-app-admin` في GitHub. لما تعمل **Merge** للفرع ده في `main`، Vercel هينشر:
لوحة التحكم على `https://egypt-shortdrama.top/admin/` + تصليحات الـ PWA (manifest/sw/sitemap) + `.well-known/assetlinks.json`.
مجلدات `android/`, `firebase/`, `paymob-later/` مش بتتنشر على الموقع (`.vercelignore`).

### 5) Google Play
- ارفع `app-release.aab` (مش الـ APK).
- مطلوب: سياسة خصوصية (رابط)، صور الشاشة، أيقونة 512×512، صورة Feature 1024×500، Data safety (بنجمع: إيميل/موبايل/اسم، صور تحويل، Device ID للإعلانات).
- الحسابات الشخصية الجديدة على Play لازم تعمل **closed testing لـ 12 مختبر لمدة 14 يوم** قبل النشر للعامة.

---

## إضافة Paymob لاحقاً (لما Merchant 1248669 يتفعل)

1. في Paymob dashboard: هات Secret key، Public key، HMAC، و Integration ID للكارت وللمحافظ.
2. انقل `paymob-later/api` لجذر الريبو (يبقى `api/paymob/...`) وانقل/ادمج `paymob-later/package.json` في الجذر.
3. في Vercel > Environment Variables ضيف:
   `PAYMOB_SECRET_KEY`, `PAYMOB_PUBLIC_KEY`, `PAYMOB_HMAC_SECRET`, `PAYMOB_CARD_INTEGRATION_ID`, `PAYMOB_WALLET_INTEGRATION_ID`,
   `FIREBASE_SERVICE_ACCOUNT` (Firebase > Project settings > Service accounts > Generate new private key — حط محتوى الـ JSON كله).
4. في Paymob > Integration: خلّي Transaction processed callback = `https://egypt-shortdrama.top/api/paymob/webhook`.
5. في `android/gradle.properties`: `PAYMOB_ENABLED=true` وابني نسخة جديدة (زوّد `versionCode`).
6. زرار "فيزا" في صفحة الدفع هيشتغل: السيرفر يعمل intention ← المستخدم يدفع في صفحة Paymob ← الـ webhook يتأكد من HMAC والمبلغ ويضيف العملات أوتوماتيك.

---

## ملاحظات مهمة

- الموقع الحالي (React المضغوط) فيه **محاكاة**: الرصيد والتحميلات والاشتراك بيتحفظوا في ذاكرة الصفحة بس. التطبيق بيحول زرار الاشتراك للدفع الحقيقي،
  وبيعرض الرصيد الحقيقي فوق (من Firestore). علشان الموقع نفسه يعرض الرصيد الحقيقي ويفتح الحلقات بالعملات لازم يتعدل كود الموقع الأصلي (مش النسخة المضغوطة) يقرأ من
  `window.egyptAppUser` / `AndroidApp.getUser()` أو من Firebase مباشرة — دي مرحلة تانية.
- صورة الـ QR دلوقتي متولدة من عنوان InstaPay. لو عندك QR الرسمي من تطبيق InstaPay حطه في `android/app/src/main/res/drawable/instapay_qr.png` وهيظهر بداله.
- الأيقونة الحالية مؤقتة — استبدلها بلوجو أبو ندى (Android Studio > New > Image Asset).
- صور التحويل بتتخزن مضغوطة جوه Firestore (مش محتاجين Firebase Storage اللي بقى مدفوع).
