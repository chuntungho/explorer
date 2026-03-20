'use client'

import { useState, useEffect, useRef } from 'react'

interface AddressBarProps {
  url: string
  onNavigate: (url: string) => void
}

export default function AddressBar({ url, onNavigate }: AddressBarProps) {
  const [value, setValue] = useState(url)
  const [focused, setFocused] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  // Sync local state when the url prop changes (e.g. tab switch or navigation)
  useEffect(() => {
    setValue(url)
  }, [url])

  function handleFocus() {
    setFocused(true)
    inputRef.current?.select()
  }

  function handleBlur() {
    setFocused(false)
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      onNavigate(value)
      inputRef.current?.blur()
    }
  }

  return (
    <div id="address-bar" className={focused ? 'selected' : ''}>
      <div id="address">
        <input
          ref={inputRef}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onFocus={handleFocus}
          onBlur={handleBlur}
          onKeyDown={handleKeyDown}
        />
      </div>
    </div>
  )
}
