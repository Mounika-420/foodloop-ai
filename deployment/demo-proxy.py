# Demo proxy: serves the frontend and forwards /api and /actuator to the backend on one origin.
import functools
import http.client
import sys
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler

BACKEND_HOST, BACKEND_PORT = '127.0.0.1', 8080
PROXY_PREFIXES = ('/api/', '/actuator/')
HOP = {'connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailers',
       'transfer-encoding', 'upgrade', 'host', 'origin', 'referer', 'content-length'}


class Handler(SimpleHTTPRequestHandler):
    def _proxy(self):
        length = int(self.headers.get('Content-Length') or 0)
        body = self.rfile.read(length) if length else None
        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP}
        headers['Host'] = '%s:%d' % (BACKEND_HOST, BACKEND_PORT)
        if body is not None:
            headers['Content-Length'] = str(len(body))
        conn = None
        try:
            conn = http.client.HTTPConnection(BACKEND_HOST, BACKEND_PORT, timeout=30)
            conn.request(self.command, self.path, body=body, headers=headers)
            resp = conn.getresponse()
            data = resp.read()
        except Exception as exc:
            msg = ('{"status":502,"message":"Backend not reachable: %s"}' % exc).encode()
            self.send_response(502)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(msg)))
            self.end_headers()
            self.wfile.write(msg)
            return
        finally:
            if conn is not None:
                conn.close()
        self.send_response(resp.status)
        for key, value in resp.getheaders():
            if key.lower() in HOP:
                continue
            self.send_header(key, value)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(data)

    def _is_api(self):
        return self.path.startswith(PROXY_PREFIXES)

    def do_GET(self):
        self._proxy() if self._is_api() else super().do_GET()

    def do_HEAD(self):
        self._proxy() if self._is_api() else super().do_HEAD()

    def _api_only(self):
        if self._is_api():
            self._proxy()
        else:
            self.send_error(405)

    do_POST = do_PUT = do_PATCH = do_DELETE = do_OPTIONS = _api_only


if __name__ == '__main__':
    port = int(sys.argv[1])
    directory = sys.argv[2]
    ThreadingHTTPServer(('127.0.0.1', port), functools.partial(Handler, directory=directory)).serve_forever()