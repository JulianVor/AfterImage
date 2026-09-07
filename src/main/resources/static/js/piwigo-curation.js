const list = document.querySelector('[data-curated-list]');

if (list) {
    let dragged = null;
    const refreshRanks = () => [...list.children].forEach((item, index) => {
        item.querySelector('.curation-rank').textContent = index + 1;
    });

    list.addEventListener('dragstart', event => {
        dragged = event.target.closest('li');
        dragged?.classList.add('is-dragging');
    });
    list.addEventListener('dragend', () => {
        dragged?.classList.remove('is-dragging');
        dragged = null;
        refreshRanks();
    });
    list.addEventListener('dragover', event => {
        event.preventDefault();
        const target = event.target.closest('li');
        if (!dragged || !target || target === dragged) return;
        const box = target.getBoundingClientRect();
        list.insertBefore(dragged, event.clientX < box.left + box.width / 2 ? target : target.nextSibling);
    });
    list.addEventListener('click', event => {
        const button = event.target.closest('[data-move]');
        if (!button) return;
        const item = button.closest('li');
        if (button.dataset.move === 'up' && item.previousElementSibling) list.insertBefore(item, item.previousElementSibling);
        if (button.dataset.move === 'down' && item.nextElementSibling) list.insertBefore(item.nextElementSibling, item);
        refreshRanks();
    });
}
