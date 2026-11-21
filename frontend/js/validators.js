/* Client-side checks that mirror the backend rules (the server still has the final say). */
(function (global) {
  'use strict';

  /** Same rules as the backend PasswordPolicy: 8-72 chars, upper, lower, digit, special, no spaces. */
  function passwordChecks(p) {
    p = p || '';
    return [
      { id: 'length', label: '8 to 72 characters', ok: p.length >= 8 && p.length <= 72 },
      { id: 'upper', label: 'An uppercase letter', ok: /[A-Z]/.test(p) },
      { id: 'lower', label: 'A lowercase letter', ok: /[a-z]/.test(p) },
      { id: 'digit', label: 'A digit', ok: /[0-9]/.test(p) },
      { id: 'special', label: 'A special character', ok: /[^A-Za-z0-9\s]/.test(p) },
      { id: 'space', label: 'No spaces', ok: !/\s/.test(p) }
    ];
  }
  function isStrongPassword(p) {
    return passwordChecks(p).every(function (c) { return c.ok; });
  }
  /** Renders the live checklist into a <ul>. */
  function renderPasswordChecklist(listEl, password) {
    listEl.innerHTML = passwordChecks(password).map(function (c) {
      return '<li class="' + (c.ok ? 'text-success' : 'text-secondary') + '">' +
        '<i class="bi ' + (c.ok ? 'bi-check-circle-fill' : 'bi-circle') + ' me-1" aria-hidden="true"></i>' +
        global.FoodLoopUi.escapeHtml(c.label) +
        '<span class="visually-hidden">' + (c.ok ? ' (met)' : ' (not met)') + '</span></li>';
    }).join('');
  }

  global.FoodLoopValidators = {
    passwordChecks: passwordChecks,
    isStrongPassword: isStrongPassword,
    renderPasswordChecklist: renderPasswordChecklist,
    isEmail: function (v) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v || ''); },
    isPhone: function (v) { return /^[0-9+]{10,15}$/.test(v || ''); },
    isPincode: function (v) { return /^[0-9]{6}$/.test(v || ''); },
    inRange: function (v, min, max) { var n = Number(v); return v !== '' && !isNaN(n) && n >= min && n <= max; }
  };
})(window);