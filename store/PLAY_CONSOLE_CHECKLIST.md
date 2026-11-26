# قائمة Play Console (لتجنّب الرفض)

## 0) قبل البناء
1. أنشئ مفتاح الرفع (مرة واحدة، واحتفظ بنسخة احتياطية آمنة):
   `keytool -genkeypair -v -keystore ghost.keystore -alias cuteghost -keyalg RSA -keysize 2048 -validity 10000`
2. حوّله: `base64 -w0 ghost.keystore` وأضف في GitHub Secrets: `GHOST_KEYSTORE_BASE64` و`GHOST_KEYSTORE_PASSWORD`.
3. AdMob: المعرّفات الحقيقية موضوعة في `app/build.gradle.kts` (نسخة release فقط)، ونسخة debug تستخدم معرّفات الاختبار دائمًا. لا تضغط على إعلاناتك الحقيقية أبدًا. ويمكن تجاوز المعرّفات عبر السرّين `ADMOB_APP_ID` و`ADMOB_REWARDED_ID` و`ADMOB_BANNER_ID`.
4. في AdMob: حدّ مستوى المحتوى إلى G وامنع الفئات الحساسة.
5. شغّل Actions ونزّل `CuteGhost-release-aab` وارفعه في Play Console.
6. ارفع مجلد الموقع `cute-ghost` إلى مستودع GitHub Pages، ثم أدخل في Play Console: رابط الخصوصية `https://belalmomani.github.io/GitHubPages/cute-ghost/privacy.html`، ورابط حذف البيانات `https://belalmomani.github.io/GitHubPages/cute-ghost/delete-data.html`، وبريد المطوّر `bilalalmomani90@gmail.com`.

## 1) App content (محتوى التطبيق)
- **Target audience:** اختر فئة «5 سنوات وأقل» فقط (لا تختر فئات أكبر، وإلا تتغير المتطلبات).
- **Ads:** نعم، يحتوي على إعلانات.
- **Advertising ID:** لا (أُزيلت الصلاحية من الـ Manifest).
- **Content rating (IARC):** أجب بلا على العنف والمحتوى الجنسي والمقامرة والمخدرات والتفاعل بين المستخدمين وشراء داخل التطبيق.
- **Data safety:** التطبيق نفسه لا يجمع بيانات. أما AdMob فيعالج بيانات تقنية لتقديم الإعلان؛ طابق إفصاحك مع صفحة إفصاح AdMob لـ Data safety، ولا تُجب بـ«لا بيانات» إن كانت صفحة AdMob تذكر خلاف ذلك.
- **Government / News / Health / Financial:** لا.

## 2) إعلانات الصلاحيات الحساسة (احتمال الرفض الأكبر)
- **Display over other apps (SYSTEM_ALERT_WINDOW):** التبرير: الأشباح العائمة هي الميزة الأساسية للتطبيق، وتُفعَّل بموافقة الأهل.
- **Foreground service type «special use»** (الخدمتان `GhostOverlayService` و`ScreenLockService`): اشرح الاستخدام بنص الـ Manifest نفسه، وسجّل فيديو قصيرًا (حتى دقيقتين) يُظهر: طلب الإذن من الأهل، ظهور الشبح والإشعار، وإيقاف الخدمة.
- **Notifications:** لعرض إشعار الخدمة التي تعمل في المقدمة.

## 3) صفحة المتجر
- أيقونة وصورة رئيسية جاهزتان في مجلد `store/`، ونص الوصف في `STORE_LISTING.md`.
- لقطات شاشة (2 على الأقل) من هاتف حقيقي، بلا لقطات تُظهر لوحة الاختبار.
- لا تذكر في الوصف ميزات غير موجودة، ولا تدّعِ أنه «تعليمي» أو «معتمد» دون دليل.

## 4) الاختبار
- إن كان حسابك شخصيًا وجديدًا فقد تطلب Google اختبارًا مغلقًا بعدد من المختبرين لعدة أيام قبل الإنتاج. تحقق من صفحة Production في حسابك.
- جرّب النسخة على هاتف حقيقي قبل الرفع (لم أستطع بناء المشروع هنا).

## 5) ملاحظات
- `applicationId` (`com.companion.theghost`) لا يتغيّر بعد النشر.
- في أندرويد 16 تُتجاهل قيود الاتجاه (portrait) على الشاشات الكبيرة (أجهزة لوحية وقابلة للطي)؛ جرّب التطبيق على جهاز لوحي أو محاكٍ.
