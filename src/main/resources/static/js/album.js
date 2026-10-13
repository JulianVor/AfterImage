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
}
