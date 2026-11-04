# server/ — the one-tap report endpoint

The app's **It works!** / **Report a problem** buttons send the diagnostics you can also copy from the
app (no accounts, contacts or Bluetooth names in it) to a tiny HTTPS endpoint run by the maintainer, so
that reporting needs no GitHub account. This directory is that endpoint, published in full.

- `feedback_server.py` — the whole server (Python standard library only). `POST /sa/report` takes one
  flat JSON object (`app`, `verdict`, `version`, `device` required; `flavor`, `rom`, `mode`, `diag`,
  `note`, `nick` optional; ≤ 16 KiB; unknown keys rejected), rate-limits it (10/hour and 30/day per
  client, 500/day in total, identical report within 24 h stored once) and appends it to an inbox file.
  Only a salted 12-hex hash of the client address is kept, never the address. `GET /healthz` is for the
  local watchdog; everything else is 404. `python3 -I feedback_server.py --selftest` exercises all of it.
- `sa-feedback.service` — the systemd unit it runs under: listens on `127.0.0.1:5011` only (the reverse
  proxy terminates HTTPS and caps the body at 32 KB), 128 MB memory cap, and a sandbox in which the
  process cannot read the home directory (replaced by an empty tmpfs, only its own data directory is
  bound back in), has no capabilities and a system-call allow-list. The process holds no secrets at all;
  turning inbox rows into GitHub issues / `COMPATIBILITY.md` rows is a separate maintainer-side script
  that runs outside this sandbox.
- `endpoint.json` — where the app finds the endpoint. The app fetches this file from GitHub at most once
  a day before sending (and falls back to the address built into the release), so the endpoint can move
  without an app update, and the GitHub report form remains available whenever sending fails.

Deploy (maintainer): `install -D -m 644 feedback_server.py /usr/local/lib/sa-feedback/feedback_server.py`,
`install -m 644 sa-feedback.service /etc/systemd/system/`, `systemctl enable --now sa-feedback`, then add
`handle /sa/* { request_body { max_size 32KB } reverse_proxy localhost:5011 }` to the HTTPS site.
