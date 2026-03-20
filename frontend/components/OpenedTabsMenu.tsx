'use client'

import { TabState } from '../types/browser'

interface OpenedTabsMenuProps {
  isOpen: boolean
  onClose: () => void
  tabs: TabState[]
  activeTabId: number
  onActivate: (id: number) => void
}

export default function OpenedTabsMenu({ isOpen, onClose, tabs, activeTabId, onActivate }: OpenedTabsMenuProps) {
  if (!isOpen) return null

  return (
    <div id="opened-tabs">
      <div className="dropdown-menu">
        {tabs.map((tab) => (
          <div
            key={tab.id}
            className={`dropdown-item${tab.id === activeTabId ? ' active' : ''}`}
            role="menuitem"
            onClick={() => {
              onActivate(tab.id)
              onClose()
            }}
          >
            <div className="title">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={tab.icon} alt="" width={16} height={16} />
              <div>{tab.title}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
