# Implementation Plan: Next.js Browser Frontend

## Overview

Scaffold a Next.js 14 + React 18 + TypeScript app in `frontend/`, replacing the jQuery-based `browser.html`/`browser.js`/`browser.css` with declarative React components and hooks. The app communicates with the existing Spring Boot backend via proxied requests.

## Tasks

- [x] 1. Project scaffold and configuration
  - Create `frontend/` with `package.json`, `tsconfig.json`, `next.config.ts`, and `jest.config.ts`
  - Declare `next`, `react`, `react-dom` as dependencies; `typescript`, `fast-check`, `jest`, `@testing-library/react`, `@testing-library/jest-dom`, `jest-environment-jsdom` as devDependencies
  - Configure `next.config.ts` with URL rewrites: `/?url=*` and `/api/*` → Spring Boot backend
  - Create `frontend/app/layout.tsx` (root layout with global CSS import) and `frontend/app/globals.css` (ported from `browser.css`)
  - Copy `logo.svg`, `loading.svg`, `favicon.png`, `interceptor.js` into `frontend/public/`
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 2. Core types and pure utility functions
  - [x] 2.1 Define shared TypeScript types in `frontend/types/browser.ts`
    - Implement `TabState`, `Bookmark`, `IframeMessage`, `OutboundIframeMessage` interfaces
    - _Requirements: 10.3_

  - [x] 2.2 Implement `buildProxyUrl` in `frontend/lib/url.ts`
    - Pure function: passthrough for same-host URLs, prepend `https://` for protocol-less inputs, wrap external URLs with `?url=encodeURIComponent(url)`
    - _Requirements: 3.3, 3.4, 3.5, 3.6_

  - [ ]* 2.3 Write unit tests for `buildProxyUrl`
    - Test same-host passthrough, `https://` prepend, `?url=` wrapping, non-empty output guarantee
    - _Requirements: 13.2_

  - [ ]* 2.4 Write property test: `buildProxyUrl` never returns empty string (Property 8)
    - **Property 8: buildProxyUrl never returns empty string**
    - **Validates: Requirements 3.3**

  - [ ]* 2.5 Write property test: `buildProxyUrl` passthrough for same-host URLs (Property 9)
    - **Property 9: buildProxyUrl passthrough for same-host URLs**
    - **Validates: Requirements 3.4**

  - [ ]* 2.6 Write property test: `buildProxyUrl` prepends https:// for protocol-less inputs (Property 10)
    - **Property 10: buildProxyUrl prepends https:// for protocol-less inputs**
    - **Validates: Requirements 3.5, 12.2**

  - [ ]* 2.7 Write property test: `buildProxyUrl` wraps external URLs with ?url= (Property 11)
    - **Property 11: buildProxyUrl wraps external URLs with ?url=**
    - **Validates: Requirements 3.6**

- [x] 3. `useTabs` hook
  - [x] 3.1 Implement `useTabs` in `frontend/hooks/useTabs.ts`
    - Manage `tabs: TabState[]` and `activeTabId` with `useState`
    - Implement `addTab`, `closeTab`, `activateTab`, `navigate`, `updateTab`, `closeOthers`, `closeRight`
    - `closeTab` on last tab must auto-open a blank tab; `navigate` must set `loading: true` and `url: buildProxyUrl(url)` immediately
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 3.2, 9.3, 12.4_

  - [ ]* 3.2 Write unit tests for `useTabs`
    - Test min-1-tab invariant, `activeTabId` validity, `addTab` length increase, `closeTab` sibling selection, last-tab auto-open, `navigate` loading state
    - _Requirements: 13.1, 13.3_

  - [ ]* 3.3 Write property test: tab count invariant (Property 1)
    - **Property 1: tabs.length >= 1 after any sequence of addTab/closeTab/closeOthers/closeRight**
    - **Validates: Requirements 2.1, 2.5, 12.4**

  - [ ]* 3.4 Write property test: active tab id validity (Property 2)
    - **Property 2: activeTabId always refers to an id present in tabs**
    - **Validates: Requirements 2.2**

  - [ ]* 3.5 Write property test: addTab grows list and activates new tab (Property 3)
    - **Property 3: addTab increases tabs.length by 1 and sets activeTabId to new tab**
    - **Validates: Requirements 2.3**

  - [ ]* 3.6 Write property test: closeTab activates nearest sibling (Property 4)
    - **Property 4: closeTab(activeTabId) activates next sibling or falls back to previous**
    - **Validates: Requirements 2.4**

  - [ ]* 3.7 Write property test: closeOthers leaves only active tab (Property 5)
    - **Property 5: closeOthers() results in tabs.length === 1 with the active tab remaining**
    - **Validates: Requirements 2.7**

  - [ ]* 3.8 Write property test: closeRight removes only right-side tabs (Property 6)
    - **Property 6: closeRight() removes all tabs after active tab, leaves all before unchanged**
    - **Validates: Requirements 2.8**

  - [ ]* 3.9 Write property test: navigate sets loading and proxied URL (Property 7)
    - **Property 7: navigate(id, url) immediately sets loading=true and url=buildProxyUrl(url)**
    - **Validates: Requirements 3.2, 9.3**

