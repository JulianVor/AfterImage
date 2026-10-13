const main = document.querySelector('.album');

if (main) {
    const sleeve = main.querySelector('.album-sleeve');

    sleeve.addEventListener('click', () => {
        const flipped = sleeve.classList.toggle('is-flipped');
        sleeve.setAttribute('aria-pressed', String(flipped));
    });

    document.addEventListener('keydown', event => {
        if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
        const target = event.key === 'ArrowLeft' ? main.dataset.prev
            : event.key === 'ArrowRight' ? main.dataset.next
            : null;
        if (target) window.location.assign(target);
    });

    // The sleeve sinks and tilts toward the pointer (the pressed side recedes) and a soft highlight enters
    // from the opposite side. Everything eases toward its target frame by frame, so entering at an edge
    // never snaps. Pointer-only: touch has no hover, and reduced-motion users get a still cover.
    const canHover = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (canHover && !reducedMotion) {
        const MAX_TILT = 8;
        const EDGE_TILT = 0.45;
        const clamp = value => Math.min(1, Math.max(0, value));
        const smoothstep = (from, to, value) => {
            const t = clamp((value - from) / (to - from));
            return t * t * (3 - 2 * t);
        };

        // Time constants in ms: larger = slower, calmer.
        const TAU = { intensityIn: 580, intensityOut: 480, tilt: 190, glarePosition: 340, glareIn: 700, glareOut: 620 };

        const state = { intensity: 0, sink: 0, tiltX: 0, tiltY: 0, gx: 50, gy: 50, glare: 0, shadowX: 0, shadowY: 30 };
        const target = { tiltX: 0, tiltY: 0, depth: 0, gx: 50, gy: 50, shadowX: 0, shadowY: 30 };
        let hovering = false;
        let frame = null;
        let last = 0;

        const setTarget = (x, y) => {
            const rect = sleeve.getBoundingClientRect();
            const px = clamp((x - rect.left) / rect.width);
            const py = clamp((y - rect.top) / rect.height);
            target.tiltY = (px - 0.5) * 2 * MAX_TILT;
            target.tiltX = (0.5 - py) * 2 * MAX_TILT;
            // 0 at the edge, 1 once the pointer is well inside: the cover sinks and tilts more the deeper it goes.
            target.depth = smoothstep(0, 0.5, 2 * Math.min(px, 1 - px, py, 1 - py));
            // The pressed side recedes, so the highlight sits on the opposite side, which tilts toward the viewer.
            target.gx = (1 - px) * 100;
            target.gy = (1 - py) * 100;
            target.shadowX = -(px - 0.5) * 28;
            target.shadowY = 30 - (py - 0.5) * 20;
        };

        const render = () => {
            const style = sleeve.style;
            style.setProperty('--tilt-x', state.tiltX.toFixed(3) + 'deg');
            style.setProperty('--tilt-y', state.tiltY.toFixed(3) + 'deg');
            style.setProperty('--sink', state.sink.toFixed(4));
            style.setProperty('--glare-x', state.gx.toFixed(2) + '%');
            style.setProperty('--glare-y', state.gy.toFixed(2) + '%');
            style.setProperty('--glare-o', state.glare.toFixed(4));
            style.setProperty('--shadow-x', state.shadowX.toFixed(2) + 'px');
            style.setProperty('--shadow-y', state.shadowY.toFixed(2) + 'px');
        };

        const tick = now => {
            const dt = Math.min(now - last, 50);
            last = now;
            const ease = tau => 1 - Math.exp(-dt / tau);

            const wanted = hovering ? 1 : 0;
            state.intensity += (wanted - state.intensity) * ease(hovering ? TAU.intensityIn : TAU.intensityOut);
            state.glare += (wanted - state.glare) * ease(hovering ? TAU.glareIn : TAU.glareOut);

            // While hovering the target follows the pointer; after leaving it keeps its last value, so the
            // highlight fades out in place while tilt and sink ease back to rest through the intensity.
            const strength = state.intensity;
            const tiltScale = strength * (EDGE_TILT + (1 - EDGE_TILT) * target.depth);
            const tilt = ease(TAU.tilt);
            state.tiltX += (target.tiltX * tiltScale - state.tiltX) * tilt;
            state.tiltY += (target.tiltY * tiltScale - state.tiltY) * tilt;
            state.sink += (strength * target.depth - state.sink) * tilt;
            state.shadowX += (target.shadowX * strength - state.shadowX) * tilt;
            state.shadowY += (30 + (target.shadowY - 30) * strength - state.shadowY) * tilt;

            const glarePosition = ease(TAU.glarePosition);
            state.gx += (target.gx - state.gx) * glarePosition;
            state.gy += (target.gy - state.gy) * glarePosition;

            render();

            const settled = !hovering && state.intensity < 0.002 && state.glare < 0.002;
            if (settled) {
                frame = null;
            } else {
                frame = requestAnimationFrame(tick);
            }
        };

        const start = () => {
            if (frame === null) {
                last = performance.now();
                frame = requestAnimationFrame(tick);
            }
        };

        sleeve.addEventListener('pointerenter', event => {
            if (event.pointerType !== 'mouse') return;
            setTarget(event.clientX, event.clientY);
            // Start the highlight outside the cover on its own side so it glides in instead of appearing.
            state.gx = 50 + (target.gx - 50) * 2.4;
            state.gy = 50 + (target.gy - 50) * 2.4;
            hovering = true;
            start();
        });
        sleeve.addEventListener('pointermove', event => {
            if (event.pointerType !== 'mouse') return;
            setTarget(event.clientX, event.clientY);
        });
        sleeve.addEventListener('pointerleave', () => {
            hovering = false;
            start();
        });
    }
}
