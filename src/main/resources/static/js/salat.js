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
  url.searchParams.set('employeeContractId', String(id));
  location.href = url.toString();
}

const tomSelectConfig = (el) => {
  const hasSubtext = Array.from(el.options).some(opt => opt.dataset.subtext);
  const favoriteTarget = el.dataset.favoriteTarget || null;

  const config = {
    create: false,
    maxItems: el.classList.contains('tomselect-multi') ? null : 1,
    maxOptions: 1000,
    plugins: ['dropdown_input'],
    sortField: [{ field: '$order' }],
    placeholder: el.getAttribute('placeholder') || 'Select an option...',
    onDropdownOpen(dropdown) {
      dropdown.style.width = 'max-content';
      dropdown.style.minWidth = this.wrapper.offsetWidth + 'px';
      this.control.style.minHeight = '39px';
    },
    onDropdownClose() {
      this.control.style.minHeight = '';
    },
  };

  if (hasSubtext || favoriteTarget) {
    const favoriteId = el.dataset.favoriteId || null;

    Object.assign(config, {
      searchField: ['text', 'subtext'],
      onInitialize() {
        Array.from(el.options).forEach(opt => {
          const val = this.options[opt.value];
          if (!val) return;
          if (opt.dataset.subtext) val.subtext = opt.dataset.subtext;
          if (favoriteTarget) val.isFavorite = !!(favoriteId && opt.value === favoriteId);
        });
        const subtextEl = el.id ? document.getElementById(el.id + '-subtext') : null;
        if (subtextEl) {
          const selected = el.options[el.selectedIndex];
          subtextEl.textContent = selected?.dataset.subtext || '';
        }
        if (favoriteTarget) {
          this.dropdown.addEventListener('click', (e) => {
            if (e.target.closest('.ts-fav')) {
              e.preventDefault();
              e.stopImmediatePropagation();
            }
          }, true);
          this.dropdown.addEventListener('mousedown', (e) => {
            const star = e.target.closest('.ts-fav');
            if (!star) return;
            e.preventDefault();
            e.stopImmediatePropagation();
            const optionEl = star.closest('[data-value]');
            const value = optionEl?.dataset.value;
            if (!value) return;
            const isCurrent = this.options[value]?.isFavorite;
            const newFavId = isCurrent ? null : value;
            const raw = document.cookie.split('; ').find(r => r.startsWith('XSRF-TOKEN='))?.split('=')[1];
            fetch(favoriteTarget + (newFavId ? '?suborderId=' + newFavId : ''), {
              method: 'POST',
              headers: { 'X-XSRF-TOKEN': raw ? decodeURIComponent(raw) : '' },
            }).then(() => {
              Object.keys(this.options).forEach(k => {
                this.options[k].isFavorite = (newFavId !== null && k === newFavId);
              });
              // Direct DOM update — also updates the cached DOM element in place
              this.dropdown_content.querySelectorAll('[data-value] .ts-fav').forEach(starEl => {
                const isFav = newFavId !== null && starEl.closest('[data-value]')?.dataset.value === newFavId;
                starEl.classList.toggle('bi-star-fill', isFav);
                starEl.classList.toggle('text-warning', isFav);
                starEl.classList.toggle('bi-star', !isFav);
                starEl.classList.toggle('opacity-25', !isFav);
              });
            });
          }, true);
        }
      },
      onChange(value) {
        const subtextEl = el.id ? document.getElementById(el.id + '-subtext') : null;
        if (subtextEl) {
          subtextEl.textContent = (value && this.options[value]?.subtext) || '';
        }
      },
      render: {
        option(data, escape) {
          const starHtml = favoriteTarget
            ? '<i class="bi ' + (data.isFavorite ? 'bi-star-fill text-warning' : 'bi-star opacity-25')
              + ' ts-fav ms-auto flex-shrink-0 ps-2" style="cursor:pointer;font-size:1rem"></i>'
            : '';
          return '<div class="d-flex align-items-center py-1">'
            + '<div class="d-flex flex-column flex-grow-1">'
            + '<span class="text-nowrap">' + escape(data.text) + '</span>'
            + (data.subtext ? '<small class="text-muted lh-1 mb-1">' + escape(data.subtext) + '</small>' : '')
            + '</div>'
            + starHtml
            + '</div>';
        },
        item(data, escape) {
          return '<div>' + escape(data.text) + '</div>';
        },
      },
    });
  }

  return config;
};

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

