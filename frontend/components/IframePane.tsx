'use client'

import React, { useRef, useEffect, useCallback } from 'react'
import { TabState } from '../types/browser'
import { scaleIframe } from '../lib/scale'

interface IframePaneProps {
  tab: TabState
  isActive: boolean
  iframeRef: React.RefCallback<HTMLIFrameElement>
}

export default function IframePane({ tab, isActive, iframeRef }: IframePaneProps) {
  const localRef = useRef<HTMLIFrameElement | null>(null)
  const containerRef = useRef<HTMLDivElement | null>(null)

  const setIframeRef = useCallback(
    (el: HTMLIFrameElement | null) => {
      localRef.current = el
      iframeRef(el)
    },
    [iframeRef]
  )

  useEffect(() => {
    const iframe = localRef.current
    const container = containerRef.current
    if (!iframe || !container) return
    scaleIframe(iframe, container.clientWidth, tab.viewportWidth)
  }, [tab.viewportWidth])

  return (
    <div
      ref={containerRef}
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        visibility: isActive ? 'visible' : 'hidden',
      }}
    >
      <iframe
        ref={setIframeRef}
        src={tab.url}
        sandbox="allow-scripts allow-same-origin allow-downloads allow-forms"
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          border: 'none',
        }}
        title={tab.title || tab.url}
      />
    </div>
  )
}
