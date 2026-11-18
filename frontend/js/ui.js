/* Small UI helpers: toasts, alerts, busy buttons, confirm dialog, field errors, formatters. */
(function (global) {
  'use strict';

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function toast(message, type) {
    type = type || 'success';
    var box = document.getElementById('fl-toasts');
    if (!box) {
      box = document.createElement('div');
      box.id = 'fl-toasts';
      box.className = 'toast-container position-fixed bottom-0 end-0 p-3';
      box.setAttribute('aria-live', 'polite');
      document.body.appendChild(box);
    }
    var el = document.createElement('div');
    el.className = 'toast align-items-center text-bg-' + type + ' border-0';
    el.setAttribute('role', type === 'danger' ? 'alert' : 'status');
    el.innerHTML = '<div class="d-flex"><div class="toast-body"></div>' +
      '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button></div>';
    el.querySelector('.toast-body').textContent = message;
    box.appendChild(el);
    var remove = function () { el.remove(); };
    if (global.bootstrap) {
      el.addEventListener('hidden.bs.toast', remove);
      new global.bootstrap.Toast(el, { delay: 4500 }).show();
    } else {
      el.classList.add('show');
      setTimeout(remove, 4500);
    }
  }

  function showAlert(el, message, type) {
    el.className = 'alert alert-' + (type || 'danger');
    el.textContent = message;
    el.hidden = false;
  }
  function clearAlert(el) { el.hidden = true; el.textContent = ''; }

  function setBusy(button, busy, busyLabel) {
    if (busy) {
      button.dataset.idleHtml = button.innerHTML;
      button.disabled = true;
      button.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>' +
        escapeHtml(busyLabel || 'Please wait…');
    } else {
      button.disabled = false;
      if (button.dataset.idleHtml !== undefined) { button.innerHTML = button.dataset.idleHtml; }
    }
  }

  function clearFieldErrors(form) {
    form.querySelectorAll('.is-invalid').forEach(function (i) { i.classList.remove('is-invalid'); });
    form.querySelectorAll('.invalid-feedback[data-server]').forEach(function (n) { n.remove(); });
  }
  /** Marks inputs named in fieldErrors ({name: message}) as invalid. Returns how many were matched. */
  function showFieldErrors(form, fieldErrors) {
    clearFieldErrors(form);
    var matched = 0;
    Object.keys(fieldErrors || {}).forEach(function (name) {
      var input = form.elements[name];
      if (!input) { return; }
      matched++;
      input.classList.add('is-invalid');
      var note = document.createElement('div');
      note.className = 'invalid-feedback';
      note.dataset.server = '1';
      note.textContent = fieldErrors[name];
      input.insertAdjacentElement('afterend', note);
    });
    return matched;
  }

  /** @returns {Promise<boolean>} */
  function confirmDialog(opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      var id = 'fl-confirm-' + Date.now();
      var holder = document.createElement('div');
      holder.innerHTML =
        '<div class="modal fade" id="' + id + '" tabindex="-1" aria-labelledby="' + id + '-title" aria-hidden="true">' +
        '<div class="modal-dialog modal-dialog-centered"><div class="modal-content">' +
        '<div class="modal-header"><h2 class="modal-title fs-5" id="' + id + '-title"></h2>' +
        '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button></div>' +
        '<div class="modal-body"></div>' +
        '<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal"></button>' +
        '<button type="button" class="btn" data-confirm></button></div></div></div></div>';
      var el = holder.firstChild;
      el.querySelector('.modal-title').textContent = opts.title || 'Are you sure?';
      el.querySelector('.modal-body').textContent = opts.message || '';
      el.querySelector('.modal-footer .btn-outline-secondary').textContent = opts.cancelText || 'Cancel';
      var ok = el.querySelector('[data-confirm]');
      ok.textContent = opts.confirmText || 'Confirm';
      ok.classList.add('btn-' + (opts.variant || 'primary'));
      document.body.appendChild(el);
      var answered = false;
      var modal = new global.bootstrap.Modal(el);
      ok.addEventListener('click', function () { answered = true; modal.hide(); });
      el.addEventListener('hidden.bs.modal', function () { el.remove(); resolve(answered); });
      modal.show();
    });
  }

  function formatKg(value) {
    var n = Number(value);
    return isNaN(n) ? '–' : n.toLocaleString('en-IN', { maximumFractionDigits: 1 }) + ' kg';
  }
  function formatDateTime(iso) {
    if (!iso) { return '–'; }
    var d = new Date(iso);
    return isNaN(d.getTime()) ? '–' : d.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
  }

  global.FoodLoopUi = {
    escapeHtml: escapeHtml, toast: toast, showAlert: showAlert, clearAlert: clearAlert, setBusy: setBusy,
    showFieldErrors: showFieldErrors, clearFieldErrors: clearFieldErrors, confirm: confirmDialog,
    formatKg: formatKg, formatDateTime: formatDateTime
  };
})(window);