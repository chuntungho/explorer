export interface TabState {
  id: number
  url: string          // current proxied or blank URL
  displayUrl: string   // original remote URL shown in address bar
  title: string
  icon: string         // favicon URL or fallback 'logo.svg'
  loading: boolean
  canGoBack: boolean
  canGoForward: boolean
  viewportWidth?: number  // reported by interceptor for scaling
}

export interface Bookmark {
  label: string
  url: string
  external?: boolean  // opens in new browser tab
}

export type IframeMessage =
  | { action: 'load';   url: string; title: string; icon?: string; width?: number }
  | { action: 'unload' }
  | { action: 'open';   url: string }
  | { action: 'scroll'; scrollTop: number }

export type OutboundIframeMessage =
  | { action: 'back' }
  | { action: 'forward' }
