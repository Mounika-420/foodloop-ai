/* Light/dark theme. Loaded in <head> so the saved theme is applied before first paint. */
(function (global) {
  'use strict';
  var KEY = global.FoodLoopConfig.THEME_KEY;

  function stored() {
    try { return global.localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function preferred() {
    var s = stored();
    if (s === 'light' || s === 'dark') { return s; }
    return global.matchMedia && global.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  function apply(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    var buttons = document.querySelectorAll('[data-theme-toggle]');
    for (var i = 0; i < buttons.length; i++) {
      var dark = theme === 'dark';
      buttons[i].setAttribute('aria-pressed', String(dark));
      buttons[i].innerHTML = '<i class="bi ' + (dark ? 'bi-sun' : 'bi-moon-stars') + '" aria-hidden="true"></i>';
      buttons[i].setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
    }
  }
  function toggle() {
    var next = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
    try { global.localStorage.setItem(KEY, next); } catch (e) { /* storage blocked: theme just won't persist */ }
    apply(next);
  }
  /** Wire every [data-theme-toggle] button inside root (default: whole document). */
  function bind(root) {
    var buttons = (root || document).querySelectorAll('[data-theme-toggle]');
    for (var i = 0; i < buttons.length; i++) {
      if (!buttons[i].dataset.themeBound) {
        buttons[i].dataset.themeBound = '1';
        buttons[i].addEventListener('click', toggle);
      }
    }
    apply(document.documentElement.getAttribute('data-bs-theme') || preferred());
  }

  document.documentElement.setAttribute('data-bs-theme', preferred());
  document.addEventListener('DOMContentLoaded', function () { bind(); });
  global.FoodLoopTheme = { apply: apply, toggle: toggle, bind: bind };
})(window);