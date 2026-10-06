# Running your own SearXNG for Roadstr

Roadstr has no built-in search instance and no list of fallbacks (decision D-50).
Web results stay off until you type the address of an instance in the web results
settings. This page is for people who run their own, at home or on a small server.
It says what the app needs from an instance and gives a minimal setup that meets it.

## What Roadstr needs

**The address.**

- `https` works for any address.
- Plain `http` works for the device itself (`localhost`, `127.0.0.1`) and, once you tick
  "this is my own instance", for local networks: private IPv4 ranges (`10.0.0.0/8`,
  `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`), `100.64.0.0/10` (carrier-grade NAT,
  used by some VPNs), names ending in `.local`, `.lan`, `.home.arpa`, `.internal`, or a name
  without a dot.
- No `user:password@` in the address and no `?query` or `#fragment`: such an address is
  refused. A trailing `/search` is cut back to the base.
- **No redirects.** Roadstr does not follow them, so the address must answer directly. Do not
  put an `http` to `https` or a `www` redirect on it.

**The answers.** Roadstr asks for `GET /config` (the engine list) and
`GET /search?q=…&format=json&engines=…&language=…&safesearch=…&pageno=1`, with the headers
an ordinary client sends (`User-Agent`, `Accept` with `text/html` in it, `Accept-Language`).
JSON must be enabled, an answer must arrive within 8 seconds and fit in 1 MiB.

**Nothing in front that wants a browser.** A JavaScript check, a captcha or a "checking your
browser" page (Cloudflare, Anubis and the like) cannot be solved by the app and reads as
"answers, but not like SearXNG".

## A minimal setup with Docker

Create `config/settings.yml` before the first start:

```yaml
use_default_settings:
  engines:
    remove:
      - google
      - startpage

server:
  secret_key: "put a long random string here"   # for example: openssl rand -hex 32
  limiter: false
  public_instance: false
  image_proxy: false

search:
  formats:
    - html
    - json
  safe_search: 1
```

- `search.formats` must list `json`: SearXNG ships with `html` only.
- `secret_key` must be changed from the default.
- `limiter: false` is right for a private instance: the limiter needs a Valkey database and
  exists to turn automated clients away, which is what Roadstr is. If the instance is reachable
  from the internet, protect it at the network level (a VPN, an allow-list) and not with a bot
  check.

Then start it (the container listens on port 8080; here it is published on 8888):

```bash
docker run --name searxng -d \
    -p 8888:8080 \
    -v "./config/:/etc/searxng/" \
    -v "./data/:/var/cache/searxng/" \
    docker.io/searxng/searxng:latest
```

## Reaching it from the phone

- **Same network:** `http://192.168.x.y:8888`, with "this is my own instance" ticked.
- **Away from home:** put the phone and the server on a WireGuard tunnel and use the server's
  address inside the tunnel, for example `http://10.8.0.1:8888`, again with the box ticked.
  Roadstr does not contain a VPN: bring the tunnel up with the WireGuard app first.
- **A public name with a real certificate** (a reverse proxy such as Caddy or nginx) needs no
  box, but the search endpoint is then open to the internet, so restrict who can reach it.

## Check it before blaming the app

```bash
curl -s http://HOST:8888/config | head -c 200
curl -s -H 'Accept: application/json, text/html;q=0.9' -H 'Accept-Language: en' \
     'http://HOST:8888/search?q=test&format=json' | head -c 300
```

| What you see | Meaning |
|---|---|
| JSON with `"results"` | It works. |
| `403` | JSON is not enabled in `search.formats`. |
| `429` | A limiter or a proxy is rate-limiting or filtering the request. |
| HTML or `302` | A challenge page, a redirect, or the wrong address. |

Then use "test connection" in the web results settings: it says which of these it found.
The "strict sources" switch makes Roadstr refuse an instance whose engine list it cannot read.

## What each side sees

- **Your instance** sees the words you search, the name of your town for "near me" searches,
  and the address the phone connects from (the tunnel's address over a VPN). No coordinates, no
  cookies, no account.
- **The search engines** see the server's address, not the phone's.
- Roadstr only asks for general-purpose engines whose names do not contain `google` or
  `startpage`, and drops results that only those produced. That is a promise about its own
  requests: what your instance asks upstream is whatever its settings say, which is why the
  example removes them.
- A home address that sends many queries can be asked for a captcha by some engines. Roadstr
  sends one search per tap and remembers the answer for 10 minutes, and shows what the other
  engines returned when one of them fails.

## Keeping it healthy

SearXNG changes often and engines break. Update the image regularly, and watch the instance's
own logs rather than Roadstr when results thin out.
