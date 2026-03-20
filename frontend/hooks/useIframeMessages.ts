import { useEffect, useRef } from 'react'
import { IframeMessage } from '../types/browser'

const LOAD_TIMEOUT_MS = 30_000

function useIframeMessages(
  iframeRefs: React.MutableRefObject<Record<number, HTMLIFrameElement | null>>,
  onMessage: (tabId: number, msg: IframeMessage) => void
): void {
  // Track per-tab load timeouts
  const timeoutsRef = useRef<Record<number, ReturnType<typeof setTimeout>>>({})

  useEffect(() => {
    function handleMessage(event: MessageEvent) {
      // Find the tab id by matching event.source against iframe content windows
      const refs = iframeRefs.current
      let tabId: number | null = null

      for (const key of Object.keys(refs)) {
        const id = Number(key)
        const iframe = refs[id]
        if (iframe && iframe.contentWindow === event.source) {
          tabId = id
          break
        }
      }

      // Silently ignore unknown sources
      if (tabId === null) return

      const msg = event.data as IframeMessage

      if (msg.action === 'unload') {
        // Page is starting to load — start 30s timeout
        if (timeoutsRef.current[tabId] != null) {
          clearTimeout(timeoutsRef.current[tabId])
        }
        const capturedTabId = tabId
        timeoutsRef.current[capturedTabId] = setTimeout(() => {
          delete timeoutsRef.current[capturedTabId]
          onMessage(capturedTabId, {
            action: 'load',
            url: '',
            title: 'Error loading page',
            icon: 'logo.svg',
          })
        }, LOAD_TIMEOUT_MS)
      } else if (msg.action === 'load') {
        // Page loaded — clear any pending timeout
        if (timeoutsRef.current[tabId] != null) {
          clearTimeout(timeoutsRef.current[tabId])
          delete timeoutsRef.current[tabId]
        }
      }

      onMessage(tabId, msg)
    }

    window.addEventListener('message', handleMessage)

    return () => {
      window.removeEventListener('message', handleMessage)
      // Clear all pending timeouts on unmount
      const timeouts = timeoutsRef.current
      for (const id of Object.keys(timeouts)) {
        clearTimeout(timeouts[Number(id)])
      }
      timeoutsRef.current = {}
    }
  }, [iframeRefs, onMessage])
}

export default useIframeMessages
