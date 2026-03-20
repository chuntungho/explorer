'use client'

import { useRef, useCallback, useEffect } from 'react'
import { useTabs } from '../hooks/useTabs'
import { useDropdown } from '../hooks/useDropdown'
import useIframeMessages from '../hooks/useIframeMessages'
import TopBar from '../components/TopBar'
import PageArea from '../components/PageArea'
import { Bookmark, IframeMessage } from '../types/browser'

const DEFAULT_BOOKMARKS: Bookmark[] = [
  { label: 'Google', url: 'https://www.google.com' },
  { label: 'GitHub', url: 'https://github.com' },
  { label: 'MDN Web Docs', url: 'https://developer.mozilla.org' },
  { label: 'Stack Overflow', url: 'https://stackoverflow.com' },
  { label: 'Wikipedia', url: 'https://www.wikipedia.org' },
]

export default function BrowserApp() {
  const {
    tabs,
    activeTabId,
    activeTab,
    addTab,
    closeTab,
    activateTab,
    navigate,
    updateTab,
    closeOthers,
    closeRight,
  } = useTabs()

  const { openDropdown, toggle, closeAll } = useDropdown()
  const iframeRefs = useRef<Record<number, HTMLIFrameElement | null>>({})

  const handleMessage = useCallback(
    (tabId: number, msg: IframeMessage) => {
      if (msg.action === 'load') {
        updateTab(tabId, {
          url: msg.url,
          title: msg.title,
          icon: msg.icon ?? 'logo.svg',
          loading: false,
          viewportWidth: msg.width,
        })
      } else if (msg.action === 'unload') {
        updateTab(tabId, { loading: true })
      } else if (msg.action === 'open') {
        addTab(msg.url)
      }
    },
    [updateTab, addTab]
  )

  useIframeMessages(iframeRefs, handleMessage)

  // Escape key closes all dropdowns
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        closeAll()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [closeAll])

  function handleBack() {
    if (!activeTab) return
    const iframe = iframeRefs.current[activeTabId]
    iframe?.contentWindow?.postMessage({ action: 'back' }, '*')
  }

  function handleForward() {
    if (!activeTab) return
    const iframe = iframeRefs.current[activeTabId]
    iframe?.contentWindow?.postMessage({ action: 'forward' }, '*')
  }

  function handleRefresh() {
    if (activeTab) {
      navigate(activeTabId, activeTab.displayUrl)
    }
  }

  return (
    <div id="browser">
      <TopBar
        tabs={tabs}
        activeTabId={activeTabId}
        onAddTab={addTab}
        onActivateTab={activateTab}
        onCloseTab={closeTab}
        onCloseOthers={closeOthers}
        onCloseRight={closeRight}
        onNavigate={(url) => navigate(activeTabId, url)}
        onBack={handleBack}
        onForward={handleForward}
        onRefresh={handleRefresh}
        bookmarks={DEFAULT_BOOKMARKS}
        openDropdown={openDropdown}
        onToggleDropdown={toggle}
        onCloseDropdowns={closeAll}
      />
      <PageArea
        tabs={tabs}
        activeTabId={activeTabId}
        iframeMaskVisible={openDropdown !== null}
        onMaskClick={closeAll}
        iframeRefs={iframeRefs}
      />
    </div>
  )
}
