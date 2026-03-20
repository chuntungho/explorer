'use client'

interface SettingsMenuProps {
  isOpen: boolean
  onClose: () => void
  onAddTab: () => void
  onCloseTab: () => void
  onCloseOthers: () => void
  onCloseRight: () => void
}

export default function SettingsMenu({ isOpen, onClose, onAddTab, onCloseTab, onCloseOthers, onCloseRight }: SettingsMenuProps) {
  if (!isOpen) return null

  const items: { label: string; action: () => void }[] = [
    { label: 'New Tab', action: () => { onAddTab(); onClose() } },
    { label: 'Close Tab', action: () => { onCloseTab(); onClose() } },
    { label: 'Close Other Tabs', action: () => { onCloseOthers(); onClose() } },
    { label: 'Close Tabs to the Right', action: () => { onCloseRight(); onClose() } },
  ]

  return (
    <div id="setting">
      <div className="dropdown-menu">
        {items.map((item) => (
          <div
            key={item.label}
            className="dropdown-item"
            role="menuitem"
            onClick={item.action}
          >
            {item.label}
          </div>
        ))}
      </div>
    </div>
  )
}
