// Masonry that keeps the reading order: each next photo goes into the currently shortest column,
// so the sequence runs roughly left to right instead of column by column. Without this script
// the CSS columns fall back to the plain column order.
document.querySelectorAll('.photo-masonry[data-ordered]').forEach(root => {
    const tiles = [...root.children].filter(element => element.matches('.photo-tile'));
    let current = 0;

    function ratio(tile) {
        const image = tile.querySelector('img');
        const width = Number(image?.getAttribute('width'));
        const height = Number(image?.getAttribute('height'));
        return width > 0 && height > 0 ? height / width : 1;
    }

    function layout() {
        const count = parseInt(getComputedStyle(root).getPropertyValue('--cols'), 10) || 3;
        if (count === current) return;
        current = count;

        const columns = Array.from({ length: count }, () => {
            const column = document.createElement('div');
            column.className = 'photo-masonry-col';
            return column;
        });
        const heights = new Array(count).fill(0);
        tiles.forEach(tile => {
            const target = heights.indexOf(Math.min(...heights));
            columns[target].append(tile);
            heights[target] += ratio(tile);
        });
        root.classList.add('is-ordered');
        root.replaceChildren(...columns);
    }

    layout();
    let timer;
    window.addEventListener('resize', () => {
        clearTimeout(timer);
        timer = setTimeout(layout, 120);
    });
});
