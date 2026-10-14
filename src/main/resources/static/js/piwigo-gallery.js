const lightbox = document.querySelector('#photo-lightbox');

if (lightbox) {
    const image = lightbox.querySelector('img');
    const stage = lightbox.querySelector('.photo-lightbox-stage');
    const loading = lightbox.querySelector('.photo-lightbox-loading');
    const error = lightbox.querySelector('.photo-lightbox-error');
    const caption = lightbox.querySelector('.photo-lightbox-caption');
    const close = lightbox.querySelector('.photo-lightbox-close');
    let requestId = 0;
    let group = [];
    let position = -1;

    // Tiles that share a data-group form a fixed sequence the lightbox can step through.
    const nav = document.createElement('div');
    nav.className = 'photo-lightbox-nav';
    nav.hidden = true;
    nav.innerHTML = '<button type="button" class="photo-lightbox-prev" aria-label="Vorheriges Foto">←</button>'
        + '<p class="photo-lightbox-count" aria-live="polite"></p>'
        + '<button type="button" class="photo-lightbox-next" aria-label="Nächstes Foto">→</button>';
    lightbox.append(nav);
    const prev = nav.querySelector('.photo-lightbox-prev');
    const next = nav.querySelector('.photo-lightbox-next');
    const count = nav.querySelector('.photo-lightbox-count');

    function show(tile) {
        if (!tile.dataset.full) return;

        const currentRequest = ++requestId;
        const title = tile.dataset.title || '';
        image.hidden = true;
        image.removeAttribute('src');
        image.alt = '';
        error.hidden = true;
        loading.hidden = false;
        stage.setAttribute('aria-busy', 'true');
        caption.textContent = '';
        if (tile.dataset.href && title) {
            const link = document.createElement('a');
            link.href = tile.dataset.href;
            link.textContent = `${title} ansehen →`;
            caption.append(link);
        } else {
            caption.textContent = title;
        }

        nav.hidden = group.length < 2;
        count.textContent = group.length > 1 ? `${position + 1} / ${group.length}` : '';
        prev.disabled = position <= 0;
        next.disabled = position >= group.length - 1;

        if (!lightbox.open) lightbox.showModal();

        const pendingImage = new Image();
        pendingImage.decoding = 'async';
        pendingImage.addEventListener('load', () => {
            if (currentRequest !== requestId) return;
            image.src = pendingImage.src;
            image.alt = tile.dataset.alt || (title ? `Foto aus dem Album ${title}` : 'Galeriefoto');
            image.hidden = false;
            loading.hidden = true;
            stage.setAttribute('aria-busy', 'false');
        });
        pendingImage.addEventListener('error', () => {
            if (currentRequest !== requestId) return;
            loading.hidden = true;
            error.hidden = false;
            stage.setAttribute('aria-busy', 'false');
        });
        pendingImage.src = tile.dataset.full;
    }

    function step(offset) {
        const target = position + offset;
        if (target < 0 || target >= group.length) return;
        position = target;
        show(group[position]);
    }

    document.querySelectorAll('.photo-tile[data-full]').forEach(tile => tile.addEventListener('click', () => {
        const name = tile.dataset.group;
        group = name ? [...document.querySelectorAll(`.photo-tile[data-group="${name}"]`)] : [];
        position = group.indexOf(tile);
        show(tile);
    }));
    prev.addEventListener('click', () => step(-1));
    next.addEventListener('click', () => step(1));
    lightbox.addEventListener('keydown', event => {
        if (event.key === 'ArrowLeft') { event.preventDefault(); step(-1); }
        if (event.key === 'ArrowRight') { event.preventDefault(); step(1); }
    });
    close?.addEventListener('click', () => lightbox.close());
    lightbox.addEventListener('click', event => {
        if (event.target === lightbox) lightbox.close();
    });
    lightbox.addEventListener('close', () => {
        requestId++;
        image.hidden = true;
        image.removeAttribute('src');
        loading.hidden = true;
        error.hidden = true;
        stage.setAttribute('aria-busy', 'false');
    });
}
