const mapEl = document.querySelector('.project-places-map');

if (mapEl && window.L) {
    const places = [...document.querySelectorAll('.project-places-list li[data-lat][data-lon]')]
        .map(item => ({
            lat: Number(item.dataset.lat),
            lon: Number(item.dataset.lon),
            title: item.dataset.title,
            url: item.dataset.url,
        }))
        .filter(place => Number.isFinite(place.lat) && Number.isFinite(place.lon));

    if (places.length === 0) {
        mapEl.remove();
    } else {
        const map = L.map(mapEl, { scrollWheelZoom: false });
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
            maxZoom: 18,
        }).addTo(map);

        places.forEach(place => {
            const marker = L.circleMarker([place.lat, place.lon], {
                radius: 9, weight: 1.5, color: '#8f2c10', fillColor: '#c8431c', fillOpacity: 0.8,
            }).addTo(map);
            const content = document.createElement('div');
            const title = document.createElement('strong');
            title.textContent = place.title;
            const link = document.createElement('a');
            link.href = place.url;
            link.textContent = 'Ort im Archiv öffnen →';
            content.append(title, link);
            marker.bindPopup(content);
        });

        if (places.length === 1) {
            map.setView([places[0].lat, places[0].lon], 14);
        } else {
            map.fitBounds(L.latLngBounds(places.map(place => [place.lat, place.lon])), { padding: [40, 40], maxZoom: 14 });
        }

        mapEl.addEventListener('mouseenter', () => map.scrollWheelZoom.enable());
        mapEl.addEventListener('mouseleave', () => map.scrollWheelZoom.disable());
    }
}
