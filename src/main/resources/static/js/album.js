const main = document.querySelector('.album');

if (main) {
    const sleeve = main.querySelector('.album-sleeve');

    sleeve.addEventListener('click', () => {
        const flipped = sleeve.classList.toggle('is-flipped');
        sleeve.setAttribute('aria-pressed', String(flipped));
        // Restart the lift-and-turn animation for the new direction.
        sleeve.classList.remove('flip-to-back', 'flip-to-front');
        void sleeve.offsetWidth;
        sleeve.classList.add(flipped ? 'flip-to-back' : 'flip-to-front');
    });

    document.addEventListener('keydown', event => {
        if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
        const target = event.key === 'ArrowLeft' ? main.dataset.prev
            : event.key === 'ArrowRight' ? main.dataset.next
            : null;
        if (target) window.location.assign(target);
    });

    // The sleeve sinks and tilts toward a "pressed" point (the pressed side recedes) and a soft highlight enters
    // from the opposite side. Everything eases toward its target frame by frame, so nothing snaps. The point comes
    // from the mouse on desktop, or from the tilt sensor on touch devices. Reduced-motion users get a still cover.
    const canHover = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (!reducedMotion) {
        const MAX_TILT = 8;
        const EDGE_TILT = 0.6;
        const clamp = value => Math.min(1, Math.max(0, value));
        const smoothstep = (from, to, value) => {
            const t = clamp((value - from) / (to - from));
            return t * t * (3 - 2 * t);
        };

        // Time constants in ms: larger = slower, calmer.
        const TAU = { intensityIn: 150, intensityOut: 170, tilt: 65, glarePosition: 110, glareIn: 210, glareOut: 260 };

        const state = { intensity: 0, sink: 0, tiltX: 0, tiltY: 0, gx: 50, gy: 50, glare: 0, shadowX: 0, shadowY: 30 };
        const target = { tiltX: 0, tiltY: 0, depth: 0, gx: 50, gy: 50, shadowX: 0, shadowY: 30 };
        let hovering = false;
        let frame = null;
        let last = 0;

        // px/py: where the cover is "pressed", 0..1 from the top-left. The depth is 0 at the edge and 1 well inside
        // for the mouse (the cover sinks and tilts more the deeper it goes); the phone passes a fixed value.
        const setTargetFraction = (px, py, depth = smoothstep(0, 0.5, 2 * Math.min(px, 1 - px, py, 1 - py))) => {
            target.tiltY = (px - 0.5) * 2 * MAX_TILT;
            target.tiltX = (0.5 - py) * 2 * MAX_TILT;
            target.depth = depth;
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
            // The first frame's timestamp can precede the start time, so never let dt go negative.
            const dt = Math.max(0, Math.min(now - last, 50));
            last = now;
            const ease = tau => 1 - Math.exp(-dt / tau);

            const wanted = hovering ? 1 : 0;
            state.intensity += (wanted - state.intensity) * ease(hovering ? TAU.intensityIn : TAU.intensityOut);
            state.glare += (wanted - state.glare) * ease(hovering ? TAU.glareIn : TAU.glareOut);

            // While active the target follows the pointer or phone; after leaving it keeps its last value, so the
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

        // Start the highlight outside the cover on its own side so it glides in instead of appearing.
        const engage = () => {
            state.gx = 50 + (target.gx - 50) * 1.7;
            state.gy = 50 + (target.gy - 50) * 1.7;
            hovering = true;
            start();
        };
        const release = () => {
            hovering = false;
            start();
        };

        const setupMouse = () => {
            const fromPointer = event => {
                const rect = sleeve.getBoundingClientRect();
                setTargetFraction(clamp((event.clientX - rect.left) / rect.width), clamp((event.clientY - rect.top) / rect.height));
            };
            sleeve.addEventListener('pointerenter', event => {
                if (event.pointerType !== 'mouse') return;
                fromPointer(event);
                engage();
            });
            sleeve.addEventListener('pointermove', event => {
                if (event.pointerType !== 'mouse') return;
                fromPointer(event);
            });
            sleeve.addEventListener('pointerleave', release);
        };

        // Phone: the tilt sensor moves the "pressed" point like a mouse would. iOS asks for permission on a tap, and
        // browsers only expose the sensor on HTTPS, so the button stays hidden wherever it cannot work.
        const setupDeviceTilt = () => {
            const toggle = main.querySelector('.album-motion-toggle');
            if (!toggle || !window.isSecureContext || !('DeviceOrientationEvent' in window)) return;

            const DEVICE_RANGE = 18;
            const FOLLOW_MS = 3500;
            let active = false;
            let received = false;
            let base = null;
            let lastEvent = 0;

            const wrap = degrees => ((degrees + 540) % 360) - 180;
            const screenAngle = () => (screen.orientation && typeof screen.orientation.angle === 'number')
                ? screen.orientation.angle : (window.orientation || 0);

            const onOrientation = event => {
                if (event.beta === null || event.gamma === null) return;
                received = true;
                let roll = event.gamma;
                let pitch = event.beta;
                switch (((screenAngle() % 360) + 360) % 360) {
                    case 90: [roll, pitch] = [pitch, -roll]; break;
                    case 180: [roll, pitch] = [-roll, -pitch]; break;
                    case 270: [roll, pitch] = [-pitch, roll]; break;
                    default: break;
                }
                const now = performance.now();
                const dt = Math.min(now - lastEvent, 200);
                lastEvent = now;
                if (base === null) base = { roll, pitch };
                // The reference slowly follows the phone, so holding it still lets the cover settle back to rest.
                const follow = 1 - Math.exp(-dt / FOLLOW_MS);
                base.roll += wrap(roll - base.roll) * follow;
                base.pitch += wrap(pitch - base.pitch) * follow;
                setTargetFraction(
                    clamp(0.5 + wrap(roll - base.roll) / (2 * DEVICE_RANGE)),
                    clamp(0.5 + wrap(pitch - base.pitch) / (2 * DEVICE_RANGE)),
                    0.6);
            };

            const setActive = value => {
                active = value;
                toggle.setAttribute('aria-pressed', String(value));
                toggle.textContent = value ? 'Handy-Bewegung: an' : 'Handy-Bewegung nutzen';
                if (value) {
                    base = null;
                    received = false;
                    window.addEventListener('deviceorientation', onOrientation);
                    engage();
                    // No sensor data (desktop, tablet without sensors): switch off and hide the button again.
                    setTimeout(() => {
                        if (active && !received) {
                            setActive(false);
                            toggle.hidden = true;
                        }
                    }, 1500);
                } else {
                    window.removeEventListener('deviceorientation', onOrientation);
                    release();
                }
            };

            toggle.hidden = false;
            toggle.addEventListener('click', async () => {
                if (active) {
                    setActive(false);
                    return;
                }
                if (typeof DeviceOrientationEvent.requestPermission === 'function') {
                    try {
                        if (await DeviceOrientationEvent.requestPermission() !== 'granted') return;
                    } catch (error) {
                        return;
                    }
                }
                setActive(true);
            });
        };

        if (canHover) {
            setupMouse();
        } else {
            setupDeviceTilt();
        }
    }
}
