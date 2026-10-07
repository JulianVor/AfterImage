const root = document.querySelector('.slideshow');

if (root) {
    const slides = [...root.querySelectorAll('.slide')];
    const stage = root.querySelector('.slideshow-stage');
    const current = root.querySelector('.slideshow-current');
    const toggle = root.querySelector('.slideshow-toggle');
    const INTERVAL = 5500;

    let index = 0;
    let playing = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let hovering = false;
    let timer = null;

    const show = (next) => {
        index = (next + slides.length) % slides.length;
        slides.forEach((slide, i) => slide.classList.toggle('is-active', i === index));
        current.textContent = String(index + 1).padStart(2, '0');
        const upcoming = slides[(index + 1) % slides.length];
        upcoming.loading = 'eager';
    };

    const schedule = () => {
        clearInterval(timer);
        timer = null;
        if (playing && !hovering && !document.hidden) {
            timer = setInterval(() => show(index + 1), INTERVAL);
        }
    };

    const setPlaying = (value) => {
        playing = value;
        toggle.textContent = playing ? 'Pause' : 'Abspielen';
        toggle.setAttribute('aria-pressed', String(playing));
        schedule();
    };

    const step = (delta) => {
        show(index + delta);
        schedule();
    };

    root.querySelector('.slideshow-prev').addEventListener('click', () => step(-1));
    root.querySelector('.slideshow-next').addEventListener('click', () => step(1));
    toggle.addEventListener('click', () => setPlaying(!playing));

    stage.addEventListener('mouseenter', () => { hovering = true; schedule(); });
    stage.addEventListener('mouseleave', () => { hovering = false; schedule(); });
    document.addEventListener('visibilitychange', schedule);

    root.addEventListener('keydown', (event) => {
        if (event.key === 'ArrowLeft') step(-1);
        if (event.key === 'ArrowRight') step(1);
    });

    let swipeStart = null;
    stage.addEventListener('pointerdown', (event) => { swipeStart = event.clientX; });
    stage.addEventListener('pointerup', (event) => {
        if (swipeStart === null) return;
        const delta = event.clientX - swipeStart;
        swipeStart = null;
        if (Math.abs(delta) > 40) step(delta < 0 ? 1 : -1);
    });

    show(0);
    setPlaying(playing);
}
