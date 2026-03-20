import { scaleIframe } from './scale';

function makeIframe(parentHeight = 600): HTMLIFrameElement {
  const parent = document.createElement('div');
  Object.defineProperty(parent, 'clientHeight', { value: parentHeight, configurable: true });
  const iframe = document.createElement('iframe');
  parent.appendChild(iframe);
  return iframe;
}

describe('scaleIframe', () => {
  describe('when scaling is needed (containerWidth < reportedWidth)', () => {
    it('applies transform scale with top left origin', () => {
      const iframe = makeIframe(600);
      scaleIframe(iframe, 800, 1200);
      const scale = 800 / 1200;
      expect(iframe.style.transform).toBe(`scale(${scale}, ${scale})`);
      expect(iframe.style.transformOrigin).toBe('top left');
    });

    it('sets iframe width to reportedWidth px', () => {
      const iframe = makeIframe(600);
      scaleIframe(iframe, 800, 1200);
      expect(iframe.style.width).toBe('1200px');
    });

    it('sets iframe height to floor(containerHeight / scale) px', () => {
      const iframe = makeIframe(600);
      scaleIframe(iframe, 800, 1200);
      const scale = 800 / 1200;
      expect(iframe.style.height).toBe(`${Math.floor(600 / scale)}px`);
    });

    it('uses floor for height calculation', () => {
      const iframe = makeIframe(100);
      scaleIframe(iframe, 3, 4); // scale = 0.75, height = floor(100 / 0.75) = floor(133.33) = 133
      expect(iframe.style.height).toBe('133px');
    });
  });

  describe('when scaling is not needed (containerWidth >= reportedWidth)', () => {
    it('clears styles when containerWidth equals reportedWidth', () => {
      const iframe = makeIframe(600);
      // First apply scaling
      scaleIframe(iframe, 800, 1200);
      // Then clear it
      scaleIframe(iframe, 1200, 1200);
      expect(iframe.style.transform).toBe('');
      expect(iframe.style.transformOrigin).toBe('');
      expect(iframe.style.width).toBe('');
      expect(iframe.style.height).toBe('');
    });

    it('clears styles when containerWidth is greater than reportedWidth', () => {
      const iframe = makeIframe(600);
      scaleIframe(iframe, 800, 1200);
      scaleIframe(iframe, 1400, 1200);
      expect(iframe.style.transform).toBe('');
      expect(iframe.style.transformOrigin).toBe('');
      expect(iframe.style.width).toBe('');
      expect(iframe.style.height).toBe('');
    });
  });

  describe('when reportedWidth is undefined', () => {
    it('clears all inline styles', () => {
      const iframe = makeIframe(600);
      scaleIframe(iframe, 800, 1200);
      scaleIframe(iframe, 800, undefined);
      expect(iframe.style.transform).toBe('');
      expect(iframe.style.transformOrigin).toBe('');
      expect(iframe.style.width).toBe('');
      expect(iframe.style.height).toBe('');
    });
  });
});
