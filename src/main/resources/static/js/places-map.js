const mapEl = document.querySelector('#places-map');
const emptyState = document.querySelector('.places-map-empty');

if (mapEl && window.L) {
    fetch('/api/explore/places')
        .then(response => response.ok ? response.json() : Promise.reject(new Error('request failed')))
        .then(render)
        .catch(() => { if (emptyState) emptyState.hidden = false; });

    function render(places) {
        if (!places || places.length === 0) {
            if (emptyState) emptyState.hidden = false;
            return;
        }

        const map = L.map(mapEl, { scrollWheelZoom: false });
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
            maxZoom: 18
        }).addTo(map);

        const total = place => place.concertCount + place.projectCount;
        const maxTotal = Math.max(1, ...places.map(total));
        const markerColor = place => place.concertCount > 0 && place.projectCount > 0 ? '#7a4a8f'
            : place.projectCount > 0 ? '#2f6f9e'
            : '#c8431c';
        places.forEach(place => {
            const radius = 6 + (total(place) / maxTotal) * 10;
            const color = markerColor(place);
            const marker = L.circleMarker([place.lat, place.lng], {
                radius,
                weight: 1.5,
                color,
                fillColor: color,
                fillOpacity: 0.75
            }).addTo(map);
            const parts = [];
            if (place.concertCount > 0) parts.push(place.concertCount === 1 ? '1 Konzert' : `${place.concertCount} Konzerte`);
            if (place.projectCount > 0) parts.push(place.projectCount === 1 ? '1 Projekt' : `${place.projectCount} Projekte`);
            const count = parts.length > 0 ? parts.join(', ') : 'Kein dokumentiertes Werk';
            marker.bindPopup(`<strong>${place.title}</strong><span>${count}</span><a href="/explore?focus=${encodeURIComponent('entity:' + place.slug)}">Bei Entdecken öffnen →</a>`);
        });

        const bounds = L.latLngBounds(places.map(place => [place.lat, place.lng]));
        map.fitBounds(bounds, { padding: [40, 40], maxZoom: 12 });

        // Scroll-to-zoom is off by default so scrolling the page doesn't get trapped by the map;
        // only enable it once the pointer is actually resting on the map.
        mapEl.addEventListener('mouseenter', () => map.scrollWheelZoom.enable());
        mapEl.addEventListener('mouseleave', () => map.scrollWheelZoom.disable());
    }
}
