# Explorer

Explorer is a web proxy application that lets users browse the internet through a customizable proxy layer. It rewrites URLs so all traffic flows through the proxy, enabling features like ad/content blocking, script removal, and domain remapping.

Key capabilities:
- Proxies HTTP/HTTPS requests by encoding the target host into a subdomain (e.g. `www-google-com.localhost:2025`)
- Rewrites HTML content (links, forms, assets) to keep navigation within the proxy
- Supports configurable block rules to suppress requests or inject/remove HTML content
- Supports an optional upstream proxy (HTTP/SOCKS)
- Can act as a Docker registry mirror
- Deployable via Docker with nginx as a reverse proxy using wildcard SSL certificates

Two ports are exposed:
- **2024** – Ingress (entry point): accepts a `?url=` parameter and redirects to the proxy subdomain
- **2025** – Proxy: handles all proxied subdomain requests