- [x] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. `useDropdown` and `useIframeMessages` hooks
  - [x] 5.1 Implement `useDropdown` in `frontend/hooks/useDropdown.ts`
    - Manage `openDropdown: string | null`; implement `toggle(id)` and `closeAll()`
    - At most one dropdown open at a time; `toggle` on open id closes it
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [ ]* 5.2 Write unit tests for `useDropdown`
    - Test at-most-one-open invariant, toggle closes already-open, closeAll sets null
    - _Requirements: 13.4_

  - [ ]* 5.3 Write property test: at most one dropdown open at a time (Property 21)
    - **Property 21: openDropdown holds at most one id after any sequence of toggle calls**
    - **Validates: Requirements 8.1**

  - [ ]* 5.4 Write property test: toggle closes already-open dropdown (Property 22)
    - **Property 22: toggle(id) on currently open id sets openDropdown to null**
    - **Validates: Requirements 8.3**

  - [ ]* 5.5 Write property test: closeAll sets openDropdown to null (Property 23)
    - **Property 23: closeAll() always results in openDropdown === null**
    - **Validates: Requirements 8.4**

  - [x] 5.6 Implement `useIframeMessages` in `frontend/hooks/useIframeMessages.ts`
    - Register one `window.addEventListener('message', ...)` on mount, remove on unmount
    - Match `event.source` against `iframeRefs` to find tab id; silently ignore unknown sources
    - Implement 30-second timeout to clear loading state if no `'load'` message arrives
    - _Requirements: 6.1, 6.2, 6.3, 12.1, 12.3_

  - [ ]* 5.7 Write property test: postMessage routing matches source to correct tab (Property 14)
    - **Property 14: useIframeMessages identifies tab id by matching event.source against iframeRefs**
    - **Validates: Requirements 6.2**

  - [ ]* 5.8 Write property test: unknown postMessage sources are silently ignored (Property 15)
    - **Property 15: messages from unknown sources do not modify tab state and do not throw**
    - **Validates: Requirements 6.3, 12.3**

- [x] 6. `scaleIframe` utility and IframePane component
  - [x] 6.1 Implement `scaleIframe` in `frontend/lib/scale.ts`
    - Apply `transform: scale(scale, scale)` with `transform-origin: top left` when `containerWidth < reportedWidth`
    - Set `iframe.style.width = reportedWidth + 'px'` and `iframe.style.height = floor(containerHeight / scale) + 'px'`
    - Clear all inline transform/size styles when scaling is not needed or `reportedWidth` is undefined
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 6.2 Write property test: scaleIframe applies correct transform (Property 19)
    - **Property 19: scaleIframe applies scale transform with correct values when containerWidth < reportedWidth**
    - **Validates: Requirements 7.1, 7.2**

  - [ ]* 6.3 Write property test: scaleIframe clears styles when scaling not needed (Property 20)
    - **Property 20: scaleIframe clears all inline styles when containerWidth >= reportedWidth or reportedWidth is undefined**
    - **Validates: Requirements 7.3, 7.4**

  - [x] 6.4 Implement `IframePane` component in `frontend/components/IframePane.tsx`
    - Render `<iframe>` with `sandbox="allow-scripts allow-same-origin allow-downloads allow-forms"`
    - Apply `visibility: hidden` (not `display: none`) for inactive tabs
    - Call `scaleIframe` on `'load'` postMessage receipt (via `viewportWidth` prop change)
    - Position iframe absolutely: `position: absolute; top: 0; left: 0; width: 100%; height: 100%`
    - _Requirements: 5.1, 5.2, 5.3, 5.5, 6.7, 7.5, 11.3_

  - [ ]* 6.5 Write property test: inactive iframes use visibility:hidden (Property 13)
    - **Property 13: every IframePane whose tab id !== activeTabId has visibility:hidden, not display:none**
    - **Validates: Requirements 5.2**

