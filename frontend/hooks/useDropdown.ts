import { useState, useCallback } from 'react'

export function useDropdown(): {
  openDropdown: string | null
  toggle: (id: string) => void
  closeAll: () => void
} {
  const [openDropdown, setOpenDropdown] = useState<string | null>(null)

  const toggle = useCallback((id: string) => {
    setOpenDropdown((current) => (current === id ? null : id))
  }, [])

  const closeAll = useCallback(() => {
    setOpenDropdown(null)
  }, [])

  return { openDropdown, toggle, closeAll }
}
