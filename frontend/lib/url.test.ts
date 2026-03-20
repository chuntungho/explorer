import { buildProxyUrl } from './url'

// Helper to mock window.location.host
function withHost(host: string, fn: () => void) {
  const original = window.location
  Object.defineProperty(window, 'location', {
    value: { ...original, host },
    writable: true,
    configurable: true,
  })
  try {
    fn()
  } finally {
    Object.defineProperty(window, 'location', {
      value: original,
      writable: true,
      configurable: true,
    })
  }
}

describe('buildProxyUrl', () => {
  describe('same-host passthrough (Requirement 3.4)', () => {
    it('returns the URL unchanged when it contains window.location.host', () => {
      withHost('localhost:3000', () => {
        const url = 'http://localhost:3000/some/path'
        expect(buildProxyUrl(url)).toBe(url)
      })
    })

    it('returns a ?url= proxied URL unchanged when it already contains the host', () => {
      withHost('myapp.example.com', () => {
        const url = '?url=http%3A%2F%2Fmyapp.example.com%2Fpage'
        expect(buildProxyUrl(url)).toBe(url)
      })
    })
  })

  describe('protocol-less inputs (Requirement 3.5)', () => {
    it('prepends https:// and wraps with ?url= for a bare domain', () => {
      withHost('localhost:3000', () => {
        expect(buildProxyUrl('example.com')).toBe(
          '?url=' + encodeURIComponent('https://example.com')
        )
      })
    })

    it('prepends https:// and wraps for a domain with path', () => {
      withHost('localhost:3000', () => {
        expect(buildProxyUrl('example.com/path?q=1')).toBe(
          '?url=' + encodeURIComponent('https://example.com/path?q=1')
        )
      })
    })
  })

  describe('external URLs (Requirement 3.6)', () => {
    it('wraps an http:// URL with ?url=', () => {
      withHost('localhost:3000', () => {
        const url = 'http://external.com/page'
        expect(buildProxyUrl(url)).toBe('?url=' + encodeURIComponent(url))
      })
    })

    it('wraps an https:// URL with ?url=', () => {
      withHost('localhost:3000', () => {
        const url = 'https://external.com/page'
        expect(buildProxyUrl(url)).toBe('?url=' + encodeURIComponent(url))
      })
    })
  })

  describe('never returns empty string (Requirement 3.3)', () => {
    it('returns a non-empty string for a single character input', () => {
      withHost('localhost:3000', () => {
        expect(buildProxyUrl('a').length).toBeGreaterThan(0)
      })
    })

    it('returns a non-empty string for a full URL', () => {
      withHost('localhost:3000', () => {
        expect(buildProxyUrl('https://example.com').length).toBeGreaterThan(0)
      })
    })
  })
})
