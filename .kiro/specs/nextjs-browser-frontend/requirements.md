# Requirements Document

## Introduction

This document defines the requirements for refactoring the existing jQuery-based browser UI (`browser.html` / `browser.js` / `browser.css`) into a standalone Next.js + React application located in the `frontend/` folder at the project root. The new application preserves all existing functionality — tab management, address bar navigation, bookmarks, settings menu, iframe rendering with scaling, postMessage communication, and loading indicators — while replacing imperative DOM manipulation with declarative React state and component composition.

## Glossary

- **BrowserApp**: The root Next.js page component that owns global state and orchestrates all child components.
- **Tab**: A single browsable unit with its own iframe, URL, title, favicon, and navigation history state.
- **TabState**: The data model representing a single tab's current state (id, url, displayUrl, title, icon, loading, canGoBack, canGoForward, viewportWidth).
- **ActiveTab**: The currently visible and focused tab.
- **IframePane**: A React component wrapping a single `<iframe>` element, visible only when its tab is active.
- **IframeMask**: A transparent overlay rendered above all iframes to capture pointer events when a dropdown is open.
- **useTabs**: A React hook that owns and manages the full array of TabState objects and exposes tab operations.
- **useDropdown**: A React hook that manages which dropdown menu (if any) is currently open.
- **useIframeMessages**: A React hook that registers a `window.message` listener and routes inbound postMessages to the correct tab.
- **ProxyUrl**: A URL routed through the Spring Boot backend via the `?url=` query parameter.
- **DisplayUrl**: The original remote URL shown in the address bar (not the proxied form).
- **interceptor.js**: A JavaScript file injected by the Spring Boot backend into proxied pages; it sends postMessage events back to the parent frame.
- **Bookmark**: A named URL entry shown in the bookmarks dropdown menu.
- **buildProxyUrl**: A pure function that converts a user-entered URL into a ProxyUrl.
- **scaleIframe**: A function that applies a CSS transform to an IframePane when the page's reported viewport width exceeds the container width.
- **IframeMessage**: A postMessage payload sent from interceptor.js to the parent BrowserApp window.
- **OutboundIframeMessage**: A postMessage payload sent from BrowserApp to an iframe (e.g., back/forward commands).

---

## Requirements

### Requirement 1: Project Scaffold and Configuration

**User Story:** As a developer, I want a properly configured Next.js project in the `frontend/` directory, so that I can develop, build, and serve the browser UI independently of the Spring Boot backend.

#### Acceptance Criteria

1. THE Frontend_Project SHALL be located at `frontend/` in the project root and contain a valid `package.json`, `tsconfig.json`, and `next.config.ts`.
2. THE Frontend_Project SHALL declare `next` (v14+), `react` (v18+), and `react-dom` (v18+) as dependencies and `typescript`, `fast-check`, `jest`, and `@testing-library/react` as devDependencies.
3. WHEN the Next.js development server starts, THE Frontend_Project SHALL serve the BrowserApp at the root path (`/`).
4. THE `next.config.ts` SHALL configure URL rewrites so that requests matching `/?url=*` and `/api/*` are proxied to the Spring Boot backend.
5. THE Frontend_Project SHALL include a `public/` directory containing `logo.svg`, `loading.svg`, `favicon.png`, and `interceptor.js` copied from the existing `src/main/resources/static/` directory.

---

### Requirement 2: Tab Management

**User Story:** As a user, I want to open, switch between, and close multiple browser tabs, so that I can browse several pages simultaneously without losing my place.

#### Acceptance Criteria

1. THE useTabs_Hook SHALL maintain `tabs.length >= 1` at all times; closing the last remaining tab SHALL automatically open a new blank tab.
2. THE useTabs_Hook SHALL ensure `activeTabId` always refers to an id present in the `tabs` array.
3. WHEN `addTab()` is called, THE useTabs_Hook SHALL append a new TabState to the `tabs` array, set it as the ActiveTab, and increase `tabs.length` by exactly 1.
4. WHEN `closeTab(id)` is called and `id` equals `activeTabId`, THE useTabs_Hook SHALL activate the nearest sibling tab (preferring the next sibling, falling back to the previous sibling).
5. WHEN `closeTab(id)` is called and `tabs.length === 1`, THE useTabs_Hook SHALL create a new blank tab and set it as the ActiveTab before removing the closed tab.
6. WHEN `activateTab(id)` is called, THE useTabs_Hook SHALL set `activeTabId` to `id` and update the AddressBar to display the tab's `displayUrl`.
7. WHEN `closeOthers()` is called, THE useTabs_Hook SHALL close all tabs except the ActiveTab.
8. WHEN `closeRight()` is called, THE useTabs_Hook SHALL close all tabs positioned to the right of the ActiveTab in the tab strip.

