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

/* A contract the link named for the booking form (#760) wins over the selection there, so it gives
 * way to the one chosen now; otherwise the form stayed on the old contract and its suborders. */
function selectContract(id) {
  const url = new URL(window.location.href);
  url.searchParams.delete('employeecontractId');
  url.searchParams.set('fEmployeeContractId', String(id));
  location.href = url.toString();
}

// the other contracts of the person in the bar above the header switch like the selector (#1231)
document.addEventListener('click', event => {
  const link = event.target.closest('[data-select-contract]');
  if (link) selectContract(link.dataset.selectContract);
});

/* The refresh button of a list (#1286, #1287, fragments/refresh-button.html). A link that differs
 * from the address only in its fragment does not load anything - the browser just scrolls to the
 * anchor. That is the normal case on a page with tabs, whose open tab sits in the fragment; there
 * the click reloads the page itself, which keeps the address with its tab. A modified click (new
 * tab, new window) stays the browser's. */
document.addEventListener('click', event => {
  const link = event.target.closest('[data-refresh-link]');
  if (!link || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  const target = new URL(link.href);
  const current = new URL(window.location.href);
  target.hash = '';
  current.hash = '';
  if (target.href === current.href) {
    event.preventDefault();
    location.reload();
  }
});

/* Counts a use of a beta that happens in the browser alone (#1447): an element with
 * data-beta-usage="<beta>:<event>" reports its click. keepalive lets the request outlive a click that
 * leaves the page. Counting is best effort - whatever goes wrong is ignored, never shown. */
document.addEventListener('click', event => {
  const element = event.target.closest('[data-beta-usage]');
  if (!element) return;
  const [feature, usage] = element.dataset.betaUsage.split(':');
  if (!feature || !usage) return;
  const token = document.cookie.split('; ').find(r => r.startsWith('XSRF-TOKEN='))?.split('=')[1];
  fetch('/beta/usage', {
    method: 'POST',
    keepalive: true,
    headers: { 'X-XSRF-TOKEN': token ? decodeURIComponent(token) : '' },
    body: new URLSearchParams({ feature, event: usage }),
  }).catch(() => {});
});

/* The hint promoting a beta on its page (#1442, beta/hint.html): rendered hidden, shown unless it was
 * dismissed on this device. "Später" and closing dismiss it for good - in localStorage, not in the
 * preferences, since it is a nudge for the duration of the beta. */
function betaHintStorageKey(hint) {
  return `salat-beta-${hint.dataset.betaHint}-hint`;
}

document.querySelectorAll('[data-beta-hint]').forEach(hint => {
  let dismissed = false;
  try {
    dismissed = localStorage.getItem(betaHintStorageKey(hint)) === 'dismissed';
  } catch (e) {
    // private mode: the hint shows, it only cannot be dismissed for good
  }
  if (!dismissed) hint.hidden = false;
});

document.addEventListener('click', event => {
  const button = event.target.closest('[data-beta-hint-dismiss]');
  const hint = button?.closest('[data-beta-hint]');
  if (!hint) return;
  hint.hidden = true;
  try {
    localStorage.setItem(betaHintStorageKey(hint), 'dismissed');
  } catch (e) {
    // private mode: gone until the next page load
  }
});

/* Settings that apply only within a beta stand under its switch in a fieldset
 * data-beta-settings="<key>" (#1442): shown and sent only while the switch is on, so that switching
 * the beta off neither shows nor stores them. */
document.addEventListener('change', event => {
  const input = event.target;
  if (!input.matches?.('input[type="checkbox"][name="betaFeatures"]')) return;
  document.querySelectorAll(`fieldset[data-beta-settings="${CSS.escape(input.value)}"]`).forEach(fieldset => {
    fieldset.hidden = !input.checked;
    fieldset.disabled = !input.checked;
  });
});

/* The settings moved from the header into the user menu (#1231). A dot on the trigger and "Neu" on
 * the entry point there until the menu has been opened once; then both stay away. The dot goes on
 * opening, the badge only when the menu closes again, so that it is seen once. Both are taken out
 * again with a follow-up commit after some weeks, and the key with them. */
const USER_MENU_SEEN_KEY = 'salat-user-menu-seen';

function userMenuNews() {
  return document.querySelectorAll('[data-user-menu-news]');
}

(function () {
  let seen = false;
  try {
    seen = localStorage.getItem(USER_MENU_SEEN_KEY) === 'true';
  } catch (e) {
    // private mode: the marker shows on, it only cannot be dismissed for good
  }
  if (!seen) userMenuNews().forEach(el => { el.hidden = false; });
})();

document.addEventListener('shown.bs.dropdown', event => {
  if (event.target.id !== 'user-menu-toggle') return;
  try {
    localStorage.setItem(USER_MENU_SEEN_KEY, 'true');
  } catch (e) {
    // see above
  }
  event.target.querySelectorAll('[data-user-menu-news]').forEach(el => { el.hidden = true; });
});

document.addEventListener('hidden.bs.dropdown', event => {
  if (event.target.id === 'user-menu-toggle') userMenuNews().forEach(el => { el.hidden = true; });
});

/* Tabler faltet die Sidebar auf "folded-hover": gefaltet, solange die Maus nicht darueber steht.
 * Beim Klick steht sie aber genau dort, und die Leiste blieb offen, bis die Maus sie verliess. Bis
 * dahin gilt deshalb "folded", das kein Aufklappen beim Ueberfahren kennt; gespeichert bleibt
 * "folded-hover". Mit der Tastatur bleibt es bei Tabler: der Knopf hat den Fokus, und gefaltet
 * waere er ausgeblendet. */
document.addEventListener('tabler:sidebar-folded', event => {
  const nav = document.getElementById('salat-nav');
  if (!event.detail.folded || !nav?.matches(':hover') || nav.matches(':has(:focus-visible)')) return;
  const html = document.documentElement;
  html.setAttribute('data-bs-sidebar', 'folded');
  nav.addEventListener('mouseleave', () => {
    if (html.getAttribute('data-bs-sidebar') === 'folded') html.setAttribute('data-bs-sidebar', 'folded-hover');
  }, { once: true });
});

/* Der Tooltip des Faltknopfs nennt wie sein Icon, was der Klick tut: gefaltet klappt er aus. Das
 * aria-label bleibt, den Zustand traegt aria-pressed (base.html). */
function syncSidebarPinTitle() {
  const folded = (document.documentElement.getAttribute('data-bs-sidebar') ?? '').startsWith('folded');
  document.querySelectorAll('[data-bs-toggle="sidebar-folded"][data-title-folded]').forEach(pin => {
    pin.dataset.titleUnfolded ??= pin.title;
    pin.title = folded ? pin.dataset.titleFolded : pin.dataset.titleUnfolded;
  });
}

syncSidebarPinTitle();
document.addEventListener('tabler:sidebar-folded', syncSidebarPinTitle);

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

/* Wie die Anwendung eine Ticket-Referenz speichert (#1326, TicketReferences): ohne umgebende
 * Leerzeichen, ein Ticket-Schluessel in Grossbuchstaben, alles andere wie getippt. */
const TICKET_KEY = /^[A-Za-z][A-Za-z0-9_]+-[0-9]+$/;
function normalizeTicketReference(value) {
  const trimmed = (value || '').trim();
  return TICKET_KEY.test(trimmed) ? trimmed.toUpperCase() : trimmed;
}

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
    // TomSelect drops an option without a value unless told otherwise; a select whose empty value
    // means something says so with data-allow-empty-option (#1243: the top level of a suborder)
    allowEmptyOption: el.dataset.allowEmptyOption === 'true',
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
  // list is a convenience, and a value that matches nothing on the server is kept as entered. As a
  // <select multiple> it takes several values (#1326), each submitted on its own; data-max-items
  // bounds them, and the form changes the bound with the suborder (setTicketReferenceLimit).
  if (remoteUrl) {
    const contextField = el.dataset.remoteContextField || null;
    const contextParam = el.dataset.remoteContextParam || null;
    const fillTarget = el.dataset.fillTarget || null;
    const createLabel = el.dataset.createLabel || '';
    const context = () => (contextField ? (document.querySelector(contextField)?.value || '') : '');

    Object.assign(config, {
      // a typed ticket key is stored in capitals (#1326) - shown that way right away
      create: input => {
        const value = normalizeTicketReference(input);
        return { value, text: value };
      },
      createOnBlur: true,
      persist: false,
      maxItems: multi ? (el.dataset.maxItems ? Number(el.dataset.maxItems) : null) : 1,
      valueField: 'value',
      labelField: 'value',
      searchField: ['value', 'subtext'],
      // the dropdown_input plugin would put the typing into a second field above the list; here the
      // control itself is the text field
      plugins: multi ? { remove_button: removeButtonOptions() } : [],
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
        // an existing booking opens with its stored references, which a <select> brings as its
        // selected options; an <input> carries its one reference as value. Only the number is
        // stored, so there is no title to show for it yet
        if (el.tagName === 'SELECT') return;
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
            + '<span class="issue-key">' + escape(data.value) + '</span>'
            + (data.subtext
              ? '<small class="text-muted lh-1 mb-1 text-truncate">' + escape(data.subtext) + '</small>'
              : '')
            + '</div>';
        },
        item(data, escape) {
          return '<div class="issue-key">' + escape(data.value) + '</div>';
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

  // A picked entry of a multi-select is named by its short key (#1266): the option reads the whole
  // label, the chip only what tells it apart — a sign, a short name —, so that a few picks do not
  // wrap the field. The whole label stays at hand as the chip's tooltip.
  const chips = multi
    ? new Map(Array.from(el.options || []).filter(opt => opt.dataset.chip).map(opt => [opt.value, opt.dataset.chip]))
    : new Map();
  if (chips.size) {
    config.render = Object.assign({}, config.render, {
      item(data, escape) {
        const chip = chips.get(String(data.value));
        return chip
          ? '<div title="' + escape(data.text) + '">' + escape(chip) + '</div>'
          : '<div>' + escape(data.text) + '</div>';
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

/* ─── Sortable lists (#1414) ─────────────────────────────────────────────────
 *
 * A list whose order the person sets: dragged by a grip with the mouse or a finger (SortableJS), or
 * moved one step with an arrow button — the way for the keyboard and for anyone who cannot drag.
 * Both rearrange the page and then submit a form, and the server reads the new order off the page
 * (the dialog "Favoriten" while arranging: hidden inputs in document order). The browser keeps no second
 * model of the arrangement.
 *
 *   data-sortable          on the container; its direct children are the items
 *   data-sortable-item     on every item; an arrow button finds its item through it
 *   data-sortable-handle   CSS selector of the grip an item is dragged by
 *   data-sortable-group    containers of the same name exchange items
 *   data-sortable-sort     "false": no order within the container, items only move between containers
 *   data-sortable-form     id of the form submitted after every change
 *   data-sortable-move     "up" or "down", on a button inside an item
 *
 * An arrow moves an item one place; at the edge of its container it continues into the neighbouring
 * container of the same group. Where the container has no order of its own, the arrow goes straight
 * to the neighbouring container. An arrow that cannot move anything is disabled.
 * -------------------------------------------------------------------------- */

let sortableFocusAfterSwap = null;

function sortableItems(container) {
  return Array.from(container.children).filter(el => el.hasAttribute('data-sortable-item'));
}

function sortableNeighbour(container, direction) {
  const group = container.dataset.sortableGroup;
  if (!group) return null;
  const all = Array.from(document.querySelectorAll('[data-sortable]'))
    .filter(el => el.dataset.sortableGroup === group);
  return all[all.indexOf(container) + (direction === 'up' ? -1 : 1)] || null;
}

/** Where an arrow takes the item - the container and the element to insert before - or null. */
function sortableTarget(item, direction) {
  const container = item.parentElement;
  if (container.dataset.sortableSort !== 'false') {
    const items = sortableItems(container);
    const index = items.indexOf(item);
    if (direction === 'up' && index > 0) return { container, before: items[index - 1] };
    if (direction === 'down' && index < items.length - 1) return { container, before: items[index + 1].nextElementSibling };
  }
  const neighbour = sortableNeighbour(container, direction);
  if (!neighbour) return null;
  // coming from below it lands at the end of the container above, coming from above at the start
  return direction === 'up'
    ? { container: neighbour, before: null }
    : { container: neighbour, before: sortableItems(neighbour)[0] || null };
}

function sortableChanged(container) {
  const form = document.getElementById(container.dataset.sortableForm || '');
  if (form) form.requestSubmit();
}

function sortableUpdateArrows() {
  document.querySelectorAll('[data-sortable-move]').forEach(function (button) {
    const item = button.closest('[data-sortable-item]');
    if (!item || !item.parentElement.hasAttribute('data-sortable')) return;
    button.disabled = !sortableTarget(item, button.dataset.sortableMove);
  });
}

function initSortables() {
  if (typeof Sortable !== 'undefined') {
    document.querySelectorAll('[data-sortable]').forEach(function (container) {
      if (container.salatSortable) return;
      container.salatSortable = Sortable.create(container, {
        group: container.dataset.sortableGroup || undefined,
        sort: container.dataset.sortableSort !== 'false',
        handle: container.dataset.sortableHandle || undefined,
        animation: 150,
        ghostClass: 'sortable-ghost',
        chosenClass: 'sortable-chosen',
        // a list without items still takes a drop near it
        emptyInsertThreshold: 24,
        onEnd: function (event) {
          if (event.from === event.to && event.oldIndex === event.newIndex) return;
          sortableChanged(event.from);
        }
      });
    });
  }
  sortableUpdateArrows();
  if (sortableFocusAfterSwap) {
    let target = document.getElementById(sortableFocusAfterSwap);
    sortableFocusAfterSwap = null;
    // the arrow that moved the item to the edge is disabled now; its sibling still points the way back
    if (target && target.disabled) target = target.parentElement.querySelector('button:not([disabled])');
    if (target) target.focus();
  }
}

document.addEventListener('click', function (event) {
  const button = event.target.closest('[data-sortable-move]');
  if (!button) return;
  const item = button.closest('[data-sortable-item]');
  if (!item || !item.parentElement.hasAttribute('data-sortable')) return;
  const origin = item.parentElement;
  const target = sortableTarget(item, button.dataset.sortableMove);
  if (!target) return;
  target.container.insertBefore(item, target.before);
  sortableFocusAfterSwap = button.id || null;
  sortableChanged(origin);
});

initSortables();
document.addEventListener('htmx:after:swap', initSortables);

/* The confirmation dialog can stand in front of another dialog (deleting a group in "Favoriten").
   Bootstrap knows one dialog at a time and, closing the front one, frees the page as if none were left. */
document.addEventListener('hidden.bs.modal', function () {
  if (!document.querySelector('.modal.show')) return;
  document.body.classList.add('modal-open');
  document.body.style.overflow = 'hidden';
});

/* A dialog with data-reload-on-close stands in front of a page that shows the same data. After a
   change - its body carries data-changed="true" from the server then - closing it reloads the page.
   Not when a submit button inside closed it (data-bs-dismiss on a pick in "Favoriten"): that request
   refreshes the page itself, and a reload would cut it off. */
document.addEventListener('click', function (event) {
  const button = event.target.closest('button[type="submit"][data-bs-dismiss="modal"]');
  const modal = button && button.closest('.modal');
  if (modal) modal.dataset.closedBySubmit = 'true';
});

document.addEventListener('hidden.bs.modal', function (event) {
  const modal = event.target;
  if (!modal.hasAttribute('data-reload-on-close')) return;
  const bySubmit = modal.dataset.closedBySubmit === 'true';
  delete modal.dataset.closedBySubmit;
  if (!bySubmit && modal.querySelector('[data-changed="true"]')) window.location.reload();
});

/* A dialog whose content asks for the first keystroke carries autofocus there. Bootstrap focuses the
   dialog itself once it is shown, after htmx may already have honoured autofocus in the loaded body. */
document.addEventListener('shown.bs.modal', function (event) {
  event.target.querySelector('[autofocus]')?.focus();
});
document.addEventListener('htmx:after:swap', function (event) {
  const modal = event.target.closest && event.target.closest('.modal.show');
  modal?.querySelector('[autofocus]')?.focus();
});

/* ─── Filtering a list as you type (#1414) ───────────────────────────────────
 *
 *   data-list-filter       on the search field, a selector of the list it filters
 *   data-filter-text       on every entry, the text that is searched
 *   data-filter-group      on a group of entries; hidden without a hit, a <details> opens with one
 *   data-filter-empty      the note shown when nothing is left
 *   data-filter-disables   on a control, the selector of a search field; it rests while that holds a term
 *
 * Every word of the term has to occur, case and accents aside. Enter takes the only hit that is left.
 * -------------------------------------------------------------------------- */

function listFilterFold(text) {
  return (text || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}

function applyListFilter(input) {
  const list = document.querySelector(input.dataset.listFilter);
  if (!list) return;
  const words = listFilterFold(input.value).split(/\s+/).filter(Boolean);
  let visible = 0;
  list.querySelectorAll('[data-filter-text]').forEach(function (entry) {
    const text = listFilterFold(entry.dataset.filterText);
    const hit = words.every(word => text.includes(word));
    entry.classList.toggle('d-none', !hit);
    if (hit) visible++;
  });
  list.querySelectorAll('[data-filter-group]').forEach(function (group) {
    const hits = group.querySelectorAll('[data-filter-text]:not(.d-none)').length;
    group.classList.toggle('d-none', hits === 0);
    if (group.tagName !== 'DETAILS') return;
    if (words.length > 0) {
      if (group.dataset.filterWasOpen === undefined) group.dataset.filterWasOpen = String(group.open);
      group.open = hits > 0;
    } else if (group.dataset.filterWasOpen !== undefined) {
      group.open = group.dataset.filterWasOpen === 'true';
      delete group.dataset.filterWasOpen;
    }
  });
  list.querySelector('[data-filter-empty]')?.classList.toggle('d-none', visible > 0 || words.length === 0);
  document.querySelectorAll('[data-filter-disables]').forEach(function (control) {
    if (document.querySelector(control.dataset.filterDisables) === input) control.disabled = words.length > 0;
  });
}

document.addEventListener('input', function (event) {
  if (event.target.matches && event.target.matches('[data-list-filter]')) applyListFilter(event.target);
});

document.addEventListener('keydown', function (event) {
  const input = event.target;
  if (event.key !== 'Enter' || !input.matches || !input.matches('[data-list-filter]')) return;
  event.preventDefault();
  const list = document.querySelector(input.dataset.listFilter);
  const hits = list ? list.querySelectorAll('[data-filter-text]:not(.d-none)') : [];
  if (input.value.trim() && hits.length === 1) hits[0].click();
});

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
  // already on its way (data-submit-once, below): nothing to ask, the lock drops this submit
  if (form.salatSubmitted) return;
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

/* ─── Confirmation by typing (#1424, ADR-0027 addendum) ──────────────────────
 *
 * The irreversible actions — anonymizing a person, deleting a budget plan — sit in a dialog of
 * their own and ask for the key of the object to be typed twice:
 *
 *   data-confirm-typed         on the form, the value that has to be typed
 *   data-confirm-typed-input   on every field that has to hold it
 *
 * The submit buttons of the form stay disabled until every such field holds exactly that value; a
 * disabled default button also keeps Enter from sending. Opening the dialog around the form empties
 * the fields again. The server compares once more — this is a hurdle, not the check.
 * -------------------------------------------------------------------------- */

function updateTypedConfirmation(form) {
  const expected = form.dataset.confirmTyped;
  const inputs = Array.from(form.querySelectorAll('[data-confirm-typed-input]'));
  const matches = inputs.length > 0 && inputs.every(input => input.value === expected);
  form.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = !matches; });
}

document.addEventListener('input', function (event) {
  if (!event.target.matches || !event.target.matches('[data-confirm-typed-input]')) return;
  const form = event.target.closest('form[data-confirm-typed]');
  if (form) updateTypedConfirmation(form);
});

document.addEventListener('show.bs.modal', function (event) {
  event.target.querySelectorAll('form[data-confirm-typed]').forEach(function (form) {
    form.querySelectorAll('[data-confirm-typed-input]').forEach(input => { input.value = ''; });
    updateTypedConfirmation(form);
  });
});

/* ─── Submit once (#1237) ────────────────────────────────────────────────────
 *
 * A form marked data-submit-once goes out once: its buttons are locked from the submit until the
 * answer replaces the page, and a second click or Enter in that time sends nothing. Release and
 * acceptance take a second or two to answer, and a second request racing the first one fails on
 * the version of the contract.
 *
 * The listener sits on `document` in the bubble phase, behind the confirmation handler above. A
 * submit that handler holds back for its question never gets here; only the confirmed re-submit
 * does, and that one is the submit that locks. A second click on a locked form does not ask again:
 * the confirmation handler lets it through to be dropped here. A submit somebody else prevented
 * locks nothing — HTMX prevents its own and has its own means. The buttons are not set `disabled`,
 * which would drop the pressed one from the form data; they get `disabled` as a class (no pointer
 * events) and `aria-disabled`. Keyboard and the shortcut still reach the form and are dropped here.
 *
 * The browser restores a page from its back-forward cache as it was left, locked; `pageshow`
 * unlocks it again.
 * -------------------------------------------------------------------------- */

function lockSubmitButtons(form, locked) {
  form.querySelectorAll('button[type="submit"], button:not([type]), input[type="submit"]').forEach(function (button) {
    button.classList.toggle('disabled', locked);
    if (locked) button.setAttribute('aria-disabled', 'true');
    else button.removeAttribute('aria-disabled');
  });
}

document.addEventListener('submit', function (event) {
  const form = event.target;
  if (!form.hasAttribute('data-submit-once') || event.defaultPrevented) return;
  if (form.salatSubmitted) {
    event.preventDefault();
    return;
  }
  form.salatSubmitted = true;
  lockSubmitButtons(form, true);
});

window.addEventListener('pageshow', function (event) {
  if (!event.persisted) return;
  document.querySelectorAll('form[data-submit-once]').forEach(function (form) {
    form.salatSubmitted = false;
    lockSubmitButtons(form, false);
  });
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
 * A field without data-time-step gets no extra controls, only the tolerant parsing below.
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
  // A form the command palette prefilled names where to go on (#1158): the first field it left
  // empty, or the save button when it filled them all. Only while that field can take the focus.
  const named = wrapper.querySelector('[data-entry-focus]');
  const field = named && (named.matches('button') ? !named.disabled && named.offsetParent !== null
    : isEntryFocusCandidate(named))
    ? named
    : Array.from(wrapper.querySelectorAll(ENTRY_FOCUS_SELECTOR)).find(isEntryFocusCandidate);
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
 * saves anything, which is why no command needs a confirmation. The one form it submits is the
 * login's own (#1231, ADR-0030 addendum): a login switch and its end, through the forms of the user
 * menu and of the switch dialog; they change no data.
 *
 * What it offers is read from the rendered page instead of from a list of its own:
 *
 *   #sidebar-menu .dropdown-item[href]  the navigation. Label, section and the role filter come
 *                                       along, and a new menu entry shows up without further ado.
 *   data-palette-href-from              on a sidebar entry: selector of an element whose href wins
 *                                       over the entry's own, where that element is on the page
 *   data-palette-command                on a control of the sidebar, the footer or the user menu:
 *                                       the key a settings command is remembered by. Its accessible name is
 *                                       the label, clicking it is the action, and it is offered
 *                                       only while displayed — in the user menu (data-palette-menu)
 *                                       whenever rendered, see paletteOffers.
 *   data-palette-keywords               further words a command is found by
 *   data-palette-label-pressed          the label while the control is aria-pressed
 *   data-palette-kind                   the kind shown next to it, where it is not a setting
 *   data-palette-forget                 not remembered under "recently used"
 *   #loginSwitchModal form              one hit per person to switch to, found only by typing
 *
 * Day jumps into the daily view are read in the browser, against the server's today carried by
 * the dialog (see paletteToday). Neither opening the palette nor any of these hits sends a request;
 * only the business objects — orders, suborders, customers, persons — come from the server (#1157,
 * see "Objects from the server" below), and every module decides there what the user may see.
 * -------------------------------------------------------------------------- */

const PALETTE_RECENT_KEY = 'salat-command-palette-recent';
const PALETTE_RECENT_MAX = 10;
// Apple's systems name the modifiers differently and put the shortcuts on ⌘ — the palette and
// the shortcuts below (#1016) both ask this
const IS_MAC = /mac|iphone|ipad|ipod/i.test(
  (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || '');

// ranks, highest first; a day jump sits between a word start and a hit inside a word, so that
// "fr" offers the page "Freigabe" first and the Friday right below it. A command found only by one
// of its further words shows nothing that matches, and ranks below everything that does.
const PALETTE_TIER_WORD_START = 3;
const PALETTE_TIER_DAY = 2.5;
// A command with parameters (#1158) found by the beginning of its word ranks below the pages and the
// day, so that "mat" and Enter still lead to the matrix view; typed as its whole word, it comes first.
const PALETTE_TIER_COMMAND = 2.25;
const PALETTE_TIER_INSIDE = 2;
const PALETTE_TIER_FUZZY = 1;
const PALETTE_TIER_KEYWORD = 0.75;
const PALETTE_TIER_SECTION = 0.5;

// wiredDialog is kept here and not as an attribute: the history cache of htmx restores the page
// from a copy of its markup, and a copied attribute would claim listeners the new dialog lacks
const paletteState = { origin: null, commands: [], items: [], active: -1, wiredDialog: null,
  clockSkew: null, pressedOnBackdrop: false,
  // the object search (#1157): items before localCount are the page's own, the rest came from the
  // server; token discards an answer overtaken by the next keystroke; objects is the last answer
  localCount: 0, objectToken: 0, objectTimer: null, objects: null,
  // the targets of one object, opened with → ({ item, query }); heldKey is the key that last went
  // into or out of them, as long as it is held
  drill: null, heldKey: null,
  // the command being entered (#1158): { verb, values, fallback, label }; valueParam is the
  // parameter the list offers values for and valueQuery what of the field they answer. The answers
  // of the server are kept per address until the palette closes; openToken drops a late one.
  invocation: null, valueParam: null, valueQuery: '', valueCache: new Map(), valueTimer: null, openToken: 0 };

// the object search asks the server from two characters on, once the typing pauses (#1157)
const PALETTE_OBJECT_MIN_LENGTH = 2;
const PALETTE_OBJECT_DELAY = 150;

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

/**
 * A calendar date, or null for one that does not exist (31.2.) — and for a year before 1000,
 * which is a typing error here and no date the daily view accepts in ISO form.
 */
function paletteValidDate(year, month, day) {
  if (year < 1000) return null;
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

/**
 * Today as the server counts it: the day in the server's time zone at the server's time. The page
 * brings that time along, but a tab stays open past midnight and a page comes back from the
 * browser's cache — what is kept is therefore how far the browser's clock is off, taken at load
 * time, and the server's time is the browser's plus that. A difference of days would not do: with
 * the browser in another time zone, the two days change at different moments.
 */
function paletteToday(dialog) {
  if (paletteState.clockSkew === null) {
    paletteState.clockSkew = Number(dialog.dataset.now) - Date.now();
  }
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: dialog.dataset.timeZone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date(Date.now() + paletteState.clockSkew));
  const part = (type) => parts.find(p => p.type === type).value;
  return part('year') + '-' + part('month') + '-' + part('day');
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

/**
 * Whether a control is offered. Elsewhere that is whether it is displayed; in the user menu
 * (data-palette-menu, #1231) it is whether the server rendered it, since the closed menu hides
 * every entry. Only the colour mode needs a word more: there both entries are rendered and CSS
 * hides the one for the mode in force — the palette asks the mode instead.
 */
function paletteOffers(el) {
  if (el.closest('[data-palette-menu]')) {
    return !el.dataset.paletteTheme
      || el.dataset.paletteTheme !== document.documentElement.getAttribute('data-bs-theme');
  }
  return el.checkVisibility ? el.checkVisibility() : el.offsetParent !== null;
}

function paletteSettingsCommands(dialog) {
  return Array.from(document.querySelectorAll('[data-palette-command]'))
    .filter(paletteOffers)
    .map(el => ({
      type: 'cmd',
      key: el.dataset.paletteCommand,
      label: el.getAttribute('aria-pressed') === 'true' && el.dataset.paletteLabelPressed
        ? el.dataset.paletteLabelPressed
        : (el.getAttribute('aria-label') || paletteCleanText(el)),
      kind: el.dataset.paletteKind || dialog.dataset.kindSettings,
      keywords: el.dataset.paletteKeywords || '',
      forget: el.hasAttribute('data-palette-forget'),
      href: el.href || null,
      // a submit button goes through its form, so that the form's own checks run as on a click
      run: () => (el.type === 'submit' && el.form ? el.form.requestSubmit(el) : el.click()),
    }));
}

/**
 * One hit per person the login switch offers (#1231): the forms of #loginSwitchModal, which the
 * server renders only for whoever may switch, and to exactly the persons allowed. Enter submits
 * that form — the palette sends no request of its own and asks nothing the dialog does not. They
 * follow the entry of the menu: while a switch is running, the menu offers only its end.
 */
function paletteLoginSwitchCommands(dialog) {
  if (!document.querySelector('[data-palette-menu] [data-palette-command="login-switch"]')) return [];
  return Array.from(document.querySelectorAll('#loginSwitchModal form')).map(form => {
    const name = form.querySelector('[data-switch-name]')?.textContent.trim() || '';
    const sign = form.querySelector('[data-switch-sign]')?.textContent.trim() || '';
    return {
      type: 'login',
      key: sign,
      label: dialog.dataset.labelSwitchTo + ' ' + name + ' (' + sign + ')',
      kind: dialog.dataset.kindAccount,
      href: null,
      run: () => form.requestSubmit(),
    };
  });
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
    // whatever else stands there is not ours to interpret
    return Array.isArray(stored)
      ? stored.filter(e => e && typeof e.t === 'string' && typeof e.k === 'string')
      : [];
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
    // A person to switch to is found by name, sign or the words of its label, never by letters in
    // order: "buch" would otherwise run through "Benutzer wechseln" and list every person (#1231).
    if (command.type === 'login') {
      if (match && match.tier >= PALETTE_TIER_INSIDE) {
        hits.push({ command, ranges: match.ranges, tier: match.tier, pos: match.pos, order });
      }
      return;
    }
    if (match && command.type === 'verb') {
      match = paletteFold(command.label).folded === paletteFold(query.trim()).folded
        ? { tier: PALETTE_TIER_WORD_START, pos: -1, ranges: match.ranges }
        : { tier: Math.min(match.tier, PALETTE_TIER_COMMAND), pos: match.pos, ranges: match.ranges };
    }
    if (!match && command.keywords) {
      const byKeyword = paletteMatch(command.keywords, query);
      if (byKeyword) match = { tier: PALETTE_TIER_KEYWORD, pos: byKeyword.pos, ranges: [] };
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

function paletteOption(hit, index, dialog) {
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
  // An object with more than one target: → on the keyboard, a click or tap on the arrow for the
  // pointer. The arrow is no button of its own — the row is the option, and focus stays in the field.
  // a value that cannot be chosen (#1158): shown, so that the one looked for is not missing without a
  // word, but skipped by the arrows and not taken
  if (hit.command.disabled) {
    option.setAttribute('aria-disabled', 'true');
    option.classList.add('command-palette-option-disabled');
  }
  // its parameters are what tells a command apart, so they are not cut short (salat.css)
  if (hit.command.type === 'verb') option.classList.add('command-palette-option-verb');
  if (hit.command.type === 'object' && hit.command.targets.length > 1) {
    const more = document.createElement('span');
    more.className = 'command-palette-more';
    more.dataset.paletteMore = '';
    more.title = dialog.dataset.targetsMore;
    more.setAttribute('aria-hidden', 'true');
    more.textContent = '→';
    option.append(more);
  }
  return option;
}

/** A group of hits with its heading, appended to the list; returns the element the hits go into. */
function paletteGroupContainer(list, label, id) {
  if (!label) return list;
  const container = document.createElement('div');
  container.setAttribute('role', 'group');
  const heading = document.createElement('div');
  heading.className = 'command-palette-group';
  heading.id = id;
  heading.setAttribute('role', 'presentation');
  heading.textContent = label;
  container.setAttribute('aria-labelledby', heading.id);
  container.append(heading);
  list.append(container);
  return container;
}

/**
 * Shows the hits of the page. With {@code pending} the server is still to answer: then the list is
 * not announced as empty yet — "Keine Treffer" before the objects arrive would be a false alarm.
 */
function paletteRender(groups, pending) {
  const input = document.getElementById('commandPaletteInput');
  const list = document.getElementById('commandPaletteList');
  list.replaceChildren();
  paletteState.items = [];
  groups.forEach((group, groupIndex) => {
    const container = group.hits.length
      ? paletteGroupContainer(list, group.label, 'commandPaletteGroup' + groupIndex) : list;
    group.hits.forEach(hit => {
      container.append(paletteOption(hit, paletteState.items.length));
      paletteState.items.push(hit.command);
    });
  });
  paletteState.localCount = paletteState.items.length;
  paletteAnnounce(pending);
  paletteSetActive(paletteState.items.length ? 0 : -1, true);
}

function paletteAnnounce(pending) {
  const input = document.getElementById('commandPaletteInput');
  // the status region stays in the page and only changes its text: a region that appears together
  // with its content is not announced
  const empty = document.getElementById('commandPaletteEmpty');
  empty.textContent = paletteState.items.length || pending ? '' : empty.dataset.text;
  input.setAttribute('aria-expanded', String(paletteState.items.length > 0));
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
  const today = paletteToday(dialog);
  const vocabulary = paletteVocabulary(dialog);
  const asksServer = query.trim().length >= PALETTE_OBJECT_MIN_LENGTH;
  if (query.trim()) {
    paletteRender([{ hits: paletteSearch(dialog, paletteState.commands, query, today, vocabulary) }], asksServer);
    paletteRequestObjects(dialog, query);
    return;
  }
  paletteRequestObjects(dialog, query);
  const recent = paletteRecentItems(dialog, paletteState.commands, today, vocabulary);
  if (recent.length) {
    paletteRender([{ label: dialog.dataset.groupRecent, hits: recent.map(command => ({ command })) }]);
    return;
  }
  // nothing used yet: then everything there is, so that the first look shows what can be found
  const ofType = (type) => paletteState.commands.filter(command => command.type === type)
    .map(command => ({ command }));
  paletteRender([
    { label: dialog.dataset.groupPages, hits: ofType('nav') },
    { label: dialog.dataset.kindSettings, hits: ofType('cmd') },
    { label: dialog.dataset.groupCommands, hits: ofType('verb') },
  ]);
}

function paletteRun(index) {
  const command = paletteState.items[index];
  if (!command) return;
  // a command with parameters is not run but entered (#1158); the palette stays open for them
  if (command.type === 'verb') {
    command.run();
    return;
  }
  // An object is not remembered: whether it may still be opened is the server's question, and a
  // remembered entry is shown without asking it. Neither is a change of the login (#1231): the top
  // remembered entry is preselected on an empty input, and a stray Enter would log out or switch.
  if (command.type !== 'object' && command.type !== 'target' && command.type !== 'login' && !command.forget) {
    paletteRemember({ t: command.type, k: command.key });
  }
  document.getElementById('commandPalette').close();
  command.run();
}

/* ─── Objects from the server (#1157, ADR-0031) ───
 *
 * From two characters on, the palette asks /palette/search once the typing pauses, and appends the
 * answer below the page's own hits: the selected row does not move. An answer the next keystroke has
 * overtaken is dropped. Until the new answer is there, the previous one stays, narrowed to what still
 * matches, so that the objects do not blink away with every letter.
 */

function paletteRequestObjects(dialog, query) {
  clearTimeout(paletteState.objectTimer);
  const token = ++paletteState.objectToken;
  if (query.trim().length < PALETTE_OBJECT_MIN_LENGTH) {
    paletteState.objects = null;
    return;
  }
  if (paletteState.objects) {
    const same = paletteState.objects.query === query;
    paletteShowObjects(dialog, paletteState.objects.root, query, !same, !same);
    if (same) return;
  }
  paletteState.objectTimer = setTimeout(() => {
    fetch(dialog.dataset.searchUrl + '?q=' + encodeURIComponent(query), { headers: { 'HX-Request': 'true' } })
      .then(response => (response.ok && !response.redirected ? response.text() : null))
      .catch(() => null)
      .then(html => {
        // overtaken, closed, or the targets of an object opened meanwhile
        if (token !== paletteState.objectToken || !dialog.open || paletteState.drill) return;
        const root = html && new DOMParser().parseFromString(html, 'text/html')
          .querySelector('[data-palette-results]');
        // No fragment — a 403 before any controller, a login page, no network: no objects, and the
        // page's own hits stand as they are.
        paletteState.objects = root ? { query, root } : null;
        paletteShowObjects(dialog, root, query, false, false);
      });
  }, PALETTE_OBJECT_DELAY);
}

/** The objects of one hit element of the fragment, as a command of the palette. */
function paletteObjectCommand(article) {
  const part = (name) => article.querySelector('[data-part="' + name + '"]')?.textContent || '';
  const title = part('title');
  const subtitle = part('subtitle');
  const targets = Array.from(article.querySelectorAll('[data-part="target"]'))
    .map(link => ({ label: link.textContent, href: link.getAttribute('href') }));
  const markers = Array.from(article.querySelectorAll('[data-part="marker"]')).map(marker => marker.textContent);
  return {
    type: 'object',
    key: article.dataset.kind + ':' + article.dataset.key,
    label: subtitle ? title + ' · ' + subtitle : title,
    title,
    kind: markers.concat(part('context') ? [part('context')] : []).join(' · '),
    targets,
    run: () => window.location.assign(targets[0].href),
  };
}

/**
 * Replaces the objects below the page's hits. {@code stale} marks an answer to an earlier query,
 * narrowed to what still matches until the new one arrives; {@code pending} keeps the list from being
 * announced as empty meanwhile. The selected row stays where it is; a selected object stays selected
 * if it is still there.
 */
function paletteShowObjects(dialog, root, query, stale, pending) {
  const list = document.getElementById('commandPaletteList');
  const active = paletteState.items[paletteState.active];
  list.querySelectorAll('[data-palette-server]').forEach(group => group.remove());
  paletteState.items.length = paletteState.localCount;

  let firstObject = -1;
  (root ? Array.from(root.querySelectorAll('[data-palette-group]')) : []).forEach((section, groupIndex) => {
    const hits = Array.from(section.querySelectorAll('[data-palette-hit]'))
      .map(paletteObjectCommand)
      .filter(command => command.targets.length && (!stale || paletteMatch(command.label, query)));
    if (!hits.length) return;
    const heading = section.querySelector('[data-part="heading"]')?.textContent || '';
    const container = paletteGroupContainer(list, heading, 'commandPaletteObjects' + groupIndex);
    container.dataset.paletteServer = '';
    hits.forEach(command => {
      const match = paletteMatch(command.label, query);
      const index = paletteState.items.length;
      if (firstObject < 0) firstObject = index;
      container.append(paletteOption({ command, ranges: match ? match.ranges : [] }, index, dialog));
      paletteState.items.push(command);
    });
  });

  paletteAnnounce(pending);
  if (active && active.type === 'object') {
    // gone with the new answer, the selection falls back to the first object, then to the first
    // hit of the page; where rows came in above it, it is scrolled back into view
    const kept = paletteState.items.findIndex(item => item.key === active.key);
    const index = kept >= 0 ? kept : firstObject >= 0 ? firstObject : paletteState.localCount ? 0 : -1;
    paletteSetActive(index, index !== paletteState.active);
  } else if (paletteState.active < 0 && firstObject >= 0) {
    // nothing was selected — the page had no hit —, so selecting the first object moves nothing
    paletteSetActive(firstObject, true);
  }
}

/* ─── The targets of an object: → opens them, ← leads back ─── */

function paletteDrill(dialog, item) {
  const input = document.getElementById('commandPaletteInput');
  clearTimeout(paletteState.objectTimer);
  paletteState.objectToken++;
  paletteState.drill = { item, query: input.value };
  const crumb = document.getElementById('commandPaletteCrumb');
  crumb.querySelector('[data-part="query"]').textContent = input.value;
  crumb.querySelector('[data-part="object"]').textContent = item.label;
  crumb.hidden = false;
  input.value = '';
  // what the field searches for now are the targets; "Seite, Tag, …" would promise something else
  input.dataset.placeholder = input.placeholder;
  input.placeholder = '';
  paletteRenderTargets('');
}

function paletteRenderTargets(filter) {
  const item = paletteState.drill.item;
  const hits = item.targets
    .map(target => ({ target, match: filter.trim() ? paletteMatch(target.label, filter) : { ranges: [] } }))
    .filter(entry => entry.match)
    .map(entry => ({
      command: {
        type: 'target', key: entry.target.href, label: entry.target.label, kind: '',
        run: () => window.location.assign(entry.target.href),
      },
      ranges: entry.match.ranges,
    }));
  paletteRender([{ hits }], false);
}

function paletteUndrill(dialog) {
  const drill = paletteState.drill;
  paletteState.drill = null;
  document.getElementById('commandPaletteCrumb').hidden = true;
  const input = document.getElementById('commandPaletteInput');
  input.value = drill.query;
  input.placeholder = input.dataset.placeholder || input.placeholder;
  paletteUpdate(dialog);
  // back on the object the targets belonged to
  const index = paletteState.items.findIndex(item => item.key === drill.item.key);
  if (index >= 0) paletteSetActive(index, true);
}

function paletteCaretAt(input, position) {
  return input.selectionStart === position && input.selectionEnd === position;
}

/* ─── Commands with parameters (#1158) ───
 *
 * A sidebar entry marked data-palette-verb offers the command of its page, so the sidebar decides
 * which commands a user gets, as it does for the pages (ADR-0030). Typed as a word of its own
 * ("buchen ") or taken with Tab, the command becomes a chip, and what follows is read as its
 * parameters, in their order: a complete word that means exactly one value becomes a chip, and
 * whatever doesn't stays in the field and gets suggestions. An optional parameter the word does not
 * fit is passed over — "buchen wart" books today. Tab takes the selected suggestion, Backspace in
 * the empty field gives the last chip back, a click on a chip takes it out. Enter opens the target
 * with what is there — a prefilled form or a review page, never an action.
 *
 * Day, month and duration are read here, so that the preview follows every keystroke. The last
 * working day, suborders, persons, orders and the months of release and acceptance come from
 * /palette/suggest, answered by the module that owns the command's page; the tickets come from
 * the booking form's own endpoint. A ticket is taken only by its whole number — any other word
 * begins the comment —, and without a comment of its own the form gets the ticket's number and
 * title, as a pick in the form writes them.
 */

// the parameters of each command in the order they are read; "?" marks one that may be left out
const PALETTE_VERBS = {
  book: ['day?', 'suborder', 'duration?', 'ticket?', 'comment?'],
  day: ['day'],
  matrix: ['month', 'person?'],
  release: ['month'],
  accept: ['person', 'month'],
  controlling: ['customerorder'],
};
const PALETTE_SERVER_PARAMS = ['suborder', 'person', 'customerorder'];
const PALETTE_PENDING = 'pending';

function paletteCapitalized(word) {
  return word.charAt(0).toUpperCase() + word.slice(1);
}

function paletteParams(verb) {
  return PALETTE_VERBS[verb].map(spec => ({ type: spec.replace('?', ''), optional: spec.endsWith('?') }));
}

function paletteParamName(dialog, type) {
  return dialog.dataset['param' + paletteCapitalized(type)] || type;
}

/** The commands the sidebar offers, once each, in the order of PALETTE_VERBS. */
function paletteVerbCommands(dialog) {
  const entries = new Map();
  document.querySelectorAll('#sidebar-menu [data-palette-verb]').forEach(link => {
    if (PALETTE_VERBS[link.dataset.paletteVerb] && !entries.has(link.dataset.paletteVerb)) {
      entries.set(link.dataset.paletteVerb, link);
    }
  });
  return Object.keys(PALETTE_VERBS).filter(verb => entries.has(verb)).map(verb => ({
    type: 'verb',
    key: verb,
    label: dialog.dataset['verb' + paletteCapitalized(verb)],
    kind: paletteParams(verb).map(param => paletteParamName(dialog, param.type)).join(' · '),
    keywords: '',
    // the entry's own href, not the one of data-palette-href-from: the fallback is the plain page
    fallback: { href: entries.get(verb).getAttribute('href'), label: paletteCleanText(entries.get(verb)) },
    run: () => paletteInvoke(dialog, verb, ''),
  }));
}

/* ─── Reading month and duration ─── */

function paletteMonthOf(year, month) {
  return year + '-' + timeInputPad(month);
}

function paletteShiftMonths(ym, months) {
  const [year, month] = ym.split('-').map(Number);
  const index = year * 12 + month - 1 + months;
  return paletteMonthOf(Math.floor(index / 12), index % 12 + 1);
}

/**
 * Reads a month from what was typed, relative to `today` (ISO): a number 1–12, M/JJJJ, M.JJ or
 * JJJJ-MM, the name of a month from three letters on, or the word for the last month. A month
 * without its year is the most recent one, the current month included — in January, "dez" is the
 * December before. Returns every month the input can mean, as `{ ym }`.
 *
 * @param vocabulary `{ months: [january … december], last: word for the last month }`
 */
function paletteParseMonth(raw, today, vocabulary) {
  const input = paletteFold(String(raw || '').trim()).folded;
  if (!input) return [];
  const current = today.slice(0, 7);
  const recent = (month) => {
    const ym = paletteMonthOf(Number(today.slice(0, 4)), month);
    return { ym: ym <= current ? ym : paletteShiftMonths(ym, -12) };
  };
  const valid = (year, month) => (month >= 1 && month <= 12 && year >= 1000 ? [{ ym: paletteMonthOf(year, month) }] : []);
  let match;
  if ((match = /^(\d{1,2})$/.exec(input))) return valid(1000, Number(match[1])).length ? [recent(Number(match[1]))] : [];
  if ((match = /^(\d{1,2})[/.](\d{2}|\d{4})$/.exec(input))) {
    return valid(match[2].length === 2 ? 2000 + Number(match[2]) : Number(match[2]), Number(match[1]));
  }
  if ((match = /^(\d{4})-(\d{1,2})$/.exec(input))) return valid(Number(match[1]), Number(match[2]));
  if (input.length < 3) return [];
  const months = [];
  if (vocabulary.last && paletteFold(vocabulary.last).folded.startsWith(input)) {
    months.push({ ym: paletteShiftMonths(current, -1) });
  }
  vocabulary.months.forEach((name, index) => {
    if (paletteFold(name).folded.startsWith(input)) months.push(recent(index + 1));
  });
  return months;
}

/** "September 2026" in the language of the page. */
function paletteFormatMonth(ym, lang) {
  const [year, month] = ym.split('-').map(Number);
  return new Intl.DateTimeFormat(lang, { month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(Date.UTC(year, month - 1, 1)));
}

function paletteMonthVocabulary(dialog) {
  const lang = document.documentElement.lang || 'de';
  const format = new Intl.DateTimeFormat(lang, { month: 'long', timeZone: 'UTC' });
  return {
    months: Array.from({ length: 12 }, (_, index) => format.format(new Date(Date.UTC(2000, index, 1)))),
    last: dialog.dataset.monthLast,
  };
}

/**
 * A duration as the time field reads it (parseDurationValue: 1:30, 1h30, 90m, 1,5), in minutes;
 * null for anything else, and for nothing or more than a day.
 */
function paletteParseDuration(raw) {
  const minutes = parseDurationValue(raw);
  return minutes && minutes > 0 && minutes <= TIME_INPUT_MAX_DURATION ? minutes : null;
}

/** "1:30" — without the leading zero of the time field, as it is said. */
function paletteFormatDuration(minutes) {
  return Math.floor(minutes / 60) + ':' + timeInputPad(minutes % 60);
}

/** "Fr 25.09." — a day on a chip, where the year is rarely in question. */
function paletteShortDay(iso, lang) {
  return paletteFormatDay(iso, lang).replace(/\d{4}$/, '');
}

/* ─── Values from the server ─── */

function paletteSuggestUrl(dialog, invocation, type, query) {
  const params = new URLSearchParams({ command: invocation.verb.toUpperCase(), parameter: type.toUpperCase(), q: query });
  const day = paletteValue(invocation, 'day');
  if (invocation.verb === 'book' && day) params.set('date', day.value);
  const person = paletteValue(invocation, 'person');
  if (invocation.verb === 'accept' && person) params.set('contractId', person.value);
  return dialog.dataset.suggestUrl + '?' + params;
}

function paletteTicketUrl(dialog, suborderId, query) {
  return dialog.dataset.ticketUrl + '?' + new URLSearchParams({ suborderId, q: query });
}

function paletteSuggestionOf(article) {
  const part = (name) => article.querySelector('[data-part="' + name + '"]')?.textContent || '';
  return {
    value: article.dataset.value,
    label: part('label'),
    detail: part('detail'),
    note: part('note'),
    disabled: article.dataset.disabled === 'true',
    commentRequired: article.dataset.commentRequired === 'true',
    exact: article.dataset.exact === 'true',
  };
}

/**
 * The answer for `url` if it is there, otherwise undefined — and the request goes out: at once, or
 * with `delayed` once the typing pauses, as for the object search. Every answer is kept until the
 * palette closes; when one arrives, the command is read again.
 */
function paletteFetchValues(dialog, url, delayed, read) {
  const cache = paletteState.valueCache;
  if (cache.has(url)) {
    const known = cache.get(url);
    return known === PALETTE_PENDING ? undefined : known;
  }
  const send = () => {
    if (cache.has(url)) return;
    cache.set(url, PALETTE_PENDING);
    const token = paletteState.openToken;
    fetch(url, { headers: { 'HX-Request': 'true' } })
      .then(response => (response.ok && !response.redirected ? response.text() : null))
      .catch(() => null)
      .then(body => {
        if (token !== paletteState.openToken) return;
        let values = [];
        try {
          values = body ? read(body) : [];
        } catch (e) {
          // an answer that is no answer offers nothing
        }
        cache.set(url, values);
        if (dialog.open && paletteState.invocation) paletteInvocationUpdate(dialog);
      });
  };
  clearTimeout(paletteState.valueTimer);
  if (delayed) paletteState.valueTimer = setTimeout(send, PALETTE_OBJECT_DELAY);
  else send();
  return undefined;
}

function paletteServerValues(dialog, invocation, type, query, delayed) {
  return paletteFetchValues(dialog, paletteSuggestUrl(dialog, invocation, type, query), delayed, body => {
    const root = new DOMParser().parseFromString(body, 'text/html').querySelector('[data-palette-suggestions]');
    return root ? Array.from(root.querySelectorAll('[data-palette-suggestion]')).map(paletteSuggestionOf) : [];
  });
}

/**
 * The ticket suggestions of the booking form, for the chosen suborder. `comment` is what the form
 * writes into an untouched comment when a ticket is picked there: number and title.
 */
function paletteTicketValues(dialog, suborderId, query, delayed) {
  return paletteFetchValues(dialog, paletteTicketUrl(dialog, suborderId, query), delayed, body =>
    JSON.parse(body).map(ticket => ({
      value: ticket.key, label: ticket.key, detail: ticket.summary || '', note: '',
      comment: ticket.summary ? ticket.key + ' - ' + ticket.summary : ticket.key,
      exact: paletteFold(ticket.key).folded === paletteFold(query.trim()).folded,
    })));
}

/** The last working day, from the server: weekends and public holidays are its knowledge. */
function paletteLastWorkday(dialog, invocation) {
  const answer = paletteServerValues(dialog, { verb: invocation.verb, values: {} }, 'day', '', false);
  return answer && answer.length ? answer[0].value : null;
}

/* ─── The command being entered ─── */

function paletteValue(invocation, type) {
  const value = invocation.values[type];
  return value && !value.skipped ? value : null;
}

/** The first parameter without a value; the one the typed text is read for. */
function paletteCurrentParam(invocation) {
  return paletteParams(invocation.verb).find(param => !(param.type in invocation.values)) || null;
}

function paletteNextParam(invocation, param) {
  const params = paletteParams(invocation.verb);
  return params.slice(params.findIndex(p => p.type === param.type) + 1)
    .find(p => !(p.type in invocation.values)) || null;
}

/**
 * Whether `text` begins with the words for the last working day: `{ rest }` behind them once they
 * are complete and followed by a space, 'partial' while they are still being typed.
 */
function paletteLastWorkdayPhrase(dialog, text) {
  const phrase = paletteFold(dialog.dataset.dayLastworkday || '').folded;
  const folded = paletteFold(text).folded.replace(/\s+/g, ' ');
  if (!phrase) return null;
  if (folded.startsWith(phrase + ' ')) {
    const words = phrase.split(' ').length;
    return { rest: text.replace(/^\s+/, '').split(/\s+/).slice(words).join(' ') };
  }
  const typed = folded.trim();
  return typed.length >= 3 && phrase.startsWith(typed) ? 'partial' : null;
}

/**
 * What a complete word means for a parameter: a value, 'skip' where an optional parameter does not
 * fit it, 'open' where it means nothing or more than one thing, 'pending' while the server is asked.
 */
function paletteResolveWord(dialog, invocation, param, word) {
  const lang = document.documentElement.lang || 'de';
  const today = paletteToday(dialog);
  if (param.type === 'day') {
    const days = paletteParseDay(word, today, paletteVocabulary(dialog));
    if (days.length === 1) return { value: days[0].iso, label: paletteShortDay(days[0].iso, lang) };
    return days.length || !param.optional ? 'open' : 'skip';
  }
  if (param.type === 'month') {
    const months = paletteParseMonth(word, today, paletteMonthVocabulary(dialog));
    return months.length === 1 ? { value: months[0].ym, label: paletteFormatMonth(months[0].ym, lang) } : 'open';
  }
  if (param.type === 'duration') {
    const minutes = paletteParseDuration(word);
    if (minutes) return { value: paletteFormatDuration(minutes), label: paletteFormatDuration(minutes) };
    return param.optional ? 'skip' : 'open';
  }
  if (param.type === 'ticket') {
    // a ticket only by its whole number: any other word is the beginning of the comment
    const suborder = paletteValue(invocation, 'suborder');
    if (!suborder || suborder.error) return 'skip';
    const answer = paletteTicketValues(dialog, suborder.value, word, false);
    if (!answer) return 'pending';
    const exact = answer.find(value => value.exact);
    return exact ? paletteTaken(exact) : 'skip';
  }
  if (PALETTE_SERVER_PARAMS.includes(param.type)) {
    const answer = paletteServerValues(dialog, invocation, param.type, word, false);
    if (!answer) return 'pending';
    const enabled = answer.filter(value => !value.disabled);
    const exact = enabled.filter(value => value.exact);
    const pick = exact.length === 1 ? exact[0] : enabled.length === 1 ? enabled[0] : null;
    return pick ? paletteTaken(pick) : 'open';
  }
  return 'open';
}

function paletteTaken(value) {
  return { value: value.value, label: value.label, detail: value.detail, commentRequired: value.commentRequired,
    comment: value.comment };
}

/** Reads the complete words of the field into chips, as far as they resolve. */
function paletteResolve(dialog) {
  const invocation = paletteState.invocation;
  const input = document.getElementById('commandPaletteInput');
  let text = input.value;
  let changed = false;
  for (;;) {
    const param = paletteCurrentParam(invocation);
    if (!param || param.type === 'comment') break;
    const lead = text.replace(/^\s+/, '');
    if (param.type === 'day') {
      const phrase = paletteLastWorkdayPhrase(dialog, lead);
      if (phrase === 'partial') break;
      if (phrase) {
        const iso = paletteLastWorkday(dialog, invocation);
        if (!iso) break;
        invocation.values.day = { value: iso, label: paletteShortDay(iso, document.documentElement.lang || 'de') };
        text = phrase.rest;
        changed = true;
        continue;
      }
    }
    const word = /^(\S+)\s+/.exec(lead);
    if (!word) break;
    const outcome = paletteResolveWord(dialog, invocation, param, word[1]);
    if (outcome === 'pending' || outcome === 'open') break;
    changed = true;
    if (outcome === 'skip') {
      invocation.values[param.type] = { skipped: true };
      continue;
    }
    invocation.values[param.type] = outcome;
    text = lead.slice(word[0].length);
  }
  if (changed) input.value = text;
}

/**
 * The suggestions for a parameter and what was typed for it, as `{ value, label, detail, note,
 * disabled }` — or undefined while the server has not answered yet.
 */
function paletteParamValues(dialog, invocation, param, query, partial) {
  const lang = document.documentElement.lang || 'de';
  const today = paletteToday(dialog);
  const data = dialog.dataset;
  if (param.type === 'day') {
    const lastWorkday = paletteLastWorkday(dialog, invocation);
    const word = (label, iso) => ({ value: iso, label, note: paletteFormatDay(iso, lang), chip: paletteShortDay(iso, lang) });
    if (!query) {
      const yesterday = paletteShiftDays(today, -1);
      return [word(data.dayToday, today)]
        .concat(lastWorkday && lastWorkday !== yesterday ? [word(data.dayLastworkday, lastWorkday)] : [])
        .concat([word(data.dayYesterday, yesterday)]);
    }
    const values = paletteParseDay(query, today, paletteVocabulary(dialog))
      .map(day => ({ value: day.iso, label: paletteFormatDay(day.iso, lang), chip: paletteShortDay(day.iso, lang) }));
    if (lastWorkday && paletteLastWorkdayPhrase(dialog, query + ' ')) values.unshift(word(data.dayLastworkday, lastWorkday));
    return values;
  }
  if (param.type === 'month') {
    const month = (ym, note) => ({ value: ym, label: paletteFormatMonth(ym, lang), note: note || '' });
    if (query) return paletteParseMonth(query, today, paletteMonthVocabulary(dialog)).map(m => month(m.ym));
    const proposed = invocation.verb === 'release' || (invocation.verb === 'accept' && paletteValue(invocation, 'person'))
      ? paletteServerValues(dialog, invocation, 'month', '', false) : [];
    if (proposed === undefined) return undefined;
    const current = today.slice(0, 7);
    const values = proposed.map(value => month(value.value, value.note));
    [month(current), month(paletteShiftMonths(current, -1), data.monthLast)].forEach(value => {
      if (!values.some(known => known.value === value.value)) values.push(value);
    });
    return values;
  }
  if (param.type === 'duration') {
    if (!query) return [15, 30, 60].map(minutes => ({ value: paletteFormatDuration(minutes), label: paletteFormatDuration(minutes) }));
    const minutes = paletteParseDuration(query);
    return minutes ? [{ value: paletteFormatDuration(minutes), label: paletteFormatDuration(minutes) }] : [];
  }
  if (param.type === 'ticket') {
    const suborder = paletteValue(invocation, 'suborder');
    return suborder && !suborder.error ? paletteTicketValues(dialog, suborder.value, query, partial) : [];
  }
  if (param.type === 'comment') return [];
  return paletteServerValues(dialog, invocation, param.type, query, partial);
}

/**
 * Whether the chosen suborder can still be booked on the chosen day — asked again after the day
 * changed. Returns the reason it cannot, '' where it can, undefined while the server is asked.
 */
function paletteSuborderProblem(dialog, invocation) {
  const suborder = paletteValue(invocation, 'suborder');
  const answer = paletteServerValues(dialog, invocation, 'suborder', suborder.label, false);
  if (!answer) return undefined;
  const found = answer.find(value => value.value === suborder.value);
  if (found) return found.disabled ? found.note : '';
  // neither on that day nor today: the server names no reason, the day is reason enough
  const day = paletteValue(invocation, 'day');
  return dialog.dataset.suborderNotbookable.replace('{0}',
    paletteFormatDay(day ? day.value : paletteToday(dialog), document.documentElement.lang || 'de'));
}

function paletteInvoke(dialog, verb, rest) {
  const command = paletteState.commands.find(item => item.type === 'verb' && item.key === verb);
  paletteState.invocation = { verb, values: {}, fallback: command ? command.fallback : null,
    label: command ? command.label : verb };
  // room for the chips where the screen has it (salat.css)
  dialog.classList.add('command-palette-wide');
  clearTimeout(paletteState.objectTimer);
  paletteState.objectToken++;
  paletteState.objects = null;
  const input = document.getElementById('commandPaletteInput');
  input.dataset.placeholder = input.dataset.placeholder || input.placeholder;
  input.placeholder = '';
  input.value = rest;
  // asked right away, so that the last working day is there once the day is typed
  if (paletteParams(verb).some(param => param.type === 'day')) paletteLastWorkday(dialog, paletteState.invocation);
  paletteInvocationUpdate(dialog);
}

function paletteLeaveInvocation(dialog, text) {
  paletteState.invocation = null;
  dialog.classList.remove('command-palette-wide');
  const input = document.getElementById('commandPaletteInput');
  input.placeholder = input.dataset.placeholder || input.placeholder;
  input.value = text;
  document.getElementById('commandPaletteChips').hidden = true;
  document.getElementById('commandPaletteParam').hidden = true;
  document.getElementById('commandPalettePreview').hidden = true;
  paletteUpdate(dialog);
}

/** The command typed as a word of its own at the start of the field; then what follows is its input. */
function paletteDetectVerb(dialog, text) {
  const match = /^\s*(\S+)\s+([\s\S]*)$/.exec(text);
  if (!match) return false;
  const word = paletteFold(match[1]).folded;
  const command = paletteState.commands.find(item => item.type === 'verb' && paletteFold(item.label).folded === word);
  if (!command) return false;
  paletteInvoke(dialog, command.key, match[2]);
  return true;
}

function paletteInvocationUpdate(dialog) {
  const invocation = paletteState.invocation;
  const input = document.getElementById('commandPaletteInput');
  paletteResolve(dialog);

  const suborder = paletteValue(invocation, 'suborder');
  if (suborder) {
    const problem = paletteSuborderProblem(dialog, invocation);
    if (problem !== undefined) suborder.error = problem;
  }

  const text = input.value;
  let param = paletteCurrentParam(invocation);
  const lead = text.replace(/^\s+/, '');
  const firstWord = /^(\S+)(\s|$)/.exec(lead);
  const complete = /\s/.test(lead);
  let query = '';
  if (param && (param.type === 'comment' || (param.type === 'day' && paletteLastWorkdayPhrase(dialog, lead)))) {
    query = lead.trim();
  } else if (firstWord) {
    query = firstWord[1];
  }
  let values = param ? paletteParamValues(dialog, invocation, param, query, !complete) : [];
  // an optional parameter the input does not fit, or one with nothing to offer at all — the ticket
  // of a suborder without tickets —: it is left out, and the next one is offered
  if (param && param.optional && param.type !== 'comment' && values && !values.length) {
    const next = paletteNextParam(invocation, param);
    if (next) {
      param = next;
      values = paletteParamValues(dialog, invocation, param, next.type === 'comment' ? lead.trim() : query, !complete);
    }
  }
  paletteState.valueParam = param;
  paletteState.valueQuery = param && param.type === 'comment' ? '' : query;

  paletteRenderChips(dialog);
  const hint = document.getElementById('commandPaletteParam');
  hint.hidden = !param;
  hint.textContent = param ? paletteParamName(dialog, param.type) + '?' : '';

  const hits = (values || []).map(value => ({
    command: {
      type: 'value', key: value.value, label: value.detail ? value.label + ' · ' + value.detail : value.label,
      kind: value.note || '', disabled: !!value.disabled, value, run: () => paletteTake(dialog, value),
    },
    ranges: query && param && param.type !== 'comment' && paletteMatch(value.label, query)
      ? paletteMatch(value.label, query).ranges : [],
  }));
  // "Keine Treffer" only where something was looked for: a comment has no suggestions to miss
  paletteRender([{ hits }], values === undefined || !query || !param || param.type === 'comment');
  const firstEnabled = paletteState.items.findIndex(item => !item.disabled);
  paletteSetActive(firstEnabled, true);
  paletteRenderPreview(dialog);
}

function paletteRenderChips(dialog) {
  const invocation = paletteState.invocation;
  const chips = document.getElementById('commandPaletteChips');
  chips.replaceChildren();
  const chip = (label, type, error) => {
    const button = document.createElement('button');
    button.type = 'button';
    // only aimed at by a click: the keyboard stays in the field, where Backspace takes chips back
    button.tabIndex = -1;
    button.className = 'command-palette-chip badge' + (error ? ' bg-danger-lt' : ' bg-primary-lt');
    button.dataset.paletteChip = type;
    button.textContent = label;
    button.title = error || dialog.dataset.chipRemove;
    chips.append(button);
  };
  chip(invocation.label, '');
  paletteParams(invocation.verb).forEach(param => {
    const value = paletteValue(invocation, param.type);
    // a person by name and sign, as the palette lists them — two of the same name stay apart
    const label = value && (value.chip
      || (param.type === 'person' && value.detail ? value.label + ' · ' + value.detail : value.label));
    if (value) chip(label, param.type, value.error);
  });
  chips.hidden = false;
}

/** The title of what Enter opens, the values it opens with, and what is still wrong. */
function paletteRenderPreview(dialog) {
  const preview = document.getElementById('commandPalettePreview');
  const target = paletteTarget(dialog, paletteAsEnterTakesIt());
  preview.querySelector('[data-part="title"]').textContent = target.title;
  preview.querySelector('[data-part="values"]').textContent = target.parts.filter(Boolean).join(' · ');
  preview.querySelector('[data-part="warning"]').textContent = target.warnings.join(' · ');
  preview.hidden = false;
}

/**
 * The comment as it stands: the text of the field, once every parameter before it is read. With
 * the ticket still open that is its text as well — Enter makes a ticket of it only by its number.
 */
function paletteComment(invocation) {
  if (invocation.fieldTaken) return '';
  const param = paletteCurrentParam(invocation);
  return param && (param.type === 'comment' || param.type === 'ticket')
    ? document.getElementById('commandPaletteInput').value.trim() : '';
}

/**
 * Where Enter leads with the values there are, with the preview's title, values and warnings. A
 * required value that is missing and cannot be defaulted leads to the command's page instead.
 */
function paletteTarget(dialog, invocation) {
  const lang = document.documentElement.lang || 'de';
  const data = dialog.dataset;
  const today = paletteToday(dialog);
  const value = (type) => paletteValue(invocation, type);
  const title = data['preview' + paletteCapitalized(invocation.verb)];
  const page = () => ({
    href: invocation.fallback ? invocation.fallback.href : null,
    title: data.previewPage.replace('{0}', invocation.fallback ? invocation.fallback.label : invocation.label),
    parts: [], warnings: [],
  });
  const month = (type) => value(type) || null;
  const proposedMonth = () => {
    const answer = paletteState.valueCache.get(paletteSuggestUrl(dialog, invocation, 'month', ''));
    return Array.isArray(answer) && answer.length ? { value: answer[0].value } : null;
  };
  const monthLabel = (ym) => paletteFormatMonth(ym, lang);

  switch (invocation.verb) {
    case 'book': {
      const day = value('day') ? value('day').value : today;
      const suborder = value('suborder') && !value('suborder').error ? value('suborder') : null;
      const duration = value('duration');
      const ticket = suborder && value('ticket');
      // without a comment of its own, the ticket's number and title, as the form writes them
      const comment = paletteComment(invocation) || (ticket ? ticket.comment : '');
      const params = new URLSearchParams({ date: day });
      if (suborder) params.set('suborderId', suborder.value);
      if (duration) params.set('duration', duration.value);
      if (ticket) params.set('ticketReference', ticket.value);
      if (comment) params.set('comment', comment);
      params.set('focus', !duration ? 'duration' : !suborder ? 'suborder' : !comment ? 'comment' : 'save');
      const warnings = [];
      if (value('suborder') && value('suborder').error) warnings.push(value('suborder').error);
      if (suborder && suborder.commentRequired && !comment) warnings.push(data.previewCommentRequired);
      return {
        href: data.targetBook + '?' + params, title, warnings,
        parts: [paletteFormatDay(day, lang), suborder && (suborder.detail ? suborder.label + ' ' + suborder.detail : suborder.label),
          duration && duration.label, ticket && ticket.label, comment && '„' + comment + '“'],
      };
    }
    case 'day': {
      const day = value('day') ? value('day').value : today;
      return { href: data.dailyUrl + '?mode=daily&date=' + day, title, parts: [paletteFormatDay(day, lang)], warnings: [] };
    }
    case 'matrix': {
      const ym = month('month') ? month('month').value : today.slice(0, 7);
      const [year, monthNumber] = ym.split('-').map(Number);
      const params = new URLSearchParams({ fMonth: monthNumber, fYear: year });
      const person = value('person');
      if (person) params.set('fEmployeeContractId', person.value);
      return { href: data.targetMatrix + '?' + params, title, parts: [monthLabel(ym), person && person.label], warnings: [] };
    }
    case 'release': {
      const chosen = month('month') || proposedMonth();
      if (!chosen) return page();
      return { href: data.targetRelease + '?until=' + chosen.value, title, parts: [monthLabel(chosen.value)], warnings: [] };
    }
    case 'accept': {
      const person = value('person');
      const chosen = person && (month('month') || proposedMonth());
      if (!chosen) return page();
      return {
        href: data.targetAccept + '?' + new URLSearchParams({ contractId: person.value, until: chosen.value }),
        title, parts: [person.label, monthLabel(chosen.value)], warnings: [],
      };
    }
    case 'controlling': {
      const order = value('customerorder');
      if (!order) return page();
      return {
        href: data.targetControlling + '?' + new URLSearchParams({ fBudgetCustomerOrderId: order.value, evaluate: 'true' }),
        title, parts: [order.detail ? order.label + ' ' + order.detail : order.label], warnings: [],
      };
    }
    default:
      return page();
  }
}

/**
 * Takes a suggestion for the parameter it was offered for. Where that is not the current one, the
 * current, optional one is left out.
 */
function paletteTake(dialog, value) {
  const invocation = paletteState.invocation;
  const param = paletteState.valueParam;
  if (!param || value.disabled) return;
  const input = document.getElementById('commandPaletteInput');
  const current = paletteCurrentParam(invocation);
  if (current && current.type !== param.type) invocation.values[current.type] = { skipped: true };
  invocation.values[param.type] = Object.assign(paletteTaken(value), value.chip ? { chip: value.chip } : {});
  const lead = input.value.replace(/^\s+/, '');
  const query = paletteState.valueQuery;
  let rest = lead;
  if (query) {
    const phrase = param.type === 'day' && paletteLastWorkdayPhrase(dialog, lead);
    rest = phrase && phrase !== 'partial' ? phrase.rest
      : phrase === 'partial' ? '' : lead.slice(lead.indexOf(query) + query.length);
  }
  input.value = rest.replace(/^\s+/, '');
  paletteInvocationUpdate(dialog);
}

/** Backspace in the empty field, or a click on a chip: the value goes, and its parameter is next. */
function paletteTakeBack(dialog, type) {
  const invocation = paletteState.invocation;
  if (!type) {
    const set = paletteParams(invocation.verb).filter(param => paletteValue(invocation, param.type));
    if (!set.length) {
      paletteLeaveInvocation(dialog, invocation.label);
      return;
    }
    type = set[set.length - 1].type;
  }
  delete invocation.values[type];
  // parameters left out on the way are open again: the one taken back may be what they missed
  Object.keys(invocation.values).forEach(key => {
    if (invocation.values[key].skipped) delete invocation.values[key];
  });
  // a ticket belongs to its suborder's branch and goes with it
  if (type === 'suborder') delete invocation.values.ticket;
  paletteInvocationUpdate(dialog);
}

/**
 * The value Enter takes before it opens the target: the selected one for the parameter still open,
 * where something was typed for it or where the command needs it. Null where there is none.
 */
function paletteEnterTakes() {
  const param = paletteState.valueParam;
  const active = paletteState.items[paletteState.active];
  const typed = document.getElementById('commandPaletteInput').value.trim();
  if (!param || param.type === 'comment' || !active || active.type !== 'value' || active.disabled) return null;
  // a ticket only by its whole number: a ticket whose title merely contains the word is no reason
  // to turn the comment into a ticket
  if (param.type === 'ticket') return active.value.exact ? active.value : null;
  return typed || !param.optional ? active.value : null;
}

/** The command as Enter would open it — what the preview shows, so that it never promises otherwise. */
function paletteAsEnterTakesIt() {
  const invocation = paletteState.invocation;
  const taken = paletteEnterTakes();
  if (!taken) return invocation;
  const values = Object.assign({}, invocation.values);
  const current = paletteCurrentParam(invocation);
  if (current && current.type !== paletteState.valueParam.type) values[current.type] = { skipped: true };
  values[paletteState.valueParam.type] = paletteTaken(taken);
  // the field still holds the word the value is taken for; it is no comment
  return Object.assign({}, invocation, { values, fieldTaken: true });
}

/** Enter: the value paletteEnterTakes names is taken first, then the target opens. */
function paletteInvocationRun(dialog) {
  const invocation = paletteState.invocation;
  const taken = paletteEnterTakes();
  if (taken) paletteTake(dialog, taken);
  const target = paletteTarget(dialog, invocation);
  if (!target.href) return;
  document.getElementById('commandPalette').close();
  window.location.assign(target.href);
}

function paletteMoveActive(step) {
  const count = paletteState.items.length;
  if (!count) return;
  let index = paletteState.active;
  for (let tried = 0; tried < count; tried++) {
    index = (index + step + count) % count;
    if (!paletteState.items[index].disabled) {
      paletteSetActive(index, true);
      return;
    }
  }
}

/* ─── Opening and closing ─── */

/**
 * Where the focus goes back to. Inside a TomSelect field the focus sat on its search input, which
 * closes with the dropdown as soon as the palette takes the focus; the field's control stands for
 * it, and it is focused without dropping the dropdown open again.
 */
function paletteRestoreFocus(origin) {
  if (!origin || origin === document.body || !document.body.contains(origin)) return;
  const wrapper = origin.closest('.ts-wrapper');
  const select = wrapper && wrapper.previousElementSibling && wrapper.previousElementSibling.tomselect;
  if (!select) {
    origin.focus();
    return;
  }
  const openOnFocus = select.settings.openOnFocus;
  select.settings.openOnFocus = false;
  (select.focus_node || select.control).focus();
  select.settings.openOnFocus = openOnFocus;
}

function paletteWire(dialog) {
  if (paletteState.wiredDialog === dialog) return;
  paletteState.wiredDialog = dialog;
  const input = document.getElementById('commandPaletteInput');
  const list = document.getElementById('commandPaletteList');

  input.addEventListener('input', () => {
    if (paletteState.drill) paletteRenderTargets(input.value);
    else if (paletteState.invocation) paletteInvocationUpdate(dialog);
    else if (!paletteDetectVerb(dialog, input.value)) paletteUpdate(dialog);
  });
  input.addEventListener('keydown', (event) => {
    const count = paletteState.items.length;
    const active = paletteState.items[paletteState.active];
    if (paletteState.invocation && !event.isComposing) {
      // a command is being entered (#1158): the arrows pass over what cannot be chosen, Tab takes,
      // Backspace in the empty field gives the last chip back — once per press, like → and ←
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        paletteMoveActive(event.key === 'ArrowDown' ? 1 : -1);
      } else if (event.key === 'Enter') {
        event.preventDefault();
        paletteInvocationRun(dialog);
      } else if (event.key === 'Tab' && !event.shiftKey && !event.altKey && !event.ctrlKey && !event.metaKey) {
        event.preventDefault();
        if (active && active.type === 'value' && !active.disabled) paletteTake(dialog, active.value);
      } else if (event.key === 'Backspace' && input.value === '') {
        event.preventDefault();
        if (event.repeat && paletteState.heldKey === event.key) return;
        paletteState.heldKey = event.key;
        paletteTakeBack(dialog, null);
      }
      return;
    }
    if (event.key === 'Tab' && !event.shiftKey && active && active.type === 'verb') {
      event.preventDefault();
      active.run();
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (count) paletteSetActive((paletteState.active + (event.key === 'ArrowDown' ? 1 : -1) + count) % count, true);
    } else if (event.key === 'Enter' && !event.isComposing) {
      event.preventDefault();
      paletteRun(paletteState.active);
    } else if (event.isComposing) {
      // an input method is composing: → and ← move between its clauses, Backspace deletes in it
    } else if (event.repeat) {
      // a held key goes into the targets or out of them once; its repeats would go on in the other
      // level, deleting from the query that was just put back
      if (event.key === paletteState.heldKey) event.preventDefault();
    } else if (event.key === 'ArrowRight' && !paletteState.drill && active && active.type === 'object'
        && paletteCaretAt(input, input.value.length)) {
      // only with the cursor at the end: elsewhere → moves the cursor, as it always does
      event.preventDefault();
      paletteState.heldKey = event.key;
      paletteDrill(dialog, active);
    } else if (paletteState.drill && ((event.key === 'ArrowLeft' && paletteCaretAt(input, 0))
        || (event.key === 'Backspace' && input.value === ''))) {
      event.preventDefault();
      paletteState.heldKey = event.key;
      paletteUndrill(dialog);
    }
  });
  input.addEventListener('keyup', (event) => {
    if (event.key === paletteState.heldKey) paletteState.heldKey = null;
  });
  // the input keeps the focus while an entry is clicked or tapped — on a phone the on-screen
  // keyboard would otherwise close and move the entry away from under the finger
  list.addEventListener('mousedown', (event) => event.preventDefault());
  list.addEventListener('mousemove', (event) => {
    const option = event.target.closest('[role="option"]');
    // a value that cannot be chosen is not selected under the pointer either (#1158)
    if (option && Number(option.dataset.index) !== paletteState.active && !option.hasAttribute('aria-disabled')) {
      paletteSetActive(Number(option.dataset.index), false);
    }
  });
  list.addEventListener('click', (event) => {
    const option = event.target.closest('[role="option"]');
    if (!option) return;
    const item = paletteState.items[Number(option.dataset.index)];
    if (event.target.closest('[data-palette-more]') && item && item.type === 'object') {
      paletteDrill(dialog, item);
      return;
    }
    if (item && item.type === 'value') {
      paletteTake(dialog, item.value);
      return;
    }
    paletteRun(Number(option.dataset.index));
  });
  const chips = document.getElementById('commandPaletteChips');
  chips.addEventListener('mousedown', (event) => event.preventDefault());
  chips.addEventListener('click', (event) => {
    const chip = event.target.closest('[data-palette-chip]');
    if (!chip || !paletteState.invocation) return;
    // the command's own chip leaves the command, as Backspace does once nothing else is left
    if (chip.dataset.paletteChip) paletteTakeBack(dialog, chip.dataset.paletteChip);
    else paletteLeaveInvocation(dialog, paletteState.invocation.label);
    input.focus();
  });
  dialog.querySelector('[data-palette-back]').addEventListener('click', () => {
    if (paletteState.drill) paletteUndrill(dialog);
    input.focus();
  });
  // A click on the backdrop lands on the dialog element itself — and so does one that was pressed
  // inside and released outside, selecting the typed text, which is no request to close.
  dialog.addEventListener('pointerdown', (event) => {
    paletteState.pressedOnBackdrop = event.target === dialog;
  });
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog && paletteState.pressedOnBackdrop) dialog.close();
  });
  dialog.querySelector('[data-command-palette-close]').addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => {
    // an answer still on its way has nobody to show it to
    clearTimeout(paletteState.objectTimer);
    // Not openToken: the close event comes a task later, and a palette opened again at once would
    // lose its own answers. Opening counts it; a closed palette ignores an answer anyway.
    clearTimeout(paletteState.valueTimer);
    paletteState.objectToken++;
    const origin = paletteState.origin;
    paletteState.origin = null;
    paletteRestoreFocus(origin);
  });
}

/**
 * A palette that was open while htmx took its history snapshot comes back from that copy as an
 * open dialog — but not a modal one, without listeners, at the end of the page. It is a leftover of
 * the markup, not an open palette.
 */
function paletteDropStale(dialog) {
  if (dialog && dialog.open && !dialog.matches(':modal')) dialog.removeAttribute('open');
}

function paletteOpen() {
  const dialog = document.getElementById('commandPalette');
  paletteDropStale(dialog);
  if (!dialog || dialog.open) return;
  // a Bootstrap modal holds the focus inside itself and would take it straight back
  if (document.querySelector('.modal.show')) return;
  paletteWire(dialog);
  paletteState.origin = document.activeElement;
  // the commands first: on an equal hit "buchen" stands above "Buchungsliste"
  paletteState.commands = paletteVerbCommands(dialog).concat(paletteNavigationCommands(),
    paletteSettingsCommands(dialog), paletteLoginSwitchCommands(dialog));
  paletteState.drill = null;
  paletteState.objects = null;
  paletteState.invocation = null;
  paletteState.valueCache = new Map();
  paletteState.openToken++;
  dialog.classList.remove('command-palette-wide');
  document.getElementById('commandPaletteCrumb').hidden = true;
  document.getElementById('commandPaletteChips').hidden = true;
  document.getElementById('commandPaletteParam').hidden = true;
  document.getElementById('commandPalettePreview').hidden = true;
  const input = document.getElementById('commandPaletteInput');
  input.placeholder = input.dataset.placeholder || input.placeholder;
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
  const modifier = IS_MAC ? event.metaKey && !event.ctrlKey : event.ctrlKey && !event.metaKey;
  if (!modifier) return;
  event.preventDefault();
  event.stopPropagation();
  const dialog = document.getElementById('commandPalette');
  paletteDropStale(dialog);
  if (dialog && dialog.open) dialog.close();
  else paletteOpen();
}, true);

document.addEventListener('htmx:history:cache:after:restore', function () {
  paletteDropStale(document.getElementById('commandPalette'));
  dropStaleModals();
});

/**
 * A Bootstrap modal that was open while htmx took its history snapshot — the overview of the
 * shortcuts, say — comes back from that copy shown, with its backdrop and a body that does not
 * scroll, but without an instance that could close it. Everything the opening had set is undone.
 */
function dropStaleModals() {
  const stale = Array.from(document.querySelectorAll('.modal.show'))
    .filter(modal => !tabler.bootstrap.Modal.getInstance(modal));
  if (!stale.length) return;
  stale.forEach(modal => {
    modal.classList.remove('show');
    modal.style.display = 'none';
    modal.setAttribute('aria-hidden', 'true');
    modal.removeAttribute('aria-modal');
  });
  document.querySelectorAll('.modal-backdrop').forEach(backdrop => backdrop.remove());
  document.body.classList.remove('modal-open');
  document.body.style.removeProperty('overflow');
  document.body.style.removeProperty('padding-right');
}

// the distance to the server's day is taken while the page is fresh, not on the first open hours later
document.addEventListener('DOMContentLoaded', function () {
  const dialog = document.getElementById('commandPalette');
  if (dialog) paletteToday(dialog);
});

document.addEventListener('click', function (event) {
  if (event.target.closest('[data-command-palette-open]')) paletteOpen();
});

document.querySelectorAll('[data-command-palette-open]').forEach(function (trigger) {
  trigger.setAttribute('aria-keyshortcuts', IS_MAC ? 'Meta+K' : 'Control+K');
});

/* ─── Keyboard shortcuts (#1016) ─────────────────────────────────────────────
 *
 * Besides Ctrl+K for the palette, three shortcuts; the overview (fragments/shortcut-help.html,
 * included once by layout/base.html) lists them all, and a shortcut that is added belongs there:
 *
 *   ?            opens the overview
 *   i            follows the header's "new booking" button. Its target carries the page's day
 *                and contract and is built once, on the server (TimereportController.newBookingUrl);
 *                where the button is missing — on the booking form itself — i does nothing.
 *   Ctrl+Enter   (⌘Enter) submits the form through its button marked data-submit-shortcut, also
 *                from the comment field
 *
 *   data-platform-label   on a key label: data-label and data-label-mac name the key on the two
 *                         kinds of system (Ctrl or ⌘); shown only once it is set
 *
 * The single keys act only while the focus is in no input field — there they are text — and while
 * no dialog is open. None of the combinations is one the browser keeps for itself.
 * -------------------------------------------------------------------------- */

// the input types a single key would type into
const SHORTCUT_TEXT_INPUT_TYPES = ['text', 'search', 'email', 'number', 'password', 'tel', 'url',
  'date', 'datetime-local', 'month', 'time', 'week'];

function shortcutFocusInField(el) {
  if (!el || el === document.body) return false;
  if (el.isContentEditable || el.closest('.ts-wrapper')) return true;
  if (el.matches('textarea, select')) return true;
  return el.matches('input') && SHORTCUT_TEXT_INPUT_TYPES.includes(el.type);
}

function shortcutDialogOpen() {
  return !!document.querySelector('dialog[open], .modal.show');
}

function openShortcutHelp() {
  const modal = document.getElementById('shortcutHelp');
  if (!modal || shortcutDialogOpen()) return;
  // An open TomSelect dropdown puts the focus back onto its field when it closes — and it closes
  // as soon as the overview takes the focus, which then stays behind the overview, out of the
  // reach of Esc. Closed first, it moves the focus before the overview is there. (The palette does
  // not need this: a modal <dialog> makes the page behind it inert.)
  document.querySelectorAll('.ts-wrapper.dropdown-active').forEach(wrapper => {
    const select = wrapper.previousElementSibling && wrapper.previousElementSibling.tomselect;
    if (select) select.close();
  });
  // Opened from the palette, the palette's origin is where the focus belongs — what the closed
  // palette left behind may be the hidden search input of a TomSelect field.
  const trigger = paletteState.origin || document.activeElement;
  // the overview gives the focus back itself; the palette closing under it must not move it
  // there while the overview is on its way in
  paletteState.origin = null;
  // Bootstrap hands the focus back only to a toggle of data-bs-toggle; ? and the palette are none
  modal.addEventListener('hidden.bs.modal', () => paletteRestoreFocus(trigger), { once: true });
  tabler.bootstrap.Modal.getOrCreateInstance(modal).show();
}

/**
 * The form Ctrl+Enter saves: the one the focus is in, or — with the focus on the page or beside the
 * form, say on an entry of the recent comments — the page's only form that offers the shortcut.
 * A link and a submit button keep what the combination does on them: the link opens in a new tab,
 * the button does its own saving.
 */
function shortcutSubmitForm(focused) {
  const control = focused && focused.closest ? focused.closest('a[href], button, input[type="submit"]') : null;
  if (control && (control.matches('a[href]') || control.type === 'submit')) return null;
  const own = focused && focused.closest ? focused.closest('form') : null;
  if (own) return own.querySelector('[data-submit-shortcut]') ? own : null;
  const offered = document.querySelectorAll('[data-submit-shortcut]');
  return offered.length === 1 && !shortcutDialogOpen() ? offered[0].form : null;
}

function submitByShortcut(event) {
  const focused = document.activeElement;
  const form = shortcutSubmitForm(focused);
  const button = form && form.querySelector('[data-submit-shortcut]');
  if (!button) return;
  event.preventDefault();
  event.stopPropagation();
  // What is typed but not yet taken has to be in the form before it leaves. The ticket field keeps
  // its text until it loses the focus — and TomSelect drops the blur while its focus bookkeeping,
  // which runs a tick behind, still says unfocused; so the entry is created here, as leaving the
  // field would create it. The duration is put into its form on blur.
  const wrapper = focused.closest('.ts-wrapper');
  const select = wrapper && wrapper.previousElementSibling && wrapper.previousElementSibling.tomselect;
  if (select && select.settings.createOnBlur && select.inputValue()) select.createItem(null);
  if (focused && focused !== document.body) focused.blur();
  form.requestSubmit(button);
}

// Capture phase for the same reason as the palette's shortcut: TomSelect handles Enter in its own
// field and would take Ctrl+Enter as a choice of the highlighted entry.
document.addEventListener('keydown', function (event) {
  if (typeof event.key !== 'string' || event.isComposing) return;
  if (event.key === 'Enter' && !event.altKey && !event.shiftKey
      && (IS_MAC ? event.metaKey && !event.ctrlKey : event.ctrlKey && !event.metaKey)) {
    // a held key saves once: the focus can come back into the form while the first save is on
    // its way (TomSelect puts it back onto its control when its dropdown closes)
    if (event.repeat) {
      if (shortcutSubmitForm(document.activeElement)) event.preventDefault();
      return;
    }
    submitByShortcut(event);
    return;
  }
  if (event.repeat || shortcutFocusInField(document.activeElement) || shortcutDialogOpen()) return;
  // ? needs Shift on most layouts, and AltGr — Ctrl and Alt together — on some
  if (event.key === '?' && !event.metaKey && (!event.ctrlKey || event.altKey)) {
    event.preventDefault();
    openShortcutHelp();
  } else if (event.key === 'i' && !event.ctrlKey && !event.metaKey && !event.altKey) {
    const newBooking = document.getElementById('header-new-booking');
    if (!newBooking) return;
    event.preventDefault();
    newBooking.click();
  }
}, true);

document.addEventListener('click', function (event) {
  if (event.target.closest('[data-shortcut-help-open]')) openShortcutHelp();
});

// On DOMContentLoaded, because the overview stands behind this script in the page.
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-platform-label]').forEach(function (label) {
    label.textContent = IS_MAC ? label.dataset.labelMac : label.dataset.label;
    label.hidden = false;
  });
  document.querySelectorAll('[data-submit-shortcut]').forEach(function (button) {
    button.setAttribute('aria-keyshortcuts', IS_MAC ? 'Meta+Enter' : 'Control+Enter');
  });
});

/* Wie viele Ticket-Referenzen eine Buchung tragen darf (#1326): das Zahlenfeld gilt nur fuer
 * "hoechstens" und steht nur dann da. Ein leeres Feld beginnt bei 1, damit "hoechstens" nicht ohne
 * Zahl abgeschickt wird. */
document.addEventListener('change', function (event) {
  const select = event.target.closest && event.target.closest('select[data-ticket-limit-toggle]');
  if (!select) return;
  const field = document.querySelector(select.dataset.ticketLimitToggle);
  if (!field) return;
  const limited = select.value === 'LIMITED';
  field.classList.toggle('d-none', !limited);
  const input = field.querySelector('input');
  if (limited && input && !input.value) input.value = '1';
});

/* Ticket-Schluessel aus dem Kommentar, die beim Speichern als Referenz angeboten werden (#1326),
 * im Buchungsformular wie in der Inline-Bearbeitung der Tagesansicht. Angehakt werden darf, was der
 * Unterauftrag noch zulaesst (data-remaining); die uebrigen Kaestchen sperren sich, sobald das
 * erreicht ist. Der Knopf zum Uebernehmen nennt die Anzahl und bleibt ohne Haken gesperrt. */
function syncTicketSuggestions(box) {
  const remaining = box.dataset.remaining ? Number(box.dataset.remaining) : Infinity;
  const boxes = Array.from(box.querySelectorAll('input[type="checkbox"]'));
  const checked = boxes.filter(b => b.checked).length;
  boxes.forEach(b => {
    b.disabled = !b.checked && checked >= remaining;
    b.closest('label').title = b.disabled ? (box.dataset.limitReachedTitle || '') : '';
  });
  const adopt = box.querySelector('[data-adopt-button]');
  if (!adopt) return;
  adopt.disabled = checked === 0;
  adopt.textContent = checked === 0 ? adopt.dataset.labelNone
    : checked === 1 ? adopt.dataset.labelOne
    : adopt.dataset.labelMany.replace('{0}', checked);
}

document.addEventListener('change', function (event) {
  const box = event.target.closest && event.target.closest('[data-ticket-suggestions]');
  if (box) syncTicketSuggestions(box);
});
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-ticket-suggestions]').forEach(syncTicketSuggestions);
});
document.addEventListener('htmx:after:swap', function () {
  document.querySelectorAll('[data-ticket-suggestions]').forEach(syncTicketSuggestions);
});
