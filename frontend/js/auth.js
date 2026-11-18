/* Session handling. The JWT lives in sessionStorage (cleared when the tab closes). */
(function (global) {
  'use strict';
  var cfg = global.FoodLoopConfig;
  var memory = null; // fallback when sessionStorage is blocked

  function readRaw() {
    try { return global.sessionStorage.getItem(cfg.SESSION_KEY); } catch (e) { return memory; }
  }
  function writeRaw(value) {
    memory = value;
    try {
      if (value === null) { global.sessionStorage.removeItem(cfg.SESSION_KEY); }
      else { global.sessionStorage.setItem(cfg.SESSION_KEY, value); }
    } catch (e) { /* keep the in-memory copy */ }
  }

  /** @returns {{token:string,user:{id:number,fullName:string,role:string},expiresAt:number}|null} */
  function getSession() {
    var raw = readRaw();
    if (!raw) { return null; }
    try {
      var s = JSON.parse(raw);
      if (!s || !s.token || !s.user || Date.now() >= s.expiresAt) { clearSession(); return null; }
      return s;
    } catch (e) { clearSession(); return null; }
  }
  function getToken() { var s = getSession(); return s ? s.token : null; }
  function getUser() { var s = getSession(); return s ? s.user : null; }
  function clearSession() { writeRaw(null); }

  function setSession(loginResponse) {
    writeRaw(JSON.stringify({
      token: loginResponse.accessToken,
      user: loginResponse.user,
      expiresAt: Date.now() + (loginResponse.expiresIn || 900) * 1000
    }));
  }

  function homeFor(role) { return cfg.pageUrl('pages/' + String(role).toLowerCase() + '/dashboard.html'); }

  function loginUrl(next, expired) {
    var q = new URLSearchParams();
    if (next) { q.set('next', next); }
    if (expired) { q.set('expired', '1'); }
    var s = q.toString();
    return cfg.pageUrl('pages/public/login.html') + (s ? '?' + s : '');
  }

  async function login(email, password) {
    var r = await global.FoodLoopApi.post('/api/auth/login', { email: email, password: password }, { auth: false });
    setSession(r);
    return r.user;
  }

  async function logout() {
    try {
      if (getSession()) { await global.FoodLoopApi.post('/api/auth/logout', undefined, { handle401: false }); }
    } catch (e) { /* even if the server call fails we still sign out locally */ }
    clearSession();
    global.location.href = loginUrl();
  }

  global.FoodLoopAuth = {
    getSession: getSession, getToken: getToken, getUser: getUser, setSession: setSession,
    clearSession: clearSession, homeFor: homeFor, loginUrl: loginUrl, login: login, logout: logout
  };
})(window);