---

### Requirement 3: Address Bar and URL Navigation

**User Story:** As a user, I want to type a URL or search term in the address bar and navigate to it, so that I can browse any website through the proxy.

#### Acceptance Criteria

1. WHEN a user types a URL in the AddressBar and presses Enter, THE BrowserApp SHALL call `navigate(activeTabId, url)` with the entered value.
2. WHEN `navigate(id, url)` is called, THE useTabs_Hook SHALL set `tabs[id].loading` to `true` and `tabs[id].url` to `buildProxyUrl(url)` immediately.
3. THE buildProxyUrl_Function SHALL return a non-empty string for any non-empty input string.
4. WHEN the input URL already contains `window.location.host`, THE buildProxyUrl_Function SHALL return the URL unchanged.
5. WHEN the input URL does not contain `//`, THE buildProxyUrl_Function SHALL prepend `https://` before proxying.
6. WHEN the input URL contains `//` and does not contain `window.location.host`, THE buildProxyUrl_Function SHALL return `"?url=" + encodeURIComponent(url)`.
7. WHEN the AddressBar receives focus, THE AddressBar SHALL select all text in the input field and apply a visual focus style.
8. WHEN the AddressBar loses focus, THE AddressBar SHALL remove the focus style.
9. THE AddressBar SHALL display the ActiveTab's `displayUrl` (the original remote URL, not the proxied form).

---

### Requirement 4: Navigation Controls

**User Story:** As a user, I want back, forward, and refresh buttons, so that I can navigate within a tab's history without retyping URLs.

#### Acceptance Criteria

1. WHEN the back button is clicked, THE BrowserApp SHALL send `{ action: 'back' }` via postMessage to the ActiveTab's iframe.
2. WHEN the forward button is clicked, THE BrowserApp SHALL send `{ action: 'forward' }` via postMessage to the ActiveTab's iframe.
3. WHEN the refresh button is clicked, THE BrowserApp SHALL call `navigate(activeTabId, activeTab.displayUrl)` to reload the current page.
4. WHILE `activeTab.canGoBack` is `false`, THE NavButtons_Component SHALL render the back button in a disabled state.
5. WHILE `activeTab.canGoForward` is `false`, THE NavButtons_Component SHALL render the forward button in a disabled state.

---

### Requirement 5: Iframe Rendering and Lifecycle

**User Story:** As a user, I want each tab to display its page in an isolated iframe, so that pages are sandboxed and tab state is preserved when switching tabs.

#### Acceptance Criteria

1. THE IframePane_Component SHALL render each tab's iframe with `sandbox="allow-scripts allow-same-origin allow-downloads allow-forms"`.
2. WHILE a tab is not the ActiveTab, THE IframePane_Component SHALL hide the iframe using `visibility: hidden` (not `display: none`) to preserve page state.
3. WHEN a tab becomes the ActiveTab, THE IframePane_Component SHALL make its iframe visible.
4. THE PageArea_Component SHALL never unmount an IframePane while its corresponding tab exists, regardless of which tab is active.
5. WHEN `navigate(id, url)` is called, THE IframePane_Component SHALL set the iframe's `src` attribute to the ProxyUrl.

---

### Requirement 6: postMessage Communication

**User Story:** As a developer, I want the app to handle postMessage events from interceptor.js, so that tab titles, favicons, loading states, and new-tab requests are kept in sync with the loaded page.

#### Acceptance Criteria

