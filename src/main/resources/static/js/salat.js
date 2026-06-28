document.addEventListener("htmx:configRequest", function(evt) {
  const token = document.cookie.split("; ")
    .find(r => r.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
  if (token) {
    evt.detail.headers["X-XSRF-TOKEN"] = decodeURIComponent(token);
  }
});

document.addEventListener('close.bs.alert', e => {
  const wrapper = e.target.parentElement;
  e.target.addEventListener('closed.bs.alert', () => wrapper?.remove(), { once: true });
});

(function () {
  if (window.innerWidth >= 768 && localStorage.getItem('salat-nav-collapsed') === 'true') {
    document.body.classList.add('nav-collapsed');
  }
}());

function toggleNav() {
  const collapsed = document.body.classList.toggle('nav-collapsed');
  localStorage.setItem('salat-nav-collapsed', String(collapsed));
}

function toggleTheme(theme) {
  localStorage.setItem('tabler-theme', theme);
  document.documentElement.setAttribute('data-bs-theme', theme);
}

function selectContract(id) {
  const url = new URL(window.location.href);
  url.searchParams.set('employeeContractId', id);
  window.location.href = url.toString();
}

const tomSelectConfig = (el) => ({
  create: false,
  maxItems: el.classList.contains('tomselect-multi') ? null : 1,
  maxOptions: 1000,
  plugins: ['dropdown_input'],
  sortField: [{ field: '$order' }],
  placeholder: el.getAttribute('placeholder') || 'Select an option...',
});

document.querySelectorAll('select.tomselect').forEach((el) => {
  new TomSelect(el, tomSelectConfig(el));
});

document.addEventListener('htmx:afterSettle', function () {
  document.querySelectorAll('select.tomselect').forEach((el) => {
    if (!el.tomselect) {
      new TomSelect(el, tomSelectConfig(el));
    }
  });
});

document.addEventListener('htmx:afterSettle', function () {
  const raw = document.cookie.split('; ')
    .find(r => r.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
  if (raw) {
    const token = decodeURIComponent(raw);
    document.querySelectorAll('input[name="_csrf"]')
      .forEach(function (el) { el.value = token; });
  }
});

function applyFormTabOrder() {
  var wrapper = document.querySelector('.page-body');
  if (!wrapper) return;
  var els = wrapper.querySelectorAll(
    'a[href]:not([disabled]), button:not([disabled]), input:not([type="hidden"]):not([disabled]):not([readonly]), select:not([disabled]), textarea:not([disabled]):not([readonly])'
  );
  if (!els.length) return;
  els.forEach(function (el, i) {
    (el.tomselect ? el.tomselect.control_input : el).tabIndex = i + 1;
  });
}
applyFormTabOrder();
document.addEventListener('htmx:afterSettle', applyFormTabOrder);
