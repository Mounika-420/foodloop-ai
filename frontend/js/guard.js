/* Role guard. Call in <head> of every protected page: FoodLoopGuard.require(['DONOR']). */
(function (global) {
  'use strict';

  /** Returns the user if allowed; otherwise redirects (to login, or to the user's own dashboard). */
  function require(roles) {
    var html = document.documentElement;
    html.style.visibility = 'hidden'; // no flash of protected content before the redirect
    var session = global.FoodLoopAuth.getSession();
    if (!session) {
      global.location.replace(global.FoodLoopAuth.loginUrl(global.location.href));
      return null;
    }
    if (roles && roles.indexOf(session.user.role) === -1) {
      global.location.replace(global.FoodLoopAuth.homeFor(session.user.role));
      return null;
    }
    html.style.visibility = '';
    return session.user;
  }

  /** For login/register pages: signed-in users go straight to their dashboard. */
  function redirectIfLoggedIn() {
    var session = global.FoodLoopAuth.getSession();
    if (session) {
      global.location.replace(global.FoodLoopAuth.homeFor(session.user.role));
      return true;
    }
    return false;
  }

  global.FoodLoopGuard = { require: require, redirectIfLoggedIn: redirectIfLoggedIn };
})(window);