/* ─── Time and duration input (#830) ─────────────────────────────────────────
 *
 * Single implementation for every time and duration field, driven by data attributes on the
 * input itself so that HTMX fragment swaps and the surrounding hx-* wiring stay untouched:
 *
 *   data-time-mode="duration|time"   how to read and write the value (default: duration)
 *   data-time-step="15"              enables stepping via buttons, arrow keys and the wheel;
 *                                    Shift steps a full hour, Alt a single minute, in all three
 *   data-time-chips="15 30 60"       renders additive quick-add chips plus a reset chip
 *   data-time-chips-target="#id"     optional container for the chips (default: next to the field)
 *
 * The buttons never commit anything themselves: they keep (or take) the focus, so a field that is
 * saved on blur — Start/Pause in the daily view — is written exactly once, when the user is done.
 * Saving on every step would swap the surrounding HTMX fragment away mid-edit and take the focus
 * with it.
 *
 * A field without data-time-step is the classic field: it keeps the tolerant parsing below and
 * gets no extra controls. That is how the beta flag is expressed — one attribute, not a second
 * code path.
 * -------------------------------------------------------------------------- */

const TIME_INPUT_DEFAULT_STEP = 15;
const TIME_INPUT_MAX_DURATION = 24 * 60;
const TIME_INPUT_MAX_TIME = 23 * 60 + 59;

function timeInputPad(value) {
  return String(value).padStart(2, '0');
}

function timeInputFormat(minutes) {
  return timeInputPad(Math.floor(minutes / 60)) + ':' + timeInputPad(minutes % 60);
}

/**
 * Tolerant duration parsing, returns minutes or null when the input cannot be understood.
 * The digit-only rules mirror the historic mask on purpose: "8" is eight hours, "30" is thirty
 * minutes, "130" is 1:30.
 */
function parseDurationValue(raw) {
  if (raw === null || raw === undefined) return null;
  const value = String(raw).trim().toLowerCase().replace(/\s+/g, '');
  if (!value) return null;
  let match;
  if ((match = /^(\d{1,3})h(\d{1,2})m?$/.exec(value))) return Number(match[1]) * 60 + Number(match[2]);
  if ((match = /^(\d{1,3})h$/.exec(value)))            return Number(match[1]) * 60;
  if ((match = /^(\d{1,4})m$/.exec(value)))            return Number(match[1]);
  if ((match = /^(\d{1,3}):(\d{1,2})$/.exec(value)))   return Number(match[1]) * 60 + Number(match[2]);
  if ((match = /^(\d{1,3}):$/.exec(value)))            return Number(match[1]) * 60;
  if ((match = /^:(\d{1,2})$/.exec(value)))            return Number(match[1]);
  // decimal hours, matching the "2,42" notation produced by DurationUtils.decimalFormat
  if ((match = /^(\d{1,3})[.,](\d{1,2})$/.exec(value))) return Math.round(Number(match[1] + '.' + match[2]) * 60);
  if (/^\d{1,4}$/.test(value)) {
    if (value.length === 1) return Number(value) * 60;
    if (value.length === 2) return Number(value);
    if (value.length === 3) return Number(value.slice(0, 1)) * 60 + Number(value.slice(1));
    return Number(value.slice(0, 2)) * 60 + Number(value.slice(2));
  }
  return null;
}

/**
 * Tolerant time-of-day parsing, returns minutes since midnight or null. Mirrors
 * TimeFormatUtils.parseFlexibleTimeOfDay — note that the rules differ from the duration parser
 * above on purpose: "13" is one in the afternoon, and "8.30" is half past eight rather than
 * eight and a half hours.
 */
function parseTimeValue(raw) {
  const value = String(raw || '').trim().replace(/\s+/g, '');
  if (!value) return null;
  const separated = /^(\d{1,2})[:.,](\d{1,2})$/.exec(value);
  if (separated) return timeOfDayMinutes(Number(separated[1]), Number(separated[2]));
  if (!/^\d{1,4}$/.test(value)) return null;
  if (value.length <= 2) return timeOfDayMinutes(Number(value), 0);
  if (value.length === 3) return timeOfDayMinutes(Number(value.slice(0, 1)), Number(value.slice(1)));
  return timeOfDayMinutes(Number(value.slice(0, 2)), Number(value.slice(2)));
}

