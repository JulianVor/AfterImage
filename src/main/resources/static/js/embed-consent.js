// Third-party embeds (YouTube, Google Maps) stay unloaded until the visitor clicks, so no data is sent
// to the provider before then.
document.querySelectorAll('.embed-consent').forEach(box => {
    const button = box.querySelector('button');
    if (!button) return;

    button.addEventListener('click', () => {
        const frame = document.createElement('iframe');
        frame.src = box.dataset.src;
        frame.title = box.dataset.title || '';
        frame.referrerPolicy = box.dataset.referrer || 'no-referrer';
        if (box.dataset.allow) frame.allow = box.dataset.allow;
        if (box.classList.contains('embed-consent--video')) frame.allowFullscreen = true;
        frame.loading = 'lazy';
        box.replaceWith(frame);
    });
});
