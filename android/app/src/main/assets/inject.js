// بيتحقن في الموقع جوه التطبيق فقط. بيحول زراير "اشترك في ..." لصفحة الدفع الحقيقية بدل المحاكاة.
(function () {
  if (window.__egyptAppInjected || !window.AndroidApp) return;
  window.__egyptAppInjected = true;
  document.documentElement.classList.add('in-android-app');

  function findButton(el) {
    while (el && el !== document.body) {
      if (el.tagName === 'BUTTON' || el.getAttribute && el.getAttribute('role') === 'button') return el;
      el = el.parentElement;
    }
    return null;
  }

  document.addEventListener('click', function (e) {
    var btn = findButton(e.target);
    if (!btn) return;
    var t = (btn.textContent || '').trim();
    // زرار الباقة في تبويب الاشتراكات
    if (t.indexOf('اشترك في') === 0) {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      var plan = t.indexOf('المحترف') > -1 ? 'pro' : t.indexOf('النجم') > -1 ? 'star' : t.indexOf('المبتدئ') > -1 ? 'basic' : '';
      AndroidApp.openPayment(plan);
      return;
    }
    // التنقل بين التبويبات = فرصة لإعلان بيني (التطبيق نفسه بيحدد المعدل)
    if (btn.closest && btn.closest('nav')) {
      try { AndroidApp.showInterstitial(); } catch (_) {}
    }
  }, true);

  // إتاحة بيانات المستخدم للموقع
  try {
    window.egyptAppUser = JSON.parse(AndroidApp.getUser());
    window.dispatchEvent(new CustomEvent('egypt-app-user', { detail: window.egyptAppUser }));
  } catch (_) {}
})();
