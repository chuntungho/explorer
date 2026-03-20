/**
 * Converts a user-entered URL into a ProxyUrl.
 *
 * Postconditions:
 * - If `url` already contains `window.location.host`, returns `url` unchanged
 * - If `url` does not contain `//`, prepends `https://` then proxies
 * - Otherwise returns `"?url=" + encodeURIComponent(url)`
 * - Never returns an empty string
 */
export function buildProxyUrl(url: string): string {
  const host =
    typeof window !== 'undefined' ? window.location.host : ''

  // Same-host URL: return unchanged
  if (host && url.includes(host)) {
    return url
  }

  // Protocol-less input: prepend https:// then proxy
  if (!url.includes('//')) {
    return '?url=' + encodeURIComponent('https://' + url)
  }

  // External URL: wrap with ?url=
  return '?url=' + encodeURIComponent(url)
}