1. THE useIframeMessages_Hook SHALL register exactly one `window.addEventListener('message', ...)` listener on mount and remove it on unmount.
2. WHEN a `message` event is received, THE useIframeMessages_Hook SHALL identify the source tab by matching `event.source` against the `iframeRefs` map.
3. IF a `message` event's source does not match any known iframe, THEN THE useIframeMessages_Hook SHALL silently ignore the event.
4. WHEN a `{ action: 'load', url, title, icon, width }` message is received, THE BrowserApp SHALL call `updateTab(tabId, { url, title, icon: icon ?? 'logo.svg', loading: false, viewportWidth: width })`.
5. WHEN a `{ action: 'unload' }` message is received, THE BrowserApp SHALL call `updateTab(tabId, { loading: true })`.
6. WHEN a `{ action: 'open', url }` message is received, THE BrowserApp SHALL call `addTab(url)` to open the URL in a new tab.
7. WHEN a `{ action: 'load' }` message is received and `viewportWidth` is defined, THE IframePane_Component SHALL call `scaleIframe` to apply or clear the CSS scale transform.

---

### Requirement 7: Iframe Scaling

**User Story:** As a user, I want non-responsive pages to be scaled down to fit the viewport, so that I can see the full page layout without horizontal scrolling.

#### Acceptance Criteria

1. WHEN `scaleIframe` is called and `reportedWidth` is defined and `containerWidth < reportedWidth`, THE scaleIframe_Function SHALL apply `transform: scale(scale, scale)` with `transform-origin: top left` to the iframe element, where `scale = containerWidth / reportedWidth`.
2. WHEN `scaleIframe` is called and `reportedWidth` is defined and `containerWidth < reportedWidth`, THE scaleIframe_Function SHALL set `iframe.style.width` to `reportedWidth + 'px'` and `iframe.style.height` to `floor(containerHeight / scale) + 'px'`.
3. WHEN `scaleIframe` is called and `containerWidth >= reportedWidth`, THE scaleIframe_Function SHALL clear all inline transform and size styles from the iframe element.
4. WHEN `scaleIframe` is called and `reportedWidth` is undefined, THE scaleIframe_Function SHALL clear all inline transform and size styles from the iframe element.
5. THE scaleIframe_Function SHALL only be invoked in response to a `'load'` postMessage, not on every render cycle.

---

### Requirement 8: Dropdown Menus

**User Story:** As a user, I want dropdown menus for bookmarks, settings, and open tabs, so that I can access browser actions without cluttering the main toolbar.

#### Acceptance Criteria

1. THE useDropdown_Hook SHALL ensure at most one dropdown menu is open at any time.
2. WHEN `toggle(id)` is called and a different dropdown is currently open, THE useDropdown_Hook SHALL close the open dropdown before opening the requested one.
3. WHEN `toggle(id)` is called and `id` is already open, THE useDropdown_Hook SHALL close it.
4. WHEN `closeAll()` is called, THE useDropdown_Hook SHALL set `openDropdown` to `null`.
5. WHEN any dropdown is open, THE PageArea_Component SHALL render the IframeMask overlay to capture pointer events over iframes.
6. WHEN the IframeMask is clicked, THE BrowserApp SHALL call `closeAll()` to close all dropdowns and hide the mask.
7. THE BookmarksMenu_Component SHALL render a list of Bookmark entries; WHEN a bookmark is clicked, THE BookmarksMenu_Component SHALL call `onNavigate(bookmark.url)` and close the menu.
8. THE SettingsMenu_Component SHALL expose actions for: New Tab, Close Tab, Close Other Tabs, and Close Tabs to the Right.
9. THE OpenedTabsMenu_Component SHALL render one entry per open tab showing the tab's favicon and title; WHEN an entry is clicked, THE OpenedTabsMenu_Component SHALL activate that tab.
10. WHEN the Escape key is pressed, THE BrowserApp SHALL call `closeAll()` to close any open dropdown.

---

### Requirement 9: Loading Indicator

**User Story:** As a user, I want a loading spinner on the tab while a page is loading, so that I know when navigation is in progress.

#### Acceptance Criteria

1. WHILE `tab.loading` is `true`, THE Tab_Component SHALL display the loading spinner image and hide the favicon image.
2. WHILE `tab.loading` is `false`, THE Tab_Component SHALL display the favicon image and hide the loading spinner.
3. WHEN `navigate(id, url)` is called, THE useTabs_Hook SHALL set `tabs[id].loading` to `true` immediately.
4. WHEN a `{ action: 'load' }` postMessage is received for a tab, THE BrowserApp SHALL set `tabs[id].loading` to `false`.