- [x] 7. Leaf UI components
  - [x] 7.1 Implement `AddressBar` in `frontend/components/AddressBar.tsx`
    - Controlled input showing `displayUrl`; select-all on focus, navigate on Enter
    - Apply focus/blur visual styles
    - _Requirements: 3.1, 3.7, 3.8, 3.9_

  - [x] 7.2 Implement `NavButtons` in `frontend/components/NavButtons.tsx`
    - Back, Forward, Refresh buttons; disable back when `canGoBack === false`, disable forward when `canGoForward === false`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ]* 7.3 Write property test: nav buttons disabled state matches tab history flags (Property 12)
    - **Property 12: back button disabled when canGoBack=false; forward button disabled when canGoForward=false**
    - **Validates: Requirements 4.4, 4.5**

  - [x] 7.4 Implement `Tab` in `frontend/components/Tab.tsx`
    - Show loading spinner and hide favicon when `tab.loading === true`; show favicon and hide spinner when `false`
    - Render curved tab shape with `.round-left` / `.round-right` corner decorators
    - _Requirements: 9.1, 9.2, 11.4_

  - [ ]* 7.5 Write property test: loading spinner visibility matches tab.loading (Property 26)
    - **Property 26: Tab shows spinner and hides favicon when loading=true; shows favicon and hides spinner when loading=false**
    - **Validates: Requirements 9.1, 9.2**

- [x] 8. Dropdown menu components
  - [x] 8.1 Implement `BookmarksMenu` in `frontend/components/BookmarksMenu.tsx`
    - Render bookmark list; call `onNavigate(url)` and close on click
    - _Requirements: 8.7_

  - [x] 8.2 Implement `SettingsMenu` in `frontend/components/SettingsMenu.tsx`
    - Expose New Tab, Close Tab, Close Other Tabs, Close Tabs to the Right actions
    - _Requirements: 8.8_

  - [x] 8.3 Implement `OpenedTabsMenu` in `frontend/components/OpenedTabsMenu.tsx`
    - Render one entry per tab with favicon and title; activate tab on click
    - _Requirements: 8.9_

  - [ ]* 8.4 Write property test: OpenedTabsMenu renders one entry per open tab (Property 25)
    - **Property 25: OpenedTabsMenu renders exactly tabs.length entries**
    - **Validates: Requirements 8.9**

- [x] 9. Composite layout components
  - [x] 9.1 Implement `TabBar` in `frontend/components/TabBar.tsx`
    - Render tab strip with `Tab` components and `+` new-tab button; include opened-tabs dropdown toggle
    - _Requirements: 10.1_

  - [x] 9.2 Implement `MainBar` in `frontend/components/MainBar.tsx`
    - Compose `NavButtons`, `AddressBar`, `BookmarksMenu`, `SettingsMenu`
    - _Requirements: 10.1_

  - [x] 9.3 Implement `TopBar` in `frontend/components/TopBar.tsx`
    - Compose `TabBar` and `MainBar`
    - _Requirements: 10.1_

  - [x] 9.4 Implement `PageArea` in `frontend/components/PageArea.tsx`
    - Render all `IframePane` components (never unmount while tab exists) and `IframeMask` overlay
    - Show `IframeMask` when `iframeMaskVisible === true`; call `onMaskClick` on mask click
    - _Requirements: 5.4, 8.5, 8.6_

  - [ ]* 9.5 Write property test: IframeMask visible when dropdown is open (Property 24)
    - **Property 24: IframeMask is rendered and visible when openDropdown !== null**
    - **Validates: Requirements 8.5**

- [x] 10. BrowserApp root page and full wiring
  - [x] 10.1 Implement `BrowserApp` in `frontend/app/page.tsx`
    - Instantiate `useTabs`, `useDropdown`, `useIframeMessages`; wire `handleMessage` callback with `useCallback`
    - Pass all state and callbacks to `TopBar` and `PageArea`
    - Handle back/forward via `postMessage` to active iframe; handle Escape key to `closeAll()`
    - _Requirements: 4.1, 4.2, 8.6, 8.10, 10.5_

  - [ ]* 10.2 Write property test: load message updates tab fields correctly (Property 16)
    - **Property 16: load message calls updateTab with correct url, title, icon, loading:false, viewportWidth**
    - **Validates: Requirements 6.4, 9.4**

  - [ ]* 10.3 Write property test: unload message sets tab loading to true (Property 17)
    - **Property 17: unload message calls updateTab with { loading: true }**
    - **Validates: Requirements 6.5**

  - [ ]* 10.4 Write property test: open message triggers addTab with given URL (Property 18)
    - **Property 18: open message calls addTab(url) with the provided URL**
    - **Validates: Requirements 6.6**

- [x] 11. Integration tests
  - [ ]* 11.1 Write integration test: BrowserApp add-tab, type-URL, close-tab flow
    - Render `BrowserApp` in jsdom; simulate add tab, type URL + Enter, close tab; verify state transitions
    - _Requirements: 13.6_

  - [ ]* 11.2 Write integration test: interceptor postMessage updates tab state
    - Mock `window.postMessage`; simulate `load`, `unload`, `open` messages; verify title, icon, loading updates
    - _Requirements: 13.7_

- [x] 12. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Iframes are never unmounted — only hidden via `visibility: hidden` — to preserve page state
- Property tests use `fast-check` with a minimum of 100 iterations per property
- The Spring Boot backend is unchanged; `next.config.ts` rewrites handle proxy routing
