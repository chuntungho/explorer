export function scaleIframe(
  iframe: HTMLIFrameElement,
  containerWidth: number,
  reportedWidth: number | undefined
): void {
  if (reportedWidth !== undefined && containerWidth < reportedWidth) {
    const scale = containerWidth / reportedWidth;
    const containerHeight = iframe.parentElement?.clientHeight ?? 0;
    iframe.style.transformOrigin = 'top left';
    iframe.style.transform = `scale(${scale}, ${scale})`;
    iframe.style.width = `${reportedWidth}px`;
    iframe.style.height = `${Math.floor(containerHeight / scale)}px`;
  } else {
    iframe.style.transformOrigin = '';
    iframe.style.transform = '';
    iframe.style.width = '';
    iframe.style.height = '';
  }
}
