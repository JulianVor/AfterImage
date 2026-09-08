const player = document.querySelector('.track-player');

if (player) {
    const audio = player.querySelector('.track-player-audio');
    const toggle = player.querySelector('.track-player-toggle');
    const playIcon = player.querySelector('.icon-play');
    const pauseIcon = player.querySelector('.icon-pause');
    const titleEl = player.querySelector('.track-player-title');
    const elapsedEl = player.querySelector('.track-player-elapsed');
    const durationEl = player.querySelector('.track-player-duration');
    const bar = player.querySelector('.track-player-bar');
    const progress = player.querySelector('.track-player-progress');
    const items = [...player.querySelectorAll('.track-player-item')];

    const formatTime = (seconds) => {
        if (!Number.isFinite(seconds)) return '–:--';
        const minutes = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60).toString().padStart(2, '0');
        return `${minutes}:${secs}`;
    };

    const load = (src, title, autoplay) => {
        audio.src = src;
        titleEl.textContent = title;
        items.forEach(item => item.classList.toggle('is-active', item.dataset.src === src));
        progress.style.width = '0%';
        bar.setAttribute('aria-valuenow', '0');
        elapsedEl.textContent = '0:00';
        durationEl.textContent = '–:--';
        if (autoplay) audio.play();
    };

    load(player.dataset.src, player.dataset.title, false);

    toggle.addEventListener('click', () => {
        if (audio.paused) audio.play(); else audio.pause();
    });

    audio.addEventListener('play', () => {
        playIcon.hidden = true;
        pauseIcon.hidden = false;
        toggle.setAttribute('aria-pressed', 'true');
    });
    audio.addEventListener('pause', () => {
        playIcon.hidden = false;
        pauseIcon.hidden = true;
        toggle.setAttribute('aria-pressed', 'false');
    });
    audio.addEventListener('loadedmetadata', () => {
        durationEl.textContent = formatTime(audio.duration);
    });
    audio.addEventListener('timeupdate', () => {
        elapsedEl.textContent = formatTime(audio.currentTime);
        if (audio.duration) {
            const percent = (audio.currentTime / audio.duration) * 100;
            progress.style.width = percent + '%';
            bar.setAttribute('aria-valuenow', Math.round(percent).toString());
        }
    });
    audio.addEventListener('ended', () => {
        playIcon.hidden = false;
        pauseIcon.hidden = true;
    });

    const seek = (event) => {
        if (!audio.duration) return;
        const rect = bar.getBoundingClientRect();
        const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
        audio.currentTime = ratio * audio.duration;
    };
    bar.addEventListener('click', seek);
    bar.addEventListener('keydown', (event) => {
        if (!audio.duration) return;
        if (event.key === 'ArrowRight') audio.currentTime = Math.min(audio.duration, audio.currentTime + 5);
        if (event.key === 'ArrowLeft') audio.currentTime = Math.max(0, audio.currentTime - 5);
    });

    items.forEach(item => item.addEventListener('click', () => {
        load(item.dataset.src, item.dataset.title, true);
    }));
}
