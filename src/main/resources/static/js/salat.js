document.addEventListener("htmx:config:request", function(evt) {
  const token = document.cookie.split("; ")
    .find(r => r.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
  if (token) {
    // htmx 4 carries the whole request context in the event detail; the headers of htmx 2 sat
    // directly on it (https://four.htmx.org/docs#migrating-from-htmx-2x-to-4x)
    evt.detail.ctx.request.headers["X-XSRF-TOKEN"] = decodeURIComponent(token);
  }
});

document.addEventListener('close.bs.alert', e => {
  const wrapper = e.target.parentElement;
  e.target.addEventListener('closed.bs.alert', () => wrapper?.remove(), { once: true });
});

function toggleTheme(theme) {
  localStorage.setItem('tabler-theme', theme);
  document.documentElement.setAttribute('data-bs-theme', theme);
}

// Papier ist weiss: gedruckt wird immer im hellen Modus, sonst stuende etwa ein Warnhinweis in
// hellem Gelb auf weissem Grund (#1147). Nur das Attribut wechselt, nicht die gespeicherte Wahl -
// nach dem Druck steht die Seite wieder so da wie vorher.
let themeBeforePrint = null;
window.addEventListener('beforeprint', () => {
  themeBeforePrint = document.documentElement.getAttribute('data-bs-theme');
  document.documentElement.setAttribute('data-bs-theme', 'light');
});
window.addEventListener('afterprint', () => {
  if (themeBeforePrint !== null) {
    document.documentElement.setAttribute('data-bs-theme', themeBeforePrint);
    themeBeforePrint = null;
  }
});

function selectContract(id) {
  const url = new URL(window.location.href);
  url.searchParams.set('fEmployeeContractId', String(id));
  location.href = url.toString();
}

// The translated name sits on <body>, so no template with a multi-select has to bring it along. The
// plugin puts it into the title only, and a role="button" takes its accessible name from its
// content first — a screen reader would announce "×". Hence an element of our own with aria-label.
const removeButtonOptions = () => ({
  title: document.body.dataset.selectRemoveLabel,
  html: (data) => {
    const cross = document.createElement('div');
    cross.className = data.className;
    cross.title = data.title;
    cross.setAttribute('aria-label', data.title);
    cross.setAttribute('role', data.role);
    cross.tabIndex = data.tabindex;
    cross.textContent = data.label;
    return cross;
  },
});

const tomSelectConfig = (el) => {
  // a remote field is an <input>, which has no options at all
  const hasSubtext = Array.from(el.options || []).some(opt => opt.dataset.subtext);
  const favoriteTarget = el.dataset.favoriteTarget || null;
  const remoteUrl = el.dataset.remoteUrl || null;
  const multi = el.classList.contains('tomselect-multi');

  const config = {
    // A field whose values are not all known in advance says so with data-allow-create: the offered
    // options stay a convenience, and what somebody types is kept (#1074).
    create: el.dataset.allowCreate === 'true',
    maxItems: multi ? null : 1,
    maxOptions: 1000,
    // Without remove_button a chip goes away only by keyboard: activate it, then Backspace. On a
    // phone that never comes together, and a picked entry stayed (#1149).
    plugins: multi
      ? { dropdown_input: {}, remove_button: removeButtonOptions() }
      : ['dropdown_input'],
    sortField: [{ field: '$order' }],
    placeholder: el.getAttribute('placeholder') || 'Select an option...',
    onDropdownOpen(dropdown) {
      dropdown.style.width = 'max-content';
      dropdown.style.minWidth = this.wrapper.offsetWidth + 'px';
    },
  };

  // Free text field with suggestions fetched while typing (#982). The typed text always wins: the
  // list is a convenience, and a value that matches nothing on the server is kept as entered.
  if (remoteUrl) {
    const contextField = el.dataset.remoteContextField || null;
    const contextParam = el.dataset.remoteContextParam || null;
    const fillTarget = el.dataset.fillTarget || null;
    const createLabel = el.dataset.createLabel || '';
    const context = () => (contextField ? (document.querySelector(contextField)?.value || '') : '');

    Object.assign(config, {
      create: true,
      createOnBlur: true,
      persist: false,
      maxItems: 1,
      valueField: 'value',
      labelField: 'value',
      searchField: ['value', 'subtext'],
      // the dropdown_input plugin would put the typing into a second field above the list; here the
      // control itself is the text field
      plugins: [],
      // without a context there is nothing to search in — an order without replicated tickets stays
      // a plain text field
      shouldLoad: () => !!context(),
      load(query, callback) {
        const params = new URLSearchParams({ q: query });
        if (contextParam) {
          params.set(contextParam, context());
        }
        fetch(remoteUrl + '?' + params.toString(), { headers: { Accept: 'application/json' } })
          .then(response => (response.ok ? response.json() : []))
          .then(rows => callback(rows.map(row => ({ value: row.key, subtext: row.summary }))))
          .catch(() => callback([]));
      },
      onInitialize() {
        // an existing booking opens with its stored reference; only the number is stored, so there
        // is no title to show for it yet
        const initial = el.getAttribute('value') || '';
        if (initial) {
          this.addOption({ value: initial });
          this.addItem(initial, true);
        }
      },
      onItemAdd(value) {
        this.setTextboxValue('');
        if (!fillTarget) return;
        const summary = this.options[value]?.subtext;
        const target = document.querySelector(fillTarget);
        // no subtext means this is not one of the offered entries but text somebody typed, and
        // nothing is invented for the comment from that
        if (!summary || !target) return;
        // number and title, so the comment says which ticket this is without having to look the
        // number up somewhere else
        const filled = value + ' - ' + summary;
        const current = target.value.trim();
        // what somebody typed is theirs and stays; an empty field and one still holding what the
        // previously picked entry wrote follow the new pick. The mark sits on the target rather
        // than in this closure so that anything else writing the field can hand the text over as
        // the user's own by deleting it (#1029: the recent bookings card does exactly that).
        if (current && current !== target.salatAutoFilled) return;
        target.value = filled;
        target.salatAutoFilled = filled;
        target.dispatchEvent(new Event('input', { bubbles: true }));
        target.dispatchEvent(new Event('change', { bubbles: true }));
      },
      render: {
        option(data, escape) {
          return '<div class="d-flex flex-column py-1">'
            + '<span class="text-nowrap">' + escape(data.value) + '</span>'
            + (data.subtext
              ? '<small class="text-muted lh-1 mb-1 text-truncate">' + escape(data.subtext) + '</small>'
              : '')
            + '</div>';
        },
        item(data, escape) {
          return '<div>' + escape(data.value) + '</div>';
        },
        option_create(data, escape) {
          return '<div class="create">' + escape(createLabel) + ' <strong>'
            + escape(data.input) + '</strong></div>';
        },
        no_results: null,
      },
    });
    return config;
  }

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

// inputs take part as well: a free text field with remote suggestions is an input, not a select
const TOMSELECT_SELECTOR = 'select.tomselect, input.tomselect';

document.querySelectorAll(TOMSELECT_SELECTOR).forEach((el) => {
  new TomSelect(el, tomSelectConfig(el));
});

document.addEventListener('htmx:after:swap', function () {
  document.querySelectorAll(TOMSELECT_SELECTOR).forEach((el) => {
    if (!el.tomselect) {
      new TomSelect(el, tomSelectConfig(el));
    }
  });
});

document.addEventListener('htmx:after:swap', function () {
  const raw = document.cookie.split('; ')
    .find(r => r.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
  if (raw) {
    const token = decodeURIComponent(raw);
    document.querySelectorAll('input[name="_csrf"]')
      .forEach(function (el) { el.value = token; });
  }
});

/* ─── Explanation behind an info icon (#1065) ────────────────────────────────
 *
 * A rule that is needed when looking it up, not on every visit, hangs in a popover behind an info
 * icon instead of standing in the page: the form stays as short as it was, and whoever knows the
 * rule is not told it again every time.
 *
 *   data-info-popover   on the toggle, a CSS selector of the hidden block holding the explanation
 *
 * Declared here and not in the page, the way the confirmation dialog is (#1032): the two
 * hand-written popovers that predate this (matrix.html, dashboard.html) are the reason — a third
 * one-off script would have made the pattern a habit. Tabler builds `[data-bs-toggle="popover"]`
 * by itself, but only from a string attribute, and an explanation with a list of steps in an
 * attribute means markup in the message bundles.
 *
 * `hover focus` covers both ways in: pointing at it, and the keyboard. On a touch device the tap
 * focuses the button and opens it too. The content is read on every open rather than captured once,
 * so a block replaced by an htmx swap is picked up.
 * -------------------------------------------------------------------------- */

const INFO_POPOVER_SELECTOR = '[data-info-popover]';

function initInfoPopovers() {
  document.querySelectorAll(INFO_POPOVER_SELECTOR).forEach(function (toggle) {
    if (toggle.dataset.infoPopoverReady) return;
    const selector = toggle.dataset.infoPopover;
    if (!selector || !document.querySelector(selector)) return;
    toggle.dataset.infoPopoverReady = 'true';
    new tabler.bootstrap.Popover(toggle, {
      html: true,
      content: function () {
        const content = document.querySelector(selector);
        return content ? content.innerHTML : '';
      },
      trigger: 'hover focus',
      placement: 'bottom',
      customClass: 'info-popover'
    });
  });
}

initInfoPopovers();
document.addEventListener('htmx:after:swap', initInfoPopovers);

/* ─── Confirmation dialog (#1032, ADR-0027) ──────────────────────────────────
 *
 * One dialog for the whole application (fragments/confirm-dialog.html, included once by
 * layout/base.html). The triggering action describes it declaratively; no page brings its own
 * script for a confirmation:
 *
 *   data-confirm                    marks the form — or a single submit button of it — as needing
 *                                   a confirmation
 *   data-confirm-title              heading; defaults to the generic one carried by the dialog
 *   data-confirm-text               what the action does
 *   data-confirm-detail             the business object it hits, and
 *   data-confirm-detail-secondary   whatever else tells it apart from its neighbour in the list.
 *                                   Mandatory wherever the action aims at exactly one object —
 *                                   naming it is the whole point of replacing the native popup.
 *   data-confirm-detail-input       selector of a field of the same form whose current value
 *                                   completes the second line. Where the scope is what somebody
 *                                   just typed — release up to which month — a fixed text cannot
 *                                   say it, and that scope is the key information here.
 *   data-confirm-label              caption of the confirming button
 *   data-confirm-variant            danger | warning | success | azure | primary (default);
 *                                   azure marks a day as not worked (#1159)
 *
 * The listener sits on `document` in the capture phase and stops the event there. HTMX registers
 * its trigger on the form element itself, so anything but capture would let an hx-post leave
 * before the question is answered.
 * -------------------------------------------------------------------------- */

// anything else would be a class name straight from an attribute into the DOM
const CONFIRM_VARIANTS = ['primary', 'danger', 'warning', 'success', 'azure'];

function confirmDialogLine(id, value) {
  const el = document.getElementById(id);
  el.textContent = value || '';
  // an absent line collapses rather than opening a gap under the text
  el.classList.toggle('d-none', !value);
}

function confirmDialogSecondary(source, form) {
  const parts = [source.dataset.confirmDetailSecondary];
  if (source.dataset.confirmDetailInput) {
    parts.push(form.querySelector(source.dataset.confirmDetailInput)?.value);
  }
  return parts.filter(Boolean).join(': ');
}

function fillConfirmDialog(modal, source, form) {
  const data = source.dataset;
  document.getElementById('confirmModalTitle').textContent =
    data.confirmTitle || modal.dataset.defaultTitle;
  confirmDialogLine('confirmModalText', data.confirmText);
  confirmDialogLine('confirmModalDetail', data.confirmDetail);
  confirmDialogLine('confirmModalDetailSecondary', confirmDialogSecondary(source, form));

  const accept = document.getElementById('confirmModalAccept');
  accept.textContent = data.confirmLabel || modal.dataset.defaultLabel;
  const variant = CONFIRM_VARIANTS.includes(data.confirmVariant) ? data.confirmVariant : 'primary';
  accept.className = 'btn btn-' + variant;
}

function openConfirmDialog(source, form, onConfirm) {
  const modal = document.getElementById('confirmModal');
  // no dialog, no confirmation — and therefore no action either
  if (!modal) return;
  fillConfirmDialog(modal, source, form);

  const accept = document.getElementById('confirmModalAccept');
  const trigger = document.activeElement;
  let confirmed = false;

  const onAccept = () => {
    confirmed = true;
    instance.hide();
  };
  accept.addEventListener('click', onAccept);

  // the confirming button carries the focus, so Enter answers the question that was asked and
  // Escape (Bootstrap) cancels; the dialog is reachable by keyboard alone from here on
  modal.addEventListener('shown.bs.modal', () => accept.focus(), { once: true });
  modal.addEventListener('hidden.bs.modal', () => {
    accept.removeEventListener('click', onAccept);
    // the native popup handed the focus back by itself; Bootstrap only does that for a modal opened
    // through data-bs-toggle, and this one is opened from script
    if (trigger && document.body.contains(trigger)) trigger.focus();
    // act after the dialog is gone: an HTMX action swaps the page underneath it, and a backdrop
    // whose modal is mid-transition stays on the screen
    if (confirmed) onConfirm();
  }, { once: true });

  const instance = tabler.bootstrap.Modal.getOrCreateInstance(modal);
  instance.show();
}

function confirmSource(form, submitter) {
  // the button wins: a form may have one action that asks and another that does not
  if (submitter && submitter.hasAttribute('data-confirm')) return submitter;
  if (form.hasAttribute('data-confirm')) return form;
  return null;
}

document.addEventListener('submit', function (event) {
  const form = event.target;
  const submitter = event.submitter;
  const source = confirmSource(form, submitter);
  if (!source) return;
  if (form.salatConfirmed) {
    // the re-submit below, on its way through: let it pass exactly once
    form.salatConfirmed = false;
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  openConfirmDialog(source, form, function () {
    form.salatConfirmed = true;
    // requestSubmit, not submit(): it keeps the submitter (so the pressed button's name and value
    // are sent) and runs the HTML5 validation, both of which form.submit() skips
    form.requestSubmit(submitter || undefined);
  });
}, true);

// The CSS counterpart hides every confirming button until this line has run: without the script
// there is no dialog, and a destructive action that simply fires would be worse than one that is
// missing (see salat.css).
document.body.classList.add('salat-confirm-ready');

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
document.addEventListener('htmx:after:swap', initTimeInputs);

/* ─── Entry focus (#1064) ────────────────────────────────────────────────────
 *
 * The tab order is the document order — nothing hands out tabindex values. What is set here is
 * only where the keyboard starts: on the first field of the form, on a list on the first field of
 * the filter. Everything after that follows from the markup.
 *
 * Buttons and links are deliberately not candidates: the entry belongs on the first field, not on
 * "Save" and not on a link of the list. Elements outside a form are none either — that keeps the
 * dismiss button of a toast and the links of a card out of it.
 * -------------------------------------------------------------------------- */

const ENTRY_FOCUS_SELECTOR = [
  'form input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="reset"])',
  'form select',
  'form textarea',
].join(', ');

function isEntryFocusCandidate(el) {
  if (el.disabled || el.readOnly) return false;
  // TomSelect builds an input of its own plus a placeholder input inside .ts-wrapper. Neither is
  // the field the template declared — that one stands next to the wrapper and carries .tomselect.
  if (el.closest('.ts-wrapper')) return false;
  // a TomSelect field is hidden itself (ts-hidden-accessible); its control stands in for it below
  if (el.tomselect) return true;
  return el.offsetParent !== null;
}

function focusEntryField() {
  // a field focused on load opens the on-screen keyboard on a touch device and covers half the
  // page with it; the entry focus is there for operating the application by keyboard
  if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) return;
  // what the markup asks for wins — the browser has already honoured it by now
  if (document.querySelector('[autofocus]')) return;
  // something already holds the focus (a dialog opened on load brings its own)
  if (document.activeElement && document.activeElement !== document.body) return;

  const wrapper = document.querySelector('.page-body');
  if (!wrapper) return;
  const field = Array.from(wrapper.querySelectorAll(ENTRY_FOCUS_SELECTOR))
    .find(isEntryFocusCandidate);
  if (!field) return;

  if (field.tomselect) {
    const select = field.tomselect;
    // openOnFocus would drop the dropdown open over the page on every single load
    const openOnFocus = select.settings.openOnFocus;
    select.settings.openOnFocus = false;
    (select.focus_node || select.control).focus();
    select.settings.openOnFocus = openOnFocus;
    return;
  }
  field.focus();
}

// only on the first load, and deliberately not on htmx:after:swap: the daily view swaps fragments
// while the user types, and a focus set again there would take the cursor out of the field
focusEntryField();

/* ─── Command palette (#1155, ADR-0030) ──────────────────────────────────────
 *
 * Ctrl+K (⌘K on macOS) or the search entry in the header open it on every page
 * (fragments/command-palette.html, included once by layout/base.html). It navigates — it never
 * saves anything and never submits a form, which is why no command needs a confirmation.
 *
 * What it offers is read from the rendered page instead of from a list of its own:
 *
 *   #sidebar-menu .dropdown-item[href]  the navigation. Label, section and the role filter come
 *                                       along, and a new menu entry shows up without further ado.
 *   data-palette-href-from              on a sidebar entry: selector of an element whose href wins
 *                                       over the entry's own, where that element is on the page
 *   data-palette-command                on a header control: the key a settings command is
 *                                       remembered by. Its accessible name is the label, clicking
 *                                       it is the action, and it is offered only while displayed.
 *   data-palette-keywords               further words a command is found by
 *   data-palette-label-pressed          the label while the control is aria-pressed
 *
 * Day jumps into the daily view are read in the browser, against the server's today carried by
 * the dialog. Neither opening the palette nor any hit it shows sends a request.
 * -------------------------------------------------------------------------- */

const PALETTE_RECENT_KEY = 'salat-command-palette-recent';
const PALETTE_RECENT_MAX = 10;
const PALETTE_IS_MAC = /mac|iphone|ipad|ipod/i.test(
  (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || '');

// ranks, highest first; a day jump sits between a word start and a hit inside a word, so that
// "fr" offers the page "Freigabe" first and the Friday right below it
const PALETTE_TIER_WORD_START = 3;
const PALETTE_TIER_DAY = 2.5;
const PALETTE_TIER_INSIDE = 2;
const PALETTE_TIER_FUZZY = 1;
const PALETTE_TIER_SECTION = 0.5;

const paletteState = { origin: null, commands: [], items: [], active: -1 };

/**
 * Lower case without diacritics, so that "ubersicht" finds "Übersicht". The map leads every
 * character of the folded text back to its position in the original, for the highlighting.
 */
function paletteFold(text) {
  let folded = '';
  const map = [];
  for (let i = 0; i < text.length; i++) {
    const part = text[i].normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
    for (let j = 0; j < part.length; j++) {
      folded += part[j];
      map.push(i);
    }
  }
  return { folded, map };
}

function paletteIsWordStart(folded, index) {
  return index === 0 || !/[\p{L}\p{N}]/u.test(folded[index - 1]);
}

function paletteWordStartIndex(folded, word) {
  let from = 0;
  let index;
  while ((index = folded.indexOf(word, from)) !== -1) {
    if (paletteIsWordStart(folded, index)) return index;
    from = index + 1;
  }
  return -1;
}

/** The letters of the query in order, starting on a word start; the tightest such run wins. */
function paletteFuzzy(folded, query) {
  let best = null;
  for (let start = 0; start < folded.length; start++) {
    if (folded[start] !== query[0] || !paletteIsWordStart(folded, start)) continue;
    const positions = [start];
    let at = start + 1;
    for (let q = 1; q < query.length && positions.length === q; q++) {
      const next = folded.indexOf(query[q], at);
      if (next === -1) break;
      positions.push(next);
      at = next + 1;
    }
    if (positions.length !== query.length) continue;
    const span = positions[positions.length - 1] - start;
    if (!best || span < best.span) best = { span, positions };
  }
  return best;
}

/** [start, end) in the folded text, merged where they touch, then led back to the original. */
function paletteRanges(map, spans) {
  const merged = [];
  spans.slice().sort((a, b) => a[0] - b[0]).forEach(([start, end]) => {
    const last = merged[merged.length - 1];
    if (last && start <= last[1]) last[1] = Math.max(last[1], end);
    else merged.push([start, end]);
  });
  return merged.map(([start, end]) => [map[start], map[end - 1] + 1]);
}

/**
 * How well `text` answers `query`: a word of it starts with the query, the query stands inside a
 * word, or its letters follow each other from a word start on. Several words of a query may stand
 * in any order, each on a word start of its own. Returns the tier, the position of the hit and the
 * ranges to highlight — or null.
 */
function paletteMatch(text, query) {
  const q = paletteFold(query.trim()).folded.replace(/\s+/g, ' ');
  if (!q || !text) return null;
  const { folded, map } = paletteFold(text);

  const wordStart = paletteWordStartIndex(folded, q);
  if (wordStart !== -1) {
    return { tier: PALETTE_TIER_WORD_START, pos: wordStart,
      ranges: paletteRanges(map, [[wordStart, wordStart + q.length]]) };
  }
  const inside = folded.indexOf(q);
  if (inside !== -1) {
    return { tier: PALETTE_TIER_INSIDE, pos: inside, ranges: paletteRanges(map, [[inside, inside + q.length]]) };
  }
  const words = q.split(' ');
  if (words.length > 1) {
    const spans = words.map(word => {
      const index = paletteWordStartIndex(folded, word);
      return index === -1 ? null : [index, index + word.length];
    });
    if (spans.every(Boolean)) {
      return { tier: PALETTE_TIER_INSIDE, pos: Math.min(...spans.map(span => span[0])),
        ranges: paletteRanges(map, spans) };
    }
    return null;
  }
  const fuzzy = paletteFuzzy(folded, q);
  if (fuzzy) {
    return { tier: PALETTE_TIER_FUZZY, pos: fuzzy.positions[0],
      ranges: paletteRanges(map, fuzzy.positions.map(p => [p, p + 1])) };
  }
  return null;
}

/* ─── Day jumps ─── */

function paletteIsoOf(date) {
  return date.getUTCFullYear() + '-' + timeInputPad(date.getUTCMonth() + 1) + '-'
    + timeInputPad(date.getUTCDate());
}

function paletteDateOf(iso) {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function paletteShiftDays(iso, days) {
  const date = paletteDateOf(iso);
  date.setUTCDate(date.getUTCDate() + days);
  return paletteIsoOf(date);
}

/** A calendar date, or null for one that does not exist (31.2.). */
function paletteValidDate(year, month, day) {
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    return null;
  }
  return paletteIsoOf(date);
}

/**
 * Reads a day from what was typed, relative to `today` (ISO): the words for today, yesterday, the
 * day before yesterday and tomorrow from three letters on, a weekday from two — the most recent one,
 * today included —, a date T.M., T.M.JJ or T.M.JJJJ, or an ISO date. Returns every day the input
 * can mean, as `{ iso }`; an empty list for anything else.
 *
 * @param vocabulary `{ offsets: [[word, days]], weekdays: [monday … sunday] }`
 */
function paletteParseDay(raw, today, vocabulary) {
  const input = paletteFold(String(raw || '').trim()).folded.replace(/\s+/g, ' ');
  if (!input) return [];

  let match;
  if ((match = /^(\d{1,2})\.(\d{1,2})\.?(?:(\d{2}|\d{4}))?$/.exec(input))) {
    const year = match[3] === undefined ? paletteDateOf(today).getUTCFullYear()
      : (match[3].length === 2 ? 2000 + Number(match[3]) : Number(match[3]));
    const iso = paletteValidDate(year, Number(match[2]), Number(match[1]));
    return iso ? [{ iso }] : [];
  }
  if ((match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(input))) {
    const iso = paletteValidDate(Number(match[1]), Number(match[2]), Number(match[3]));
    return iso ? [{ iso }] : [];
  }

  const days = [];
  if (input.length >= 3) {
    vocabulary.offsets.forEach(([word, offset]) => {
      if (word && paletteFold(word).folded.startsWith(input)) days.push({ iso: paletteShiftDays(today, offset) });
    });
  }
  if (input.length >= 2) {
    // getUTCDay counts from Sunday, the list from Monday
    const todayIndex = (paletteDateOf(today).getUTCDay() + 6) % 7;
    vocabulary.weekdays.forEach((name, index) => {
      if (name && paletteFold(name).folded.startsWith(input)) {
        days.push({ iso: paletteShiftDays(today, -((todayIndex - index + 7) % 7)) });
      }
    });
  }
  return days;
}

/** "Fr 25.09.2026" — the weekday in the language of the page, without a trailing dot. */
function paletteFormatDay(iso, lang) {
  const date = paletteDateOf(iso);
  const weekday = new Intl.DateTimeFormat(lang, { weekday: 'short', timeZone: 'UTC' })
    .format(date).replace(/\.$/, '');
  return weekday + ' ' + timeInputPad(date.getUTCDate()) + '.' + timeInputPad(date.getUTCMonth() + 1)
    + '.' + date.getUTCFullYear();
}

function paletteVocabulary(dialog) {
  const data = dialog.dataset;
  return {
    offsets: [[data.dayToday, 0], [data.dayYesterday, -1], [data.dayDaybeforeyesterday, -2],
      [data.dayTomorrow, 1]],
    weekdays: (data.weekdays || '').split(','),
  };
}

/* ─── What the page offers ─── */

function paletteCleanText(el) {
  if (!el) return '';
  const copy = el.cloneNode(true);
  // "Beta" and "New" are markers, not part of the name
  copy.querySelectorAll('.badge').forEach(badge => badge.remove());
  return copy.textContent.replace(/\s+/g, ' ').trim();
}

function paletteNavigationCommands() {
  return Array.from(document.querySelectorAll('#sidebar-menu .dropdown-item[href]')).map(link => {
    const override = link.dataset.paletteHrefFrom
      ? document.querySelector(link.dataset.paletteHrefFrom) : null;
    const target = override && override.href ? override.href : link.href;
    return {
      type: 'nav',
      key: link.getAttribute('href'),
      label: paletteCleanText(link),
      kind: paletteCleanText(link.closest('.nav-item')?.querySelector('.nav-link-title')),
      keywords: '',
      href: target,
      run: () => window.location.assign(target),
    };
  });
}

function paletteSettingsCommands(dialog) {
  return Array.from(document.querySelectorAll('[data-palette-command]'))
    .filter(el => el.checkVisibility ? el.checkVisibility() : el.offsetParent !== null)
    .map(el => ({
      type: 'cmd',
      key: el.dataset.paletteCommand,
      label: el.getAttribute('aria-pressed') === 'true' && el.dataset.paletteLabelPressed
        ? el.dataset.paletteLabelPressed
        : (el.getAttribute('aria-label') || paletteCleanText(el)),
      kind: dialog.dataset.kindSettings,
      keywords: el.dataset.paletteKeywords || '',
      href: el.href || null,
      run: () => el.click(),
    }));
}

function paletteDayCommand(dialog, iso, expression) {
  const url = dialog.dataset.dailyUrl + '?mode=daily&date=' + iso;
  const lang = document.documentElement.lang || 'de';
  return {
    type: 'day',
    key: expression,
    label: dialog.dataset.labelDaily + ' · ' + paletteFormatDay(iso, lang),
    kind: dialog.dataset.kindDay,
    href: url,
    run: () => window.location.assign(url),
  };
}

/* ─── Remembered commands ─── */

function paletteReadRecent() {
  try {
    const stored = JSON.parse(localStorage.getItem(PALETTE_RECENT_KEY) || '[]');
    return Array.isArray(stored) ? stored : [];
  } catch (e) {
    return [];
  }
}

function paletteRemember(entry) {
  const recent = paletteReadRecent().filter(e => !(e.t === entry.t && e.k === entry.k));
  recent.unshift(entry);
  try {
    localStorage.setItem(PALETTE_RECENT_KEY, JSON.stringify(recent.slice(0, PALETTE_RECENT_MAX)));
  } catch (e) {
    // private mode or a full storage: the palette works on, it only forgets
  }
}

/**
 * Every entry is resolved again against what the page offers now: a page the current login does
 * not see is not offered, whoever used it before, and a day jump is remembered as its expression,
 * so "fr" leads to the most recent Friday next week as well.
 */
function paletteRecentItems(dialog, commands, today, vocabulary) {
  const lang = document.documentElement.lang || 'de';
  return paletteReadRecent().map(entry => {
    if (entry.t === 'day') {
      const day = paletteParseDay(entry.k, today, vocabulary)[0];
      if (!day) return null;
      const item = paletteDayCommand(dialog, day.iso, entry.k);
      item.label = dialog.dataset.labelDaily + ' · ' + entry.k;
      item.kind = paletteFormatDay(day.iso, lang);
      return item;
    }
    return commands.find(command => command.type === entry.t && command.key === entry.k) || null;
  }).filter(Boolean);
}

/* ─── Hits and their list ─── */

/** The expression a day jump is remembered by: what was typed, in the form it is read in. */
function paletteDayExpression(query) {
  return query.trim().replace(/\s+/g, ' ').toLowerCase();
}

/**
 * Every command the query finds, best first. The label counts most; the further words of a
 * command and — from three letters on — the section it belongs to find it too, but rank below.
 */
function paletteSearch(dialog, commands, query, today, vocabulary) {
  const hits = [];
  commands.forEach((command, order) => {
    let match = paletteMatch(command.label, query);
    if (!match && command.keywords) {
      const byKeyword = paletteMatch(command.keywords, query);
      if (byKeyword) match = { tier: byKeyword.tier - 0.25, pos: byKeyword.pos, ranges: [] };
    }
    if (!match && command.kind && query.trim().length >= 3) {
      const bySection = paletteMatch(command.kind, query);
      if (bySection && bySection.tier >= PALETTE_TIER_INSIDE) {
        match = { tier: PALETTE_TIER_SECTION, pos: 0, ranges: [] };
      }
    }
    if (match) hits.push({ command, ranges: match.ranges, tier: match.tier, pos: match.pos, order });
  });
  paletteParseDay(query, today, vocabulary).forEach((day, index) => {
    hits.push({ command: paletteDayCommand(dialog, day.iso, paletteDayExpression(query)),
      ranges: [], tier: PALETTE_TIER_DAY, pos: 0, order: commands.length + index });
  });
  return hits.sort((a, b) => b.tier - a.tier || a.pos - b.pos || a.order - b.order);
}

function paletteHighlight(target, text, ranges) {
  let at = 0;
  ranges.forEach(([start, end]) => {
    if (start > at) target.append(text.slice(at, start));
    const mark = document.createElement('mark');
    mark.textContent = text.slice(start, end);
    target.append(mark);
    at = end;
  });
  if (at < text.length) target.append(text.slice(at));
}

function paletteOption(hit, index) {
  const option = document.createElement('div');
  option.className = 'command-palette-option';
  option.id = 'commandPaletteOption' + index;
  option.setAttribute('role', 'option');
  option.setAttribute('aria-selected', 'false');
  option.dataset.index = String(index);
  option.dataset.commandType = hit.command.type;
  option.dataset.commandKey = hit.command.key;

  const label = document.createElement('span');
  label.className = 'command-palette-label';
  paletteHighlight(label, hit.command.label, hit.ranges || []);
  option.append(label);
  if (hit.command.kind) {
    const kind = document.createElement('span');
    kind.className = 'command-palette-kind';
    kind.textContent = hit.command.kind;
    option.append(kind);
  }
  return option;
}

function paletteRender(groups) {
  const input = document.getElementById('commandPaletteInput');
  const list = document.getElementById('commandPaletteList');
  list.replaceChildren();
  paletteState.items = [];
  groups.forEach((group, groupIndex) => {
    let container = list;
    if (group.label && group.hits.length) {
      container = document.createElement('div');
      container.setAttribute('role', 'group');
      const heading = document.createElement('div');
      heading.className = 'command-palette-group';
      heading.id = 'commandPaletteGroup' + groupIndex;
      heading.setAttribute('role', 'presentation');
      heading.textContent = group.label;
      container.setAttribute('aria-labelledby', heading.id);
      container.append(heading);
      list.append(container);
    }
    group.hits.forEach(hit => {
      container.append(paletteOption(hit, paletteState.items.length));
      paletteState.items.push(hit.command);
    });
  });
  document.getElementById('commandPaletteEmpty').hidden = paletteState.items.length > 0;
  input.setAttribute('aria-expanded', String(paletteState.items.length > 0));
  paletteSetActive(paletteState.items.length ? 0 : -1, true);
}

function paletteSetActive(index, scroll) {
  const input = document.getElementById('commandPaletteInput');
  const list = document.getElementById('commandPaletteList');
  list.querySelectorAll('[aria-selected="true"]').forEach(option => option.setAttribute('aria-selected', 'false'));
  paletteState.active = index;
  if (index < 0) {
    input.removeAttribute('aria-activedescendant');
    return;
  }
  const option = document.getElementById('commandPaletteOption' + index);
  option.setAttribute('aria-selected', 'true');
  input.setAttribute('aria-activedescendant', option.id);
  if (!scroll) return;
  // the first entry takes its group heading along into view
  if (index === 0) list.scrollTop = 0;
  else option.scrollIntoView({ block: 'nearest' });
}

function paletteUpdate(dialog) {
  const query = document.getElementById('commandPaletteInput').value;
  const today = dialog.dataset.today;
  const vocabulary = paletteVocabulary(dialog);
  if (query.trim()) {
    paletteRender([{ hits: paletteSearch(dialog, paletteState.commands, query, today, vocabulary) }]);
    return;
  }
  const recent = paletteRecentItems(dialog, paletteState.commands, today, vocabulary);
  // nothing used yet: then everything there is, so that the first look shows what can be found
  paletteRender(recent.length
    ? [{ label: dialog.dataset.groupRecent, hits: recent.map(command => ({ command })) }]
    : [{ label: dialog.dataset.groupPages, hits: paletteState.commands.map(command => ({ command })) }]);
}

function paletteRun(index) {
  const command = paletteState.items[index];
  if (!command) return;
  paletteRemember({ t: command.type, k: command.key });
  document.getElementById('commandPalette').close();
  command.run();
}

/* ─── Opening and closing ─── */

function paletteWire(dialog) {
  if (dialog.dataset.paletteReady) return;
  dialog.dataset.paletteReady = 'true';
  const input = document.getElementById('commandPaletteInput');
  const list = document.getElementById('commandPaletteList');

  input.addEventListener('input', () => paletteUpdate(dialog));
  input.addEventListener('keydown', (event) => {
    const count = paletteState.items.length;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (count) paletteSetActive((paletteState.active + (event.key === 'ArrowDown' ? 1 : -1) + count) % count, true);
    } else if (event.key === 'Enter' && !event.isComposing) {
      event.preventDefault();
      paletteRun(paletteState.active);
    }
  });
  // the input keeps the focus while an entry is clicked or tapped — on a phone the on-screen
  // keyboard would otherwise close and move the entry away from under the finger
  list.addEventListener('mousedown', (event) => event.preventDefault());
  list.addEventListener('mousemove', (event) => {
    const option = event.target.closest('[role="option"]');
    if (option && Number(option.dataset.index) !== paletteState.active) {
      paletteSetActive(Number(option.dataset.index), false);
    }
  });
  list.addEventListener('click', (event) => {
    const option = event.target.closest('[role="option"]');
    if (option) paletteRun(Number(option.dataset.index));
  });
  // a click on the backdrop lands on the dialog element itself
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });
  dialog.addEventListener('close', () => {
    const origin = paletteState.origin;
    paletteState.origin = null;
    if (origin && origin !== document.body && document.body.contains(origin)) origin.focus();
  });
}

function paletteOpen() {
  const dialog = document.getElementById('commandPalette');
  if (!dialog || dialog.open) return;
  // a Bootstrap modal holds the focus inside itself and would take it straight back
  if (document.querySelector('.modal.show')) return;
  paletteWire(dialog);
  paletteState.origin = document.activeElement;
  paletteState.commands = paletteNavigationCommands().concat(paletteSettingsCommands(dialog));
  const input = document.getElementById('commandPaletteInput');
  input.value = '';
  dialog.showModal();
  input.focus();
  paletteUpdate(dialog);
}

// Capture phase, so that a field with key handlers of its own — TomSelect, the time input — never
// sees the shortcut, and preventDefault, so that the browser's own use of it (the search bar) does
// not happen. The other system's modifier stays free: Ctrl+K on a Mac deletes to the end of line.
document.addEventListener('keydown', function (event) {
  if (typeof event.key !== 'string' || event.key.toLowerCase() !== 'k') return;
  if (event.isComposing || event.altKey || event.shiftKey) return;
  const modifier = PALETTE_IS_MAC ? event.metaKey && !event.ctrlKey : event.ctrlKey && !event.metaKey;
  if (!modifier) return;
  event.preventDefault();
  event.stopPropagation();
  const dialog = document.getElementById('commandPalette');
  if (dialog && dialog.open) dialog.close();
  else paletteOpen();
}, true);

document.addEventListener('click', function (event) {
  if (event.target.closest('[data-command-palette-open]')) paletteOpen();
});

document.querySelectorAll('[data-command-palette-shortcut]').forEach(function (kbd) {
  kbd.textContent = PALETTE_IS_MAC ? kbd.dataset.labelMac : kbd.dataset.label;
  kbd.hidden = false;
});
document.querySelectorAll('[data-command-palette-open]').forEach(function (trigger) {
  trigger.setAttribute('aria-keyshortcuts', PALETTE_IS_MAC ? 'Meta+K' : 'Control+K');
});
