'use client'

import { Bookmark } from '../types/browser'

interface BookmarksMenuProps {
  isOpen: boolean
  onClose: () => void
  bookmarks: Bookmark[]
  onNavigate: (url: string) => void
}

export default function BookmarksMenu({ isOpen, onClose, bookmarks, onNavigate }: BookmarksMenuProps) {
  if (!isOpen) return null

  return (
    <div id="bookmarks">
      <div className="dropdown-menu">
        {bookmarks.map((bookmark, index) => (
          <div
            key={index}
            className="dropdown-item"
            role="menuitem"
            onClick={() => {
              onNavigate(bookmark.url)
              onClose()
            }}
          >
            {bookmark.label}
          </div>
        ))}
      </div>
    </div>
  )
}
