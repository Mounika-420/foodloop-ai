/* Frontend configuration. Change API_BASE_URL if the backend runs somewhere else. */
(function (global) {
  'use strict';
  var root = new URL('../', document.currentScript.src).href; // .../frontend/
  global.FoodLoopConfig = Object.freeze({
    API_BASE_URL: (location.hostname === 'localhost' || location.hostname === '127.0.0.1') ? 'http://localhost:8080' : 'https://REPLACE-BACKEND-URL.onrender.com',
    SESSION_KEY: 'foodloop.session',
    THEME_KEY: 'foodloop.theme',
    /** Absolute URL of a page, given its path from the frontend root, e.g. 'pages/public/login.html'. */
    pageUrl: function (path) { return new URL(path, root).href; }
  });
})(window);