'use client'

import Image from 'next/image'
import { TabState } from '../types/browser'

interface TabProps {
  tab: TabState
  isActive: boolean
  onActivate: () => void
  onClose: (e: React.MouseEvent) => void
}

export default function Tab({ tab, isActive, onActivate, onClose }: TabProps) {
  return (
    <div className="tab-frame">
      <div className={`tab-container${isActive ? ' active' : ''}`} onClick={onActivate}>
        {/* Curved corner decorators */}
        <div className="round round-left" />
        <div className="round round-right" />

        <div className={`tab${isActive ? ' active' : ''}`}>
          {/* Favicon / loading spinner */}
          <div style={{ position: 'relative', width: 16, height: 16, flexShrink: 0 }}>
            {tab.loading ? (
              <Image
                src="/loading.svg"
                alt="Loading"
                width={16}
                height={16}
                className="icon"
              />
            ) : (
              <Image
                src={tab.icon || '/favicon.png'}
                alt=""
                width={16}
                height={16}
                className="icon"
                onError={(e) => {
                  (e.currentTarget as HTMLImageElement).src = '/favicon.png'
                }}
              />
            )}
          </div>

          {/* Tab title */}
          <div className="title" title={tab.title || tab.displayUrl}>
            {tab.title || tab.displayUrl || 'New Tab'}
          </div>

          {/* Close button */}
          <div
            className="close"
            onClick={onClose}
            role="button"
            aria-label="Close tab"
            title="Close tab"
          >
            <svg width="8" height="8" viewBox="0 0 8 8" xmlns="http://www.w3.org/2000/svg">
              <path d="M1 1l6 6M7 1l-6 6" stroke="#5F6368" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </div>
        </div>
      </div>
    </div>
  )
}