function timeOfDayMinutes(hour, minute) {
  if (hour > 23 || minute > 59) return null;
  return hour * 60 + minute;
}

/**
 * Inserts the colon while typing, as in the duration field. Everything is reduced to digits first,
 * so that typing on into an already formatted value keeps working ("8:30" + "0" → "18:30" for
 * "1830") and so that a separator typed by hand does not have to be handled separately.
 */
function timeMask(event) {
  const input = event.target;
  if (input.type === 'time') return;
  const digits = input.value.replace(/\D/g, '').slice(0, 4);
  if (digits.length === 4)      input.value = digits.slice(0, 2) + ':' + digits.slice(2);
  else if (digits.length === 3) input.value = digits.slice(0, 1) + ':' + digits.slice(1);
  else                          input.value = digits;
}

function timeBlur(event) {
  const minutes = parseTimeValue(event.target.value);
  if (minutes !== null) {
    event.target.value = timeInputFormat(minutes);
  }
}

/**
 * Keeps the automatic colon for digit-only entry (unchanged behaviour) but leaves free-form entry
 * such as "2h30" or "1,5" alone until it is normalised on blur.
 */
function durationMask(event) {
  const input = event.target;
  // a native time input rejects intermediate values, so leave it to the browser (classic break field)
  if (input.type === 'time') return;
  const raw = input.value;
  if (/[.,hm]/i.test(raw)) {
    const cleaned = raw.replace(/[^\d:.,hm ]/gi, '');
    if (cleaned !== raw) input.value = cleaned;
    return;
  }
  const digits = raw.replace(/\D/g, '').slice(0, 4);
  if (digits.length === 4)      input.value = digits.slice(0, 2) + ':' + digits.slice(2);
  else if (digits.length === 3) input.value = digits.slice(0, 1) + ':' + digits.slice(1);
  else                          input.value = digits;
}

function durationBlur(event) {
  if (event.target.type === 'time') return;
  const minutes = parseDurationValue(event.target.value);
  if (minutes !== null) {
    event.target.value = timeInputFormat(minutes);
  }
}

function timeInputIsTimeMode(input) {
  return input.dataset.timeMode === 'time';
}

function timeInputRead(input) {
  return timeInputIsTimeMode(input) ? parseTimeValue(input.value) : parseDurationValue(input.value);
}

function timeInputWrite(input, minutes) {
  const max = timeInputIsTimeMode(input) ? TIME_INPUT_MAX_TIME : TIME_INPUT_MAX_DURATION;
  input.value = timeInputFormat(Math.min(Math.max(minutes, 0), max));
  // no 'change' event: for fields that save on change/blur it would post mid-edit
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

/** Stepper and arrow keys: snap onto the grid first, then move in full steps (08:07 → 08:15 → 08:30). */
function timeInputStep(input, direction) {
  const step = Number(input.dataset.timeStep) || TIME_INPUT_DEFAULT_STEP;
  const current = timeInputRead(input);
  if (current === null) {
    timeInputWrite(input, direction > 0 ? step : 0);
    return;
  }
  timeInputWrite(input, direction > 0
    ? (Math.floor(current / step) + 1) * step
    : Math.ceil(current / step) * step - step);
}

/** Quick-add chips and the modified steps: plain addition, no snapping. */
function timeInputAdd(input, delta) {
  const current = timeInputRead(input);
  timeInputWrite(input, (current === null ? 0 : current) + delta);
}

/**
 * One place for all three ways of stepping — buttons, arrow keys and the wheel — so that the
 * modifiers mean the same thing everywhere: plain steps on the grid, Shift a full hour, Alt a
 * single minute.
 */
function timeInputStepBy(input, direction, event) {
  if (event && event.altKey) {
    timeInputAdd(input, direction);
  } else if (event && event.shiftKey) {
    timeInputAdd(input, direction * 60);
  } else {
    timeInputStep(input, direction);
  }
}

function timeInputClear(input) {
  input.value = '';
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.focus();
}

function timeInputKeydown(event) {
  if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
  event.preventDefault();
  timeInputStepBy(event.target, event.key === 'ArrowUp' ? 1 : -1, event);
}

/**
 * Only while the field has the focus, mirroring what browsers do for type=number: otherwise merely
 * scrolling past the field would silently change a booking.
 */
function timeInputWheel(event) {
  if (document.activeElement !== event.currentTarget || event.deltaY === 0) return;
  event.preventDefault();
  timeInputStepBy(event.currentTarget, event.deltaY < 0 ? 1 : -1, event);
}

function timeInputButton(input, className, content, label) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = className + (input.classList.contains('form-control-sm') ? ' btn-sm' : '');
  button.innerHTML = content;
  if (label) {
    button.title = label;
    button.setAttribute('aria-label', label);
  }
  // Never let the button take the focus, and put it into the field instead. Two reasons: a
  // blur-triggered save must not fire between two clicks, and the field ends up focused so the
  // arrow keys continue where the buttons left off.
  button.addEventListener('mousedown', (event) => event.preventDefault());
  button.addEventListener('click', () => input.focus());
  return button;
}

