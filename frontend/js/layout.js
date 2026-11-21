/* Builds the dashboard shell (top navbar + role sidebar) around <main id="content">. */
(function (global) {
  'use strict';
  var esc = function (s) { return global.FoodLoopUi.escapeHtml(s); };

  // [file, label, icon]. Notifications are added once the backend has that feature.
  var NAV = {
    DONOR: [
      ['dashboard', 'Dashboard', 'bi-speedometer2'], ['add-donation', 'Add Donation', 'bi-plus-circle'],
      ['my-donations', 'My Donations', 'bi-box-seam'], ['profile', 'Profile', 'bi-person']
    ],
    NGO: [
      ['dashboard', 'Dashboard', 'bi-speedometer2'], ['available-donations', 'Available', 'bi-basket'],
      ['recommended', 'Recommended', 'bi-stars'], ['categories', 'Categories', 'bi-tags'],
      ['pickups', 'Pickups', 'bi-truck'], ['history', 'History', 'bi-clock-history'], ['profile', 'Profile', 'bi-person']
    ],
    VOLUNTEER: [
      ['dashboard', 'Dashboard', 'bi-speedometer2'], ['tasks', 'Tasks', 'bi-geo-alt'],
      ['history', 'History', 'bi-clock-history'], ['profile', 'Profile', 'bi-person']
    ],
    ADMIN: [
      ['dashboard', 'Dashboard', 'bi-speedometer2'], ['users', 'Users', 'bi-people'],
      ['verification', 'Verification', 'bi-patch-check'], ['analytics', 'Analytics', 'bi-graph-up'], ['profile', 'Profile', 'bi-person']
    ]
  };
  var ROLE_LABEL = { DONOR: 'Donor', NGO: 'NGO', VOLUNTEER: 'Volunteer', ADMIN: 'Admin' };

  /** @param {{active:string}} opts  active = file name without .html, e.g. 'dashboard' */
  function mount(opts) {
    opts = opts || {};
    var user = global.FoodLoopAuth.getUser();
    var main = document.getElementById('content');
    if (!user || !main) { return; }
    var role = user.role;
    var base = 'pages/' + role.toLowerCase() + '/';
    var links = (NAV[role] || []).map(function (n) {
      var current = n[0] === opts.active ? ' aria-current="page"' : '';
      return '<li><a class="fl-nav-link" href="' + esc(global.FoodLoopConfig.pageUrl(base + n[0] + '.html')) + '"' + current + '>' +
        '<i class="bi ' + n[2] + '" aria-hidden="true"></i><span>' + esc(n[1]) + '</span></a></li>';
    }).join('');

    var navbar = document.createElement('div');
    navbar.innerHTML =
      '<a class="visually-hidden-focusable position-absolute p-2 bg-body" href="#content">Skip to content</a>' +
      '<nav class="navbar fl-navbar sticky-top px-3" aria-label="Top bar"><div class="container-fluid p-0">' +
      '<button class="btn btn-outline-secondary btn-sm d-lg-none me-2" type="button" data-bs-toggle="offcanvas" ' +
      'data-bs-target="#fl-sidebar" aria-controls="fl-sidebar" aria-label="Open menu"><i class="bi bi-list" aria-hidden="true"></i></button>' +
      '<a class="navbar-brand fl-brand mb-0" href="' + esc(global.FoodLoopAuth.homeFor(role)) + '">' +
      '<i class="bi bi-recycle me-1" aria-hidden="true"></i>FoodLoop AI</a>' +
      '<div class="ms-auto d-flex align-items-center gap-2">' +
      '<button class="btn btn-outline-secondary btn-sm" type="button" data-theme-toggle></button>' +
      '<div class="dropdown"><button class="btn btn-sm btn-outline-primary dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">' +
      '<i class="bi bi-person-circle me-1" aria-hidden="true"></i><span class="d-none d-sm-inline">' + esc(user.fullName) + '</span></button>' +
      '<ul class="dropdown-menu dropdown-menu-end"><li><h6 class="dropdown-header">' + esc(user.fullName) + ' · ' + esc(ROLE_LABEL[role] || role) + '</h6></li>' +
      '<li><button class="dropdown-item" type="button" data-logout><i class="bi bi-box-arrow-right me-2" aria-hidden="true"></i>Log out</button></li></ul></div>' +
      '</div></div></nav>';

    var col = document.createElement('aside');
    col.className = 'fl-sidebar-col';
    col.innerHTML =
      '<div class="offcanvas-lg offcanvas-start" tabindex="-1" id="fl-sidebar" aria-labelledby="fl-sidebar-title">' +
      '<div class="offcanvas-header"><h2 class="offcanvas-title fs-6" id="fl-sidebar-title">' + esc(ROLE_LABEL[role] || role) + ' menu</h2>' +
      '<button type="button" class="btn-close" data-bs-dismiss="offcanvas" data-bs-target="#fl-sidebar" aria-label="Close"></button></div>' +
      '<div class="offcanvas-body p-3"><nav aria-label="' + esc(ROLE_LABEL[role] || role) + ' navigation"><ul class="list-unstyled mb-0 d-grid gap-1 w-100">' + links + '</ul></nav></div></div>';

    var shell = document.createElement('div');
    shell.className = 'fl-shell';
    main.parentNode.insertBefore(shell, main);
    shell.appendChild(col);
    shell.appendChild(main);
    while (navbar.lastChild) { document.body.insertBefore(navbar.lastChild, document.body.firstChild); }

    document.querySelector('[data-logout]').addEventListener('click', function () { global.FoodLoopAuth.logout(); });
    global.FoodLoopTheme.bind(document);
  }

  global.FoodLoopLayout = { mount: mount };
})(window);