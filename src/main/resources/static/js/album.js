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

    // The sleeve tilts toward the pointer (the pressed side recedes) and a soft highlight follows it.
    // Pointer-only: touch has no hover, and reduced-motion users get a still cover.
    const canHover = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (canHover && !reducedMotion) {
        const MAX_TILT = 9;
        const clamp = value => Math.min(1, Math.max(0, value));
        let pending = null;

        const apply = () => {
            const { x, y } = pending;
            pending = null;
            const rect = sleeve.getBoundingClientRect();
            const px = clamp((x - rect.left) / rect.width);
            const py = clamp((y - rect.top) / rect.height);
            const style = sleeve.style;
            style.setProperty('--tilt-y', ((px - 0.5) * 2 * MAX_TILT).toFixed(2) + 'deg');
            style.setProperty('--tilt-x', ((0.5 - py) * 2 * MAX_TILT).toFixed(2) + 'deg');
            style.setProperty('--glare-x', (px * 100).toFixed(1) + '%');
            style.setProperty('--glare-y', (py * 100).toFixed(1) + '%');
            style.setProperty('--shadow-x', (-(px - 0.5) * 28).toFixed(1) + 'px');
            style.setProperty('--shadow-y', (30 - (py - 0.5) * 20).toFixed(1) + 'px');
        };

        const reset = () => {
            ['--tilt-x', '--tilt-y', '--glare-x', '--glare-y', '--shadow-x', '--shadow-y']
                .forEach(name => sleeve.style.removeProperty(name));
        };

        sleeve.addEventListener('pointerenter', () => sleeve.classList.add('is-tracking'));
        sleeve.addEventListener('pointermove', event => {
            if (event.pointerType !== 'mouse') return;
            const schedule = pending === null;
            pending = { x: event.clientX, y: event.clientY };
            if (schedule) requestAnimationFrame(apply);
        });
        sleeve.addEventListener('pointerleave', () => {
            pending = null;
            sleeve.classList.remove('is-tracking');
            reset();
        });
    }
}
