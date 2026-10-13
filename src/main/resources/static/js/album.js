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
        const target = { tiltX: 0, tiltY: 0, depth: 0, glareStrength: 1, gx: 50, gy: 50, shadowX: 0, shadowY: 30 };
        let hovering = false;
        let frame = null;
        let last = 0;

        // px/py: where the cover is "pressed", 0..1 from the top-left. The depth is 0 at the edge and 1 well inside
        // for the mouse (the cover sinks and tilts more the deeper it goes); the phone passes a fixed value and a
        // glare strength that grows with the tilt, so a phone held still shows no highlight.
        const setTargetFraction = (px, py, depth = smoothstep(0, 0.5, 2 * Math.min(px, 1 - px, py, 1 - py)), glareStrength = 1) => {
            target.tiltY = (px - 0.5) * 2 * MAX_TILT;
            target.tiltX = (0.5 - py) * 2 * MAX_TILT;
            target.depth = depth;
            target.glareStrength = glareStrength;
            // The pressed side recedes, so the highlight sits on the opposite side, which tilts toward the viewer.
            target.gx = (1 - px) * 100;
            target.gy = (1 - py) * 100;
            target.shadowX = -(px - 0.5) * 28;
            target.shadowY = 30 - (py - 0.5) * 20;
            start();
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

            // Moves one value toward its goal and returns how far it moved, to know when everything has come to rest.
            let moved = 0;
            const approach = (key, goal, factor) => {
                const delta = (goal - state[key]) * factor;
                state[key] += delta;
                moved += Math.abs(delta);
            };

            const wanted = hovering ? 1 : 0;
            approach('intensity', wanted, ease(hovering ? TAU.intensityIn : TAU.intensityOut));
            approach('glare', wanted * target.glareStrength, ease(hovering ? TAU.glareIn : TAU.glareOut));

            // While active the target follows the pointer or phone; after leaving it keeps its last value, so the
            // highlight fades out in place while tilt and sink ease back to rest through the intensity.
            const strength = state.intensity;
            const tiltScale = strength * (EDGE_TILT + (1 - EDGE_TILT) * target.depth);
            const tilt = ease(TAU.tilt);
            approach('tiltX', target.tiltX * tiltScale, tilt);
            approach('tiltY', target.tiltY * tiltScale, tilt);
            approach('sink', strength * target.depth, tilt);
            approach('shadowX', target.shadowX * strength, tilt);
            approach('shadowY', 30 + (target.shadowY - 30) * strength, tilt);

            const glarePosition = ease(TAU.glarePosition);
            approach('gx', target.gx, glarePosition);
            approach('gy', target.gy, glarePosition);

            render();

            // Stop the loop once nothing moves any more (a phone held still); new input restarts it.
            const settled = dt > 0 && (hovering ? moved < 0.0005 : state.intensity < 0.002 && state.glare < 0.002);
            frame = settled ? null : requestAnimationFrame(tick);
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

        // Phone: the tilt sensor moves the "pressed" point like a mouse would. iOS only releases the sensor after a tap
        // (a button asks for that); elsewhere no permission is needed, so it starts by itself and the button stays
        // hidden. Browsers only expose the sensor on HTTPS.
        const setupDeviceTilt = () => {
            const toggle = main.querySelector('.album-motion-toggle');
            if (!window.isSecureContext || !('DeviceOrientationEvent' in window)) return;

            const DEVICE_RANGE = 18;
            const FOLLOW_MS = 3500;
            let wantMotion = false;
            let listening = false;
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
                const px = clamp(0.5 + wrap(roll - base.roll) / (2 * DEVICE_RANGE));
                const py = clamp(0.5 + wrap(pitch - base.pitch) / (2 * DEVICE_RANGE));
                // The highlight appears with the tilt and is gone while the phone rests.
                const glareStrength = smoothstep(0.04, 0.5, Math.hypot(px - 0.5, py - 0.5) * 2);
                setTargetFraction(px, py, 0.6, glareStrength);
                if (!hovering) engage();
            };

            const listen = () => {
                if (listening) return;
                listening = true;
                base = null;
                window.addEventListener('deviceorientation', onOrientation);
            };
            const unlisten = () => {
                if (!listening) return;
                listening = false;
                window.removeEventListener('deviceorientation', onOrientation);
                release();
            };

            // Do not read the sensor while the page is in the background.
            document.addEventListener('visibilitychange', () => {
                if (document.hidden) {
                    unlisten();
                } else if (wantMotion) {
                    listen();
                }
            });

            const needsPermission = typeof DeviceOrientationEvent.requestPermission === 'function';
            if (!needsPermission) {
                wantMotion = true;
                listen();
                return;
            }
            if (!toggle) return;

            const setActive = value => {
                wantMotion = value;
                toggle.setAttribute('aria-pressed', String(value));
                toggle.textContent = value ? 'Handy-Bewegung: an' : 'Handy-Bewegung nutzen';
                if (value) {
                    received = false;
                    listen();
                    // No sensor data: switch off and hide the button again.
                    setTimeout(() => {
                        if (wantMotion && !received) {
                            setActive(false);
                            toggle.hidden = true;
                        }
                    }, 1500);
                } else {
                    unlisten();
                }
            };

            toggle.hidden = false;
            toggle.addEventListener('click', async () => {
                if (wantMotion) {
                    setActive(false);
                    return;
                }
                try {
                    if (await DeviceOrientationEvent.requestPermission() !== 'granted') return;
                } catch (error) {
                    return;
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
