'use client'

import { TabState } from '../types/browser'
import Tab from './Tab'
import OpenedTabsMenu from './OpenedTabsMenu'

interface TabBarProps {
  tabs: TabState[]
  activeTabId: number
  onAddTab: () => void
  onActivateTab: (id: number) => void
  onCloseTab: (id: number) => void
  openDropdown: string | null
  onToggleDropdown: (id: string) => void
}

export default function TabBar({
  tabs,
  activeTabId,
  onAddTab,
  onActivateTab,
  onCloseTab,
  openDropdown,
  onToggleDropdown,
}: TabBarProps) {
  return (
    <div id="tab-bar">
      <div id="tabs">
        {tabs.map((tab) => (
          <Tab
            key={tab.id}
            tab={tab}
            isActive={tab.id === activeTabId}
            onActivate={() => onActivateTab(tab.id)}
            onClose={(e) => {
              e.stopPropagation()
              onCloseTab(tab.id)
            }}
          />
        ))}

        {/* New tab button */}
        <div
          id="new-tab"
          role="button"
          aria-label="New tab"
          title="New tab"
          onClick={onAddTab}
        >
          <svg viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">
            <path d="M8 2v12M2 8h12" stroke="#595C62" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </div>
      </div>

      {/* Opened tabs dropdown */}
      <OpenedTabsMenu
        isOpen={openDropdown === 'opened-tabs'}
        onClose={() => onToggleDropdown('opened-tabs')}
        tabs={tabs}
        activeTabId={activeTabId}
        onActivate={onActivateTab}
      />

      {/* Toggle opened-tabs dropdown button */}
      <div
        className="button"
        role="button"
        aria-label="Show open tabs"
        title="Show open tabs"
        onClick={() => onToggleDropdown('opened-tabs')}
      >
        <svg className="icon" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">
          <path d="M4 6l4 4 4-4" stroke="#595C62" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </svg>
      </div>
    </div>
  )
}
