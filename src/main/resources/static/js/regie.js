const stage = document.querySelector('.regie-console');

if (stage) {
    const audio = stage.querySelector('.regie-audio');
    const videos = [...stage.querySelectorAll('.regie-video')];
    const cameras = [...stage.querySelectorAll('.regie-camera')];
    const programVideo = stage.querySelector('.regie-program-video');
    const playOverlay = stage.querySelector('.regie-play-overlay');
    const toggle = stage.querySelector('.regie-toggle');
    const playIcon = stage.querySelector('.icon-play');
    const pauseIcon = stage.querySelector('.icon-pause');
    const elapsedEl = stage.querySelector('.regie-elapsed');
    const durationEl = stage.querySelector('.regie-duration');
    const bar = stage.querySelector('.regie-bar');
    const progress = stage.querySelector('.regie-progress');
    const cutTrack = stage.querySelector('.regie-cut-track');
    const programName = stage.querySelector('.regie-program-name');
    const cutAge = stage.querySelector('.regie-cut-age');
    const liveCamera = stage.querySelector('.regie-live-camera');
    const liveLook = stage.querySelector('.regie-live-look');
    const lookButtons = [...stage.querySelectorAll('.regie-look')];
    const lookNote = stage.querySelector('.regie-look-note');
    const task = stage.querySelector('.regie-task');
    const cutCount = stage.querySelector('.regie-cut-count');
    const cameraCount = stage.querySelector('.regie-camera-count');
    const result = stage.querySelector('.regie-result');
    const RESYNC_THRESHOLD = 0.2;

    let started = false;
    let replaying = false;
    let replayIndex = 0;
    let activeCamera = cameras[0]?.dataset.camera;
    let activeLook = 'normal';
    let decisions = [];
    let syncFrame = null;

    cameras.forEach((camera, index) => {
        const key = index < 9 ? String(index + 1) : '–';
        camera.dataset.key = key;
        camera.querySelector('kbd').textContent = key;
    });

    const formatTime = seconds => {
        if (!Number.isFinite(seconds)) return '–:--';
        return `${Math.floor(seconds / 60)}:${Math.floor(seconds % 60).toString().padStart(2, '0')}`;
    };

    const cameraButton = cameraId => cameras.find(camera => camera.dataset.camera === cameraId);

    const refreshPreviews = () => {
        cameras.forEach(camera => {
            const preferred = camera.querySelector(`.regie-video[data-look="${activeLook}"]`);
            const fallback = camera.querySelector('.regie-video[data-look="normal"]') || camera.querySelector('.regie-video');
            camera.querySelectorAll('.regie-video').forEach(video => video.classList.toggle('is-preview-active', video === (preferred || fallback)));
        });
    };

    const updateLookControls = () => {
        const hasEffects = videos.some(video => video.dataset.look === 'effect');
        lookButtons.forEach(button => {
            button.disabled = button.dataset.look === 'effect' && !hasEffects;
            button.classList.toggle('is-active', button.dataset.look === activeLook);
            button.setAttribute('aria-pressed', String(button.dataset.look === activeLook));
        });
        lookNote.textContent = hasEffects ? '' : 'Für dieses Video gibt es keine Effektfassung.';
    };

    const renderStats = () => {
        const userCuts = Math.max(0, decisions.length - 1);
        cutCount.textContent = String(userCuts);
        cameraCount.textContent = String(new Set(decisions.map(item => item.camera)).size);
        cutTrack.replaceChildren();
        if (!audio.duration) return;
        decisions.slice(1).forEach(decision => {
            const marker = document.createElement('i');
            marker.className = 'regie-cut-marker';
            marker.style.left = `${Math.min(100, Math.max(0, decision.time / audio.duration * 100))}%`;
            marker.setAttribute('aria-label', `Schnitt bei ${formatTime(decision.time)}`);
            cutTrack.append(marker);
        });
    };

    const recordDecision = () => {
        if (replaying) return;
        const last = decisions.at(-1);
        if (last && last.camera === activeCamera && last.look === activeLook) return;
        decisions.push({ time: audio.currentTime || 0, camera: activeCamera, look: activeLook, label: cameraButton(activeCamera)?.dataset.label || activeCamera });
        renderStats();
    };

    const switchProgram = (cameraId, requestedLook = activeLook, record = true) => {
        const camera = cameraButton(cameraId);
        if (!camera) return;
        const candidate = camera.querySelector(`.regie-video[data-look="${requestedLook}"]`)
            || camera.querySelector('.regie-video[data-look="normal"]') || camera.querySelector('.regie-video');
        if (!candidate) return;
        activeCamera = cameraId;
        activeLook = candidate.dataset.look;
        if (programVideo.dataset.src !== candidate.dataset.src) {
            programVideo.dataset.src = candidate.dataset.src;
            programVideo.src = candidate.dataset.src;
            programVideo.load();
        }
        cameras.forEach(item => {
            const selected = item === camera;
            item.classList.toggle('is-active', selected);
            item.setAttribute('aria-pressed', String(selected));
        });
        refreshPreviews();
        updateLookControls();
        const lookLabel = candidate.dataset.lookLabel || 'Rohbild';
        programName.textContent = camera.dataset.label;
        liveCamera.textContent = camera.dataset.label;
        liveLook.textContent = lookLabel;
        cutAge.textContent = 'gerade geschnitten';
        if (record) recordDecision();
    };

    const synchronizeVideo = (video, threshold) => {
        if (video.readyState < HTMLMediaElement.HAVE_METADATA) return;
        const target = Math.min(audio.currentTime, Math.max(0, video.duration - .05));
        if (Math.abs(video.currentTime - target) > threshold) video.currentTime = target;
    };

    const synchronizeMedia = () => {
        synchronizeVideo(programVideo, .08);
        videos.forEach(video => synchronizeVideo(video, RESYNC_THRESHOLD));
    };

    const fallbackFromCompletedEffect = () => {
        if (activeLook !== 'effect' || programVideo.readyState < HTMLMediaElement.HAVE_METADATA) return;
        if (!Number.isFinite(programVideo.duration) || audio.currentTime < programVideo.duration - .1) return;
        switchProgram(activeCamera, 'normal', false);
        lookNote.textContent = 'Die Effektfassung endet hier – das Rohbild läuft synchron weiter.';
    };

    const keepSynchronized = () => {
        fallbackFromCompletedEffect();
        synchronizeMedia();
        if (!audio.paused && !audio.ended) syncFrame = requestAnimationFrame(keepSynchronized);
        else syncFrame = null;
    };

    const play = () => {
        synchronizeMedia();
        audio.play().catch(() => {});
        programVideo.play().catch(() => {});
        videos.forEach(video => video.play().catch(() => {}));
        if (!syncFrame) syncFrame = requestAnimationFrame(keepSynchronized);
    };

    const pause = () => {
        audio.pause();
        [...videos, programVideo].forEach(video => video.pause());
        if (syncFrame) cancelAnimationFrame(syncFrame);
        syncFrame = null;
    };

    const loadMedia = () => {
        if (started) return;
        started = true;
        playOverlay.hidden = true;
        audio.src = audio.dataset.src;
        switchProgram(activeCamera, activeLook, false);
        videos.forEach(video => {
            video.src = video.dataset.src;
            video.muted = true;
            video.load();
        });
        decisions = [{ time: 0, camera: activeCamera, look: activeLook, label: cameraButton(activeCamera)?.dataset.label || activeCamera }];
        renderStats();
    };

    const start = () => { loadMedia(); play(); };

    const showResult = () => {
        const ordered = [...decisions].sort((a, b) => a.time - b.time);
        const usage = new Map();
        let longest = 0;
        ordered.forEach((decision, index) => {
            usage.set(decision.label, (usage.get(decision.label) || 0) + 1);
            const end = ordered[index + 1]?.time ?? audio.duration;
            longest = Math.max(longest, end - decision.time);
        });
        const favorite = [...usage.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || '–';
        result.querySelector('[data-result-cuts]').textContent = String(Math.max(0, decisions.length - 1));
        result.querySelector('[data-result-favorite]').textContent = favorite;
        result.querySelector('[data-result-longest]').textContent = `${longest.toFixed(1)} s`;
        result.showModal();
    };

    const seekTo = time => {
        audio.currentTime = time;
        [...videos, programVideo].forEach(video => {
            if (video.readyState >= HTMLMediaElement.HAVE_METADATA) {
                video.currentTime = Math.min(time, Math.max(0, video.duration - .05));
            }
        });
    };

    playOverlay.addEventListener('click', start);
    toggle.addEventListener('click', () => { if (!started) start(); else if (audio.paused) play(); else pause(); });
    audio.addEventListener('play', () => {
        playIcon.hidden = true;
        pauseIcon.hidden = false;
        toggle.setAttribute('aria-pressed', 'true');
        toggle.setAttribute('aria-label', 'Pausieren');
        if (!syncFrame) syncFrame = requestAnimationFrame(keepSynchronized);
    });
    audio.addEventListener('pause', () => { playIcon.hidden = false; pauseIcon.hidden = true; toggle.setAttribute('aria-pressed', 'false'); toggle.setAttribute('aria-label', 'Abspielen'); });
    audio.addEventListener('loadedmetadata', () => { durationEl.textContent = formatTime(audio.duration); renderStats(); });
    audio.addEventListener('timeupdate', () => {
        elapsedEl.textContent = formatTime(audio.currentTime);
        if (audio.duration) {
            const percent = audio.currentTime / audio.duration * 100;
            progress.style.width = `${percent}%`;
            bar.setAttribute('aria-valuenow', String(Math.round(percent)));
        }
        synchronizeMedia();
        if (replaying) {
            while (replayIndex + 1 < decisions.length && decisions[replayIndex + 1].time <= audio.currentTime) {
                replayIndex += 1;
                const decision = decisions[replayIndex];
                switchProgram(decision.camera, decision.look, false);
            }
        }
        const lastCut = replaying ? decisions[replayIndex] : decisions.at(-1);
        if (lastCut) cutAge.textContent = `seit ${Math.max(0, audio.currentTime - lastCut.time).toFixed(1)} s`;
    });
    audio.addEventListener('ended', () => {
        pause();
        if (replaying) { replaying = false; task.textContent = 'Wiedergabe deiner Regie beendet.'; }
        else showResult();
    });
    videos.forEach(video => video.addEventListener('loadeddata', () => { video.dataset.ready = 'true'; }));
    programVideo.addEventListener('loadedmetadata', synchronizeMedia);
    programVideo.addEventListener('canplay', () => {
        synchronizeMedia();
        if (!audio.paused) programVideo.play().catch(() => {});
    });
    programVideo.addEventListener('ended', fallbackFromCompletedEffect);

    const seek = event => {
        loadMedia();
        if (!audio.duration) return;
        const rect = bar.getBoundingClientRect();
        seekTo(Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width)) * audio.duration);
    };
    bar.addEventListener('click', seek);
    bar.addEventListener('keydown', event => {
        if (!audio.duration || !['ArrowRight', 'ArrowLeft'].includes(event.key)) return;
        event.preventDefault();
        seekTo(Math.min(audio.duration, Math.max(0, audio.currentTime + (event.key === 'ArrowRight' ? 5 : -5))));
    });

    cameras.forEach(camera => camera.addEventListener('click', () => { if (!started) start(); switchProgram(camera.dataset.camera); }));
    lookButtons.forEach(button => button.addEventListener('click', () => {
        if (button.disabled) return;
        const requestedLook = button.dataset.look;
        const currentSupportsLook = videos.some(video => video.dataset.camera === activeCamera && video.dataset.look === requestedLook);
        const targetCamera = currentSupportsLook
            ? activeCamera
            : cameras.find(camera => camera.querySelector(`.regie-video[data-look="${requestedLook}"]`))?.dataset.camera;
        if (!targetCamera) return;
        const shouldStart = !started;
        if (shouldStart) loadMedia();
        switchProgram(targetCamera, requestedLook);
        if (shouldStart) play();
    }));
    document.addEventListener('keydown', event => {
        const focused = document.activeElement;
        if (['INPUT', 'TEXTAREA', 'SELECT'].includes(focused?.tagName) || focused?.isContentEditable) return;
        const index = Number(event.key) - 1;
        if (index >= 0 && index < cameras.length) { event.preventDefault(); if (!started) start(); switchProgram(cameras[index].dataset.camera); }
        else if (event.code === 'Space' && focused?.tagName !== 'BUTTON') { event.preventDefault(); if (!started) start(); else if (audio.paused) play(); else pause(); }
    });

    result.querySelector('.regie-result-close').addEventListener('click', () => result.close());
    result.querySelector('.regie-replay').addEventListener('click', () => {
        result.close(); replaying = true; replayIndex = 0;
        decisions = [...decisions].sort((a, b) => a.time - b.time);
        seekTo(0);
        const first = decisions[0]; switchProgram(first.camera, first.look, false); play();
        task.textContent = 'Deine Schnittentscheidungen werden jetzt wiedergegeben.';
    });
    result.querySelector('.regie-restart').addEventListener('click', () => {
        result.close(); replaying = false; seekTo(0); decisions = [];
        switchProgram(cameras[0].dataset.camera, 'normal', false);
        decisions.push({ time: 0, camera: activeCamera, look: activeLook, label: cameraButton(activeCamera).dataset.label });
        renderStats(); play();
    });

    switchProgram(activeCamera, 'normal', false);
}
