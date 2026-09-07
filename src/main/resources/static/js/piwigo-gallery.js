const lightbox = document.querySelector('#photo-lightbox');

if (lightbox) {
    const image = lightbox.querySelector('img');
    const stage = lightbox.querySelector('.photo-lightbox-stage');
    const loading = lightbox.querySelector('.photo-lightbox-loading');
    const error = lightbox.querySelector('.photo-lightbox-error');
    const caption = lightbox.querySelector('.photo-lightbox-caption');
    const close = lightbox.querySelector('.photo-lightbox-close');
    let requestId = 0;

    document.querySelectorAll('.photo-tile[data-full]').forEach(tile => tile.addEventListener('click', () => {
        if (!tile.dataset.full) return;

        const currentRequest = ++requestId;
        const title = tile.dataset.title || '';
        image.hidden = true;
        image.removeAttribute('src');
        image.alt = '';
        error.hidden = true;
        loading.hidden = false;
        stage.setAttribute('aria-busy', 'true');
        caption.textContent = title;

        if (!lightbox.open) lightbox.showModal();

        const pendingImage = new Image();
        pendingImage.decoding = 'async';
        pendingImage.addEventListener('load', () => {
            if (currentRequest !== requestId) return;
            image.src = pendingImage.src;
            image.alt = title ? `Foto aus dem Album ${title}` : 'Galeriefoto';
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
    }));
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
