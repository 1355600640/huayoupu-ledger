'use strict';
// Smooth, hardware-accelerated bottom-sheet and modal animations.
window.FluidMotion = (() => {
  function stop(panel) {
    if (panel._morph) {
      panel._morph.cancel();
      panel._morph = null;
    }
    if (panel._fade) {
      panel._fade.cancel();
      panel._fade = null;
    }
    if (panel._contentAnimations) {
      panel._contentAnimations.forEach(a => a.cancel());
      panel._contentAnimations = null;
    }
    if (panel._surface) {
      panel._surface.remove();
      panel._surface = null;
    }
    panel.style.willChange = '';
    panel.style.transform = '';
    panel.classList.remove('fluid-active', 'fluid-pending');
  }

  function run(panel, origin, reverse, done = () => {}, shade) {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches || !panel.animate) {
      stop(panel);
      done();
      return;
    }

    panel.classList.remove('fluid-pending');

    // Snappy physics curves: Material 3 deceleration for open, crisp ease-in for close
    const duration = reverse ? 200 : 260;
    const easing = reverse ? 'cubic-bezier(0.3, 0, 0.8, 0.15)' : 'cubic-bezier(0.16, 1, 0.3, 1)';

    let startTransform = reverse ? 'translate3d(0, 0, 0)' : 'translate3d(0, 100%, 0)';
    let endTransform = reverse ? 'translate3d(0, 100%, 0)' : 'translate3d(0, 0, 0)';

    // Handle seamless interruptions if tapped mid-flight
    if (panel._morph && panel._morph.playState === 'running') {
      try {
        const style = getComputedStyle(panel);
        const tf = style.transform;
        if (tf && tf !== 'none') {
          const match = tf.match(/matrix\([^,]+,\s*[^,]+,\s*[^,]+,\s*[^,]+,\s*[^,]+,\s*([^)]+)\)/);
          if (match) {
            const currentY = parseFloat(match[1]);
            startTransform = `translate3d(0, ${currentY}px, 0)`;
          }
        }
      } catch (_) {}
    }

    // Cancel previous animations without flashing
    if (panel._morph) {
      panel._morph.cancel();
      panel._morph = null;
    }
    if (panel._fade) {
      panel._fade.cancel();
      panel._fade = null;
    }

    panel.style.willChange = 'transform';

    // Pure transform animation: composite-only on GPU, eliminating reflow and layer rasterization
    const panelFrames = [
      { transform: startTransform },
      { transform: endTransform }
    ];

    const animation = panel.animate(panelFrames, {
      duration,
      easing,
      fill: 'forwards'
    });
    panel._morph = animation;

    // Synchronize backdrop / shade
    if (shade && shade.animate) {
      const shadeStart = reverse ? 1 : 0;
      const shadeEnd = reverse ? 0 : 1;
      const shadeFade = shade.animate(
        [{ opacity: shadeStart }, { opacity: shadeEnd }],
        { duration, easing, fill: 'forwards' }
      );
      panel._fade = shadeFade;
    }

    animation.onfinish = () => {
      if (panel._morph !== animation) return;
      panel._morph = null;
      if (reverse) {
        panel.style.transform = endTransform;
      } else {
        panel.style.transform = 'translate3d(0, 0, 0)';
      }
      if (panel._fade) {
        if (shade) shade.style.opacity = reverse ? '0' : '1';
        panel._fade.cancel();
        panel._fade = null;
      }
      animation.cancel();
      panel.style.willChange = '';
      done();
    };
  }

  return { run, stop };
})();
