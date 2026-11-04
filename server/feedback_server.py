#!/usr/bin/env python3
"""SplitAudio one-tap report endpoint: the server side of the app's "It works!" / "Report a problem" buttons.

This is the whole server, published on purpose. It takes one small JSON POST, checks it, rate-limits it and
appends it to an inbox file. A separate maintainer-side script turns inbox rows into GitHub issues and
COMPATIBILITY.md rows. No accounts, no cookies, no secrets live in this process, and it only listens on
127.0.0.1 behind the project's HTTPS reverse proxy.

  POST /sa/report   Content-Type: application/json, body <= 16 KiB, a flat object of strings:
                    app="splitaudio", verdict=works|problem|crash, version, device (required);
                    flavor, rom, mode, diag, note, nick (optional). Unknown keys are rejected.
  GET  /healthz     "ok" (local watchdog only; the proxy does not forward it)
  anything else     404

Limits: 10 reports per hour and 30 per day per client, 500 per day in total; the same report sent again
within 24 h is answered ok but stored once. Only the first 12 hex digits of a salted hash of the client
address are kept (for those limits), never the address itself.

Run:       python3 -I feedback_server.py [--port 5011] [--data DIR]
Self-test: python3 -I feedback_server.py --selftest
"""
import argparse, collections, hashlib, json, os, secrets, sys, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 16 * 1024
FIELDS = {"app": 16, "verdict": 8, "version": 40, "flavor": 16, "device": 80, "rom": 120, "mode": 16,
          "diag": 8000, "note": 1000, "nick": 40}
REQUIRED = ("app", "verdict", "version", "device")
VERDICTS = ("works", "problem", "crash")
PER_HOUR, PER_DAY, GLOBAL_DAY, DUP_S = 10, 30, 500, 24 * 3600
HOUR, DAY = 3600, 24 * 3600


def clean(v, limit):
    """Strings only; drop control characters except newline and tab; trim; cut to the field's limit."""
    if not isinstance(v, str): return None
    s = "".join(ch for ch in v.replace("\r\n", "\n").replace("\r", "\n") if ch == "\n" or ch == "\t" or ord(ch) >= 32)
    return s.strip()[:limit]


def validate(obj):
    """Return (record, None) or (None, "reason"). Pure, so the self-test can hit it directly."""
    if not isinstance(obj, dict): return None, "object expected"
    unknown = [k for k in obj if k not in FIELDS]
    if unknown: return None, "unknown field"
    rec = {}
    for k, limit in FIELDS.items():
        if k in obj:
            s = clean(obj[k], limit)
            if s is None: return None, k + " must be a string"
            rec[k] = s
    for k in REQUIRED:
        if not rec.get(k): return None, k + " required"
    if rec["app"] != "splitaudio": return None, "wrong app"
    if rec["verdict"] not in VERDICTS: return None, "bad verdict"
    return rec, None


def fingerprint(rec):
    h = hashlib.sha256()
    for k in ("device", "version", "verdict", "mode", "diag", "note"): h.update((rec.get(k, "") + "\x1f").encode("utf-8"))
    return h.hexdigest()[:24]


class Limits:
    """Sliding windows per client hash plus a global daily cap; duplicate fingerprints within DUP_S."""
    def __init__(self):
        self.lock = threading.Lock(); self.per = collections.defaultdict(collections.deque)
        self.glob = collections.deque(); self.dups = {}

    def check(self, client, fp, now=None):
        now = time.time() if now is None else now
        with self.lock:
            q = self.per[client]
            while q and now - q[0] > DAY: q.popleft()
            while self.glob and now - self.glob[0] > DAY: self.glob.popleft()
            for k in [k for k, t in self.dups.items() if now - t > DUP_S]: del self.dups[k]
            if fp in self.dups: return "dup"
            if sum(1 for t in q if now - t <= HOUR) >= PER_HOUR or len(q) >= PER_DAY: return "client"
            if len(self.glob) >= GLOBAL_DAY: return "global"
            q.append(now); self.glob.append(now); self.dups[fp] = now
            return "ok"