---

### Requirement 10: Component Architecture

**User Story:** As a developer, I want the UI decomposed into focused React components with well-defined props interfaces, so that the codebase is maintainable and each component can be tested in isolation.

#### Acceptance Criteria

1. THE Frontend_Project SHALL implement the following components, each in its own file under `frontend/components/`: `TopBar`, `TabBar`, `Tab`, `MainBar`, `AddressBar`, `NavButtons`, `BookmarksMenu`, `SettingsMenu`, `OpenedTabsMenu`, `PageArea`, `IframePane`.
2. THE Frontend_Project SHALL implement the following hooks, each in its own file under `frontend/hooks/`: `useTabs`, `useDropdown`, `useIframeMessages`.
3. THE Frontend_Project SHALL define shared TypeScript types (`TabState`, `Bookmark`, `IframeMessage`, `OutboundIframeMessage`) in `frontend/types/browser.ts`.
4. THE buildProxyUrl_Function SHALL be implemented as a pure function in `frontend/lib/url.ts`.
5. THE BrowserApp_Component SHALL own all global state via hooks and pass state and callbacks down to child components via props; child components SHALL NOT manage global tab or dropdown state directly.

---

### Requirement 11: Styling

**User Story:** As a user, I want the Next.js frontend to look and behave identically to the existing browser UI, so that the refactor is visually transparent.

#### Acceptance Criteria

1. THE Frontend_Project SHALL port the existing `browser.css` styles into `frontend/app/globals.css` or CSS modules, preserving all visual appearance including tab shapes, address bar styling, dropdown positioning, and iframe layout.
2. THE Frontend_Project SHALL import `modern-normalize.min.css` or an equivalent CSS reset.
3. THE IframePane_Component SHALL position iframes absolutely within the PageArea using `position: absolute; top: 0; left: 0; width: 100%; height: 100%` matching the existing CSS.
4. THE Tab_Component SHALL render the curved tab shape with rounded corner decorators matching the existing `.round-left` / `.round-right` CSS treatment.

---

### Requirement 12: Error Handling

**User Story:** As a user, I want the browser to handle errors gracefully, so that a failed page load or unexpected input does not break the application.

#### Acceptance Criteria

1. IF a proxied page fails to load and no `{ action: 'load' }` postMessage is received within 30 seconds, THEN THE useIframeMessages_Hook SHALL clear the loading state for that tab and set a generic error title.
2. WHEN a user enters a non-URL string in the AddressBar, THE buildProxyUrl_Function SHALL prepend `https://` and proxy the result, allowing the backend or remote server to return an error page inside the iframe.
3. IF a `message` event is received from a source not present in `iframeRefs`, THEN THE useIframeMessages_Hook SHALL ignore the event without throwing an error or modifying any tab state.
4. WHEN `closeTab(id)` is called and `tabs.length === 1`, THE useTabs_Hook SHALL open a new blank tab automatically so the application always has at least one tab.

---

### Requirement 13: Testing

**User Story:** As a developer, I want unit and property-based tests for core logic, so that I can refactor with confidence and catch regressions early.

#### Acceptance Criteria

1. THE Frontend_Project SHALL include unit tests for `useTabs` verifying: minimum 1 tab invariant, `activeTabId` validity invariant, and loading state transitions.
2. THE Frontend_Project SHALL include unit tests for `buildProxyUrl` verifying: URL passthrough for same-host URLs, `https://` prepend for protocol-less inputs, and `?url=` wrapping for external URLs.
3. THE Frontend_Project SHALL include unit tests for the `closeTab` reducer logic verifying: sibling selection order and last-tab auto-open behaviour.
4. THE Frontend_Project SHALL include unit tests for `useDropdown` verifying the at-most-one-open invariant.
5. THE Frontend_Project SHALL include property-based tests using `fast-check` with a minimum of 100 iterations per property.
6. THE Frontend_Project SHALL include an integration test that renders `BrowserApp` in a jsdom environment and simulates add-tab, type-URL, and close-tab interactions to verify end-to-end state transitions.
7. THE Frontend_Project SHALL include an integration test that mocks `window.postMessage` to simulate interceptor messages and verifies that tab title, icon, and loading state are updated correctly.
