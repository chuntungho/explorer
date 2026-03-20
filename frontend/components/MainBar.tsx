'use client'

import { TabState, Bookmark } from '../types/browser'
import NavButtons from './NavButtons'
import AddressBar from './AddressBar'
import BookmarksMenu from './BookmarksMenu'
import SettingsMenu from './SettingsMenu'

interface MainBarProps {
  activeTab: TabState | undefined
  onNavigate: (url: string) => void
  onBack: () => void
  onForward: () => void
  onRefresh: () => void
  onAddTab: () => void
  onCloseTab: () => void
  onCloseOthers: () => void
  onCloseRight: () => void
  bookmarks: Bookmark[]
  openDropdown: string | null
  onToggleDropdown: (id: string) => void
}

export default function MainBar({
  activeTab,
  onNavigate,
  onBack,
  onForward,
  onRefresh,
  onAddTab,
  onCloseTab,
  onCloseOthers,
  onCloseRight,
  bookmarks,
  openDropdown,
  onToggleDropdown,
}: MainBarProps) {
  return (
    <div id="main-bar">
      <NavButtons
        canGoBack={activeTab?.canGoBack ?? false}
        canGoForward={activeTab?.canGoForward ?? false}
        onBack={onBack}
        onForward={onForward}
        onRefresh={onRefresh}
      />

      <AddressBar
        url={activeTab?.displayUrl ?? ''}
        onNavigate={onNavigate}
      />

      {/* Bookmarks toggle button */}
      <div
        className="button"
        role="button"
        aria-label="Bookmarks"
        title="Bookmarks"
        onClick={() => onToggleDropdown('bookmarks')}
      >
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z" />
        </svg>
      </div>

      <BookmarksMenu
        isOpen={openDropdown === 'bookmarks'}
        onClose={() => onToggleDropdown('bookmarks')}
        bookmarks={bookmarks}
        onNavigate={onNavigate}
      />

      {/* Settings toggle button */}
      <div
        className="button"
        role="button"
        aria-label="Settings"
        title="Settings"
        onClick={() => onToggleDropdown('settings')}
      >
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z" />
        </svg>
      </div>

      <SettingsMenu
        isOpen={openDropdown === 'settings'}
        onClose={() => onToggleDropdown('settings')}
        onAddTab={onAddTab}
        onCloseTab={onCloseTab}
        onCloseOthers={onCloseOthers}
        onCloseRight={onCloseRight}
      />
    </div>
  )
}
