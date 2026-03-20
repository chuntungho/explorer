import { useState, useRef, useCallback } from 'react'
import { TabState } from '../types/browser'
import { buildProxyUrl } from '../lib/url'

function createBlankTab(id: number): TabState {
  return {
    id,
    url: '',
    displayUrl: '',
    title: 'New Tab',
    icon: 'logo.svg',
    loading: false,
    canGoBack: false,
    canGoForward: false,
  }
}

export function useTabs() {
  const counterRef = useRef(0)

  const nextId = () => {
    counterRef.current += 1
    return counterRef.current
  }

  const initialTab = createBlankTab(nextId())
  const [tabs, setTabs] = useState<TabState[]>([initialTab])
  const [activeTabId, setActiveTabId] = useState<number>(initialTab.id)

  const activeTab = tabs.find((t) => t.id === activeTabId)

  const addTab = useCallback((url?: string) => {
    const id = nextId()
    const tab: TabState = url
      ? {
          id,
          url: buildProxyUrl(url),
          displayUrl: url,
          title: 'New Tab',
          icon: 'logo.svg',
          loading: true,
          canGoBack: false,
          canGoForward: false,
        }
      : createBlankTab(id)

    setTabs((prev) => [...prev, tab])
    setActiveTabId(id)
  }, [])

  const closeTab = useCallback((id: number) => {
    setTabs((prev) => {
      const nextTabs = prev.filter((t) => t.id !== id)

      if (nextTabs.length === 0) {
        const newTab = createBlankTab(nextId())
        setActiveTabId(newTab.id)
        return [newTab]
      }

      setActiveTabId((currentActiveId) => {
        if (id === currentActiveId) {
          const idx = prev.findIndex((t) => t.id === id)
          if (idx < nextTabs.length) {
            return nextTabs[idx].id
          } else {
            return nextTabs[nextTabs.length - 1].id
          }
        }
        return currentActiveId
      })

      return nextTabs
    })
  }, [])

  const activateTab = useCallback((id: number) => {
    setActiveTabId(id)
  }, [])

  const navigate = useCallback((id: number, url: string) => {
    setTabs((prev) =>
      prev.map((t) =>
        t.id === id
          ? { ...t, url: buildProxyUrl(url), displayUrl: url, loading: true }
          : t
      )
    )
  }, [])

  const updateTab = useCallback((id: number, patch: Partial<TabState>) => {
    setTabs((prev) =>
      prev.map((t) => (t.id === id ? { ...t, ...patch } : t))
    )
  }, [])

  const closeOthers = useCallback(() => {
    setTabs((prev) => prev.filter((t) => t.id === activeTabId))
  }, [activeTabId])

  const closeRight = useCallback(() => {
    setTabs((prev) => {
      const idx = prev.findIndex((t) => t.id === activeTabId)
      if (idx === -1) return prev
      return prev.slice(0, idx + 1)
    })
  }, [activeTabId])

  return {
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
  }
}
