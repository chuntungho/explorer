'use client'

import React from 'react'
import { TabState } from '../types/browser'
import IframePane from './IframePane'

interface PageAreaProps {
  tabs: TabState[]
  activeTabId: number
  iframeMaskVisible: boolean
  onMaskClick: () => void
  iframeRefs: React.MutableRefObject<Record<number, HTMLIFrameElement | null>>
}

export default function PageArea({
  tabs,
  activeTabId,
  iframeMaskVisible,
  onMaskClick,
  iframeRefs,
}: PageAreaProps) {
  return (
    <div style={{ position: 'relative', flex: 1, overflow: 'hidden' }}>
      {tabs.map((tab) => (
        <IframePane
          key={tab.id}
          tab={tab}
          isActive={tab.id === activeTabId}
          iframeRef={(el) => {
            iframeRefs.current[tab.id] = el
          }}
        />
      ))}
      {iframeMaskVisible && (
        <div
          onClick={onMaskClick}
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            zIndex: 10,
          }}
        />
      )}
    </div>
  )
}
