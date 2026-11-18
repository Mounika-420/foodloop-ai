/* fetch() wrapper: base URL, JSON, JWT header, uniform errors, 401 handling. */
(function (global) {
  'use strict';
  var cfg = global.FoodLoopConfig;

  function ApiError(status, message, fieldErrors, payload) {
    this.name = 'ApiError';
    this.status = status;
    this.message = message;
    this.fieldErrors = fieldErrors || {};
    this.payload = payload || null;
  }
  ApiError.prototype = Object.create(Error.prototype);
  ApiError.prototype.constructor = ApiError;

  function fallbackMessage(status) {
    if (status === 0) { return 'Cannot reach the server. Check that the backend is running.'; }
    if (status === 401) { return 'Your session has expired. Please log in again.'; }
    if (status === 403) { return 'You do not have permission to do that.'; }
    if (status === 404) { return 'Not found.'; }
    if (status === 429) { return 'Too many requests. Please wait a minute and try again.'; }
    if (status >= 500) { return 'Something went wrong on the server. Please try again.'; }
    return 'The request could not be completed.';
  }

  /**
   * @param {string} method
   * @param {string} path  e.g. '/api/auth/login'
   * @param {{body?:any, query?:Object, auth?:boolean, signal?:AbortSignal, handle401?:boolean}} [opts]
   */
  async function request(method, path, opts) {
    opts = opts || {};
    var auth = opts.auth !== false;
    var url = new URL(cfg.API_BASE_URL + path);
    if (opts.query) {
      Object.keys(opts.query).forEach(function (k) {
        var v = opts.query[k];
        if (v !== undefined && v !== null && v !== '') { url.searchParams.set(k, v); }
      });
    }
    var headers = { Accept: 'application/json' };
    if (opts.body !== undefined) { headers['Content-Type'] = 'application/json'; }
    if (auth) {
      var token = global.FoodLoopAuth.getToken();
      if (token) { headers.Authorization = 'Bearer ' + token; }
    }

    var res;
    try {
      res = await fetch(url.toString(), {
        method: method,
        headers: headers,
        body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
        signal: opts.signal
      });
    } catch (e) {
      if (e && e.name === 'AbortError') { throw e; }
      throw new ApiError(0, fallbackMessage(0));
    }

    var text = await res.text();
    var payload = null;
    if (text) { try { payload = JSON.parse(text); } catch (e) { payload = null; } }

    if (res.ok) { return payload; }

    if (res.status === 401 && auth && opts.handle401 !== false) {
      global.FoodLoopAuth.clearSession();
      global.location.href = global.FoodLoopAuth.loginUrl(null, true);
      throw new ApiError(401, fallbackMessage(401), {}, payload);
    }
    var message = (payload && payload.message) ? payload.message : fallbackMessage(res.status);
    throw new ApiError(res.status, message, payload && payload.fieldErrors, payload);
  }

  global.FoodLoopApi = {
    ApiError: ApiError,
    request: request,
    get: function (path, opts) { return request('GET', path, opts); },
    post: function (path, body, opts) { return request('POST', path, Object.assign({}, opts, { body: body })); },
    put: function (path, body, opts) { return request('PUT', path, Object.assign({}, opts, { body: body })); },
    patch: function (path, body, opts) { return request('PATCH', path, Object.assign({}, opts, { body: body })); },
    del: function (path, opts) { return request('DELETE', path, opts); }
  };
})(window);