class Store:
    def __init__(self, data_dir):
        self.dir = data_dir; os.makedirs(data_dir, mode=0o700, exist_ok=True); self.lock = threading.Lock()
        sp = os.path.join(data_dir, "salt")
        if not os.path.exists(sp):
            fd = os.open(sp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600); os.write(fd, secrets.token_hex(16).encode()); os.close(fd)
        self.salt = open(sp).read().strip()

    def client_hash(self, addr): return hashlib.sha256((self.salt + "|" + addr).encode()).hexdigest()[:12]

    def _append(self, name, line):
        p = os.path.join(self.dir, name)
        with self.lock:
            fd = os.open(p, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
            try: os.write(fd, (line + "\n").encode("utf-8"))
            finally: os.close(fd)

    def save(self, rec, client_hash):
        rid = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()) + "-" + secrets.token_hex(3)
        row = dict(rec); row.update({"id": rid, "received": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "client": client_hash})
        self._append("inbox.jsonl", json.dumps(row, ensure_ascii=False)); return rid

    def access(self, client_hash, method, path, status, size, ua):
        self._append("access.log", "%s %s %s %s %d %d %s" % (time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), client_hash, method, path, status, size, ua))


class Handler(BaseHTTPRequestHandler):
    server_version = "sa-feedback"; sys_version = ""; protocol_version = "HTTP/1.1"
    store: Store = None; limits: Limits = None

    def log_message(self, *a): pass

    def _client(self):
        xff = self.headers.get("X-Forwarded-For", "")
        addr = xff.split(",")[0].strip() if xff else (self.client_address[0] if self.client_address else "?")
        return self.store.client_hash(addr)

    def _send(self, status, obj):
        body = json.dumps(obj).encode()
        self.send_response(status); self.send_header("Content-Type", "application/json"); self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
        self.store.access(self._client(), self.command, self.path[:80], status, len(body), (self.headers.get("User-Agent") or "-")[:80])

    def do_GET(self):
        if self.path == "/healthz":
            self.send_response(200); self.send_header("Content-Type", "text/plain"); self.send_header("Content-Length", "2"); self.end_headers(); self.wfile.write(b"ok"); return
        self._send(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        if self.path != "/sa/report": return self._send(404, {"ok": False, "error": "not found"})
        if not (self.headers.get("Content-Type") or "").lower().startswith("application/json"): return self._send(415, {"ok": False, "error": "json only"})
        try: n = int(self.headers.get("Content-Length") or "0")
        except ValueError: n = -1
        if n <= 0 or n > MAX_BODY: return self._send(413 if n > MAX_BODY else 400, {"ok": False, "error": "bad length"})
        try: obj = json.loads(self.rfile.read(n).decode("utf-8"))
        except Exception: return self._send(400, {"ok": False, "error": "bad json"})
        rec, err = validate(obj)
        if err: return self._send(400, {"ok": False, "error": err})
        client = self._client(); verdict = self.limits.check(client, fingerprint(rec))
        if verdict == "dup": return self._send(200, {"ok": True, "dup": True})
        if verdict != "ok": return self._send(429, {"ok": False, "error": "too many reports, try later"})
        rid = self.store.save(rec, client)
        self._send(200, {"ok": True, "id": rid})

    def do_PUT(self): self._send(404, {"ok": False, "error": "not found"})
    do_DELETE = do_PATCH = do_HEAD = do_PUT


def serve(host, port, data_dir):
    Handler.store = Store(data_dir); Handler.limits = Limits()
    srv = ThreadingHTTPServer((host, port), Handler); srv.daemon_threads = True
    return srv


def selftest():
    import tempfile, urllib.request, urllib.error
    d = tempfile.mkdtemp(prefix="sa-fb-"); srv = serve("127.0.0.1", 0, d); port = srv.server_address[1]
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    base = "http://127.0.0.1:%d" % port

    def post(body, ctype="application/json", path="/sa/report", raw=None, xff=None):
        data = raw if raw is not None else json.dumps(body).encode()
        req = urllib.request.Request(base + path, data=data, method="POST", headers={"Content-Type": ctype, "User-Agent": "selftest"})
        if xff: req.add_header("X-Forwarded-For", xff)
        try:
            with urllib.request.urlopen(req, timeout=5) as r: return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b"{}")

    ok = {"app": "splitaudio", "verdict": "works", "version": "0.5.6 (fdroid)", "device": "Xiaomi 15", "rom": "HyperOS", "mode": "simple", "diag": "line1\nline2\x00bad", "nick": "tester"}
    assert validate(ok)[0]["diag"] == "line1\nline2bad", validate(ok)
    assert validate({**ok, "extra": "x"})[1] == "unknown field"
    assert validate({**ok, "verdict": "meh"})[1] == "bad verdict"
    assert validate({**ok, "app": "other"})[1] == "wrong app"
    assert validate({k: v for k, v in ok.items() if k != "device"})[1] == "device required"
    assert validate({**ok, "note": 5})[1] == "note must be a string"
    assert len(validate({**ok, "diag": "x" * 9000})[0]["diag"]) == 8000
    assert validate([])[1] == "object expected"
    s, j = post(ok); assert s == 200 and j["ok"] and "id" in j, (s, j)
    s, j = post(ok); assert s == 200 and j.get("dup") is True, (s, j)
    s, j = post({**ok, "verdict": "problem", "note": "no sound"}); assert s == 200 and "id" in j, (s, j)
    s, j = post(ok, ctype="text/plain"); assert s == 415, (s, j)
    s, j = post(None, raw=b"{bad"); assert s == 400 and j["error"] == "bad json", (s, j)
    s, j = post(None, raw=b"x" * (MAX_BODY + 1)); assert s == 413, (s, j)
    s, j = post({**ok, "extra": 1}); assert s == 400 and j["error"] == "unknown field", (s, j)
    s, j = post(ok, path="/sa/other"); assert s == 404, (s, j)
    with urllib.request.urlopen(base + "/healthz", timeout=5) as r: assert r.read() == b"ok"
    try: urllib.request.urlopen(base + "/sa/report", timeout=5); assert False
    except urllib.error.HTTPError as e: assert e.code == 404
    # rate limit: a second client gets 10 distinct reports an hour, the 11th is 429; the first client is unaffected
    for i in range(10):
        s, j = post({**ok, "device": "Phone %d" % i}, xff="203.0.113.9"); assert s == 200, (i, s, j)
    s, j = post({**ok, "device": "Phone 10"}, xff="203.0.113.9"); assert s == 429, (s, j)
    s, j = post({**ok, "device": "Phone 10"}, xff="203.0.113.10"); assert s == 200, (s, j)
    lim = Limits(); t0 = 1e9
    for i in range(PER_DAY): assert lim.check("c", "fp%d" % i, t0 + i * 400) == "ok"
    assert lim.check("c", "fpX", t0 + PER_DAY * 400) == "client"
    assert lim.check("c", "fpY", t0 + DAY + PER_DAY * 400 + 1) == "ok"
    rows = [json.loads(l) for l in open(os.path.join(d, "inbox.jsonl"))]
    assert len(rows) == 2 + 10 + 1 and rows[0]["device"] == "Xiaomi 15" and rows[0]["client"] != "203.0.113.9" and len(rows[0]["client"]) == 12
    assert oct(os.stat(os.path.join(d, "inbox.jsonl")).st_mode & 0o777) == "0o600"
    srv.shutdown(); print("SELFTEST PASS (%d rows, data %s)" % (len(rows), d))


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--host", default="127.0.0.1"); ap.add_argument("--port", type=int, default=5011)
    ap.add_argument("--data", default=os.environ.get("SA_FEEDBACK_DATA", "/home/ubuntu/oss/feedback")); ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.selftest: return selftest()
    srv = serve(a.host, a.port, a.data)
    print("sa-feedback listening on %s:%d data=%s" % (a.host, a.port, a.data), flush=True)
    try: srv.serve_forever()
    except KeyboardInterrupt: pass


if __name__ == "__main__":
    main()