function timeInputChipLabel(minutes) {
  if (minutes % 60 === 0) return '+' + (minutes / 60) + 'h';
  if (minutes < 60) return '+' + minutes;
  return '+' + Math.floor(minutes / 60) + ':' + timeInputPad(minutes % 60);
}

function enhanceTimeInput(input) {
  if (input.dataset.timeInputReady === 'true') return;
  input.dataset.timeInputReady = 'true';
  if (input.disabled || input.readOnly) return;

  input.addEventListener('keydown', timeInputKeydown);
  input.addEventListener('wheel', timeInputWheel, { passive: false });

  const group = document.createElement('div');
  group.className = 'input-group flex-nowrap w-auto';
  input.parentNode.insertBefore(group, input);

  const decrease = timeInputButton(input, 'btn btn-outline-secondary px-2', '&minus;',
    input.dataset.timeLabelDecrease);
  const increase = timeInputButton(input, 'btn btn-outline-secondary px-2', '+',
    input.dataset.timeLabelIncrease);
  decrease.addEventListener('click', (event) => timeInputStepBy(input, -1, event));
  increase.addEventListener('click', (event) => timeInputStepBy(input, 1, event));

  group.appendChild(decrease);
  group.appendChild(input);
  group.appendChild(increase);

  // whitespace separated, because a comma inside a th:attr value would be read as an attribute separator
  const chips = (input.dataset.timeChips || '')
    .split(/[\s,]+/)
    .filter((value) => value !== '')
    .map(Number)
    .filter((value) => Number.isFinite(value) && value > 0);
  if (!chips.length) return;

  // an explicit target lets the template place the chips on a full-width row of their own, so they
  // do not have to fit into the (narrow) column of the field itself
  const target = input.dataset.timeChipsTarget
    ? document.querySelector(input.dataset.timeChipsTarget)
    : null;

  const chipRow = document.createElement('div');
  chipRow.className = target ? 'd-flex flex-wrap gap-1' : 'd-flex flex-wrap gap-1 mt-2';
  chips.forEach((minutes) => {
    const chip = timeInputButton(input, 'btn btn-outline-secondary', timeInputChipLabel(minutes));
    chip.addEventListener('click', () => timeInputAdd(input, minutes));
    chipRow.appendChild(chip);
  });

  // reset is only offered once there is something to reset — keeps the row short by default
  const reset = timeInputButton(input, 'btn btn-outline-secondary',
    '<i class="ti ti-rotate-2 m-0"></i>', input.dataset.timeLabelReset);
  reset.addEventListener('click', () => timeInputClear(input));
  const syncReset = () => {
    const current = timeInputRead(input);
    reset.classList.toggle('d-none', current === null || current === 0);
  };
  input.addEventListener('input', syncReset);
  syncReset();
  chipRow.appendChild(reset);

  if (target) {
    target.appendChild(chipRow);
  } else {
    group.parentNode.insertBefore(chipRow, group.nextSibling);
  }
}

function initTimeInputs() {
  document.querySelectorAll('input[data-time-step]').forEach(enhanceTimeInput);
}

initTimeInputs();
document.addEventListener('htmx:afterSettle', initTimeInputs);

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
