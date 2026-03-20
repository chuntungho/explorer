'use client'

import { TabState, Bookmark } from '../types/browser'
import TabBar from './TabBar'
import MainBar from './MainBar'

interface TopBarProps {
  tabs: TabState[]
  activeTabId: number
  onAddTab: () => void
  onActivateTab: (id: number) => void
  onCloseTab: (id: number) => void
  onCloseOthers: () => void
  onCloseRight: () => void
  onNavigate: (url: string) => void
  onBack: () => void
  onForward: () => void
  onRefresh: () => void
  bookmarks: Bookmark[]
  openDropdown: string | null
  onToggleDropdown: (id: string) => void
  onCloseDropdowns: () => void
}

export default function TopBar({
  tabs,
  activeTabId,
  onAddTab,
  onActivateTab,
  onCloseTab,
  onCloseOthers,
  onCloseRight,
  onNavigate,
  onBack,
  onForward,
  onRefresh,
  bookmarks,
  openDropdown,
  onToggleDropdown,
}: TopBarProps) {
  const activeTab = tabs.find((t) => t.id === activeTabId)

  return (
    <div id="top-bar">
      <TabBar
        tabs={tabs}
        activeTabId={activeTabId}
        onAddTab={onAddTab}
        onActivateTab={onActivateTab}
        onCloseTab={onCloseTab}
        openDropdown={openDropdown}
        onToggleDropdown={onToggleDropdown}
      />
      <MainBar
        activeTab={activeTab}
        onNavigate={onNavigate}
        onBack={onBack}
        onForward={onForward}
        onRefresh={onRefresh}
        onAddTab={onAddTab}
        onCloseTab={() => onCloseTab(activeTabId)}
        onCloseOthers={onCloseOthers}
        onCloseRight={onCloseRight}
        bookmarks={bookmarks}
        openDropdown={openDropdown}
        onToggleDropdown={onToggleDropdown}
      />
    </div>
  )
}
