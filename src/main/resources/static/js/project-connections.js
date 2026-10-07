const root = document.querySelector('.project-connections');

if (root) {
    const SVG_NS = 'http://www.w3.org/2000/svg';
    const slug = root.dataset.slug;
    const svg = root.querySelector('.connections-canvas');
    const legend = root.querySelector('.connections-legend');
    const include = root.dataset.include ? new Set(root.dataset.include.split(',')) : null;

    const TYPES = {
        BAND: ['Band', 'Bands'],
        PROJECT: ['Projekt', 'Projekte'],
        EVENT: ['Veranstaltung', 'Veranstaltungen'],
        PLACE: ['Ort', 'Orte'],
        PERSON: ['Person', 'Personen'],
    };
    const TYPE_ORDER = ['BAND', 'PROJECT', 'EVENT', 'PLACE', 'PERSON'];
    const MAX_DIAGRAM_NODES = 16;
    const LEGEND_VISIBLE = 8;

    // [reads from the source entity's side, reads from the target entity's side]
    const RELATIONS = {
        FEATURES: ['mit', 'tritt auf in'],
        FEATURES_PERSON: ['mit', 'tritt auf in'],
        PRODUCED_BY: ['produziert von', 'produziert'],
        DIRECTED_BY: ['Regie von', 'führte Regie bei'],
        SHOT_BY: ['gedreht von', 'drehte'],
        EDITED_BY: ['Schnitt von', 'schnitt'],
        COLORED_BY: ['Color Grading von', 'machte das Color Grading für'],
        WRITTEN_BY: ['geschrieben von', 'schrieb'],
        MEMBER_OF: ['Mitglied von', 'Mitglied'],
        FORMER_MEMBER_OF: ['ehemals Mitglied von', 'ehemaliges Mitglied'],
        ASSOCIATED_WITH: ['verbunden mit', 'verbunden mit'],
        PERFORMED_AT: ['spielte bei', 'Auftritt von'],
        HEADLINED: ['Headliner bei', 'Headliner'],
        ORGANIZED_BY: ['organisiert von', 'organisiert'],
        FOUNDED_BY: ['gegründet von', 'gründete'],
        HELD_AT: ['fand statt in', 'Schauplatz von'],
        RECORDED_AT_EVENT: ['aufgenommen bei', 'Aufnahme von'],
        EDITION_OF: ['Ausgabe von', 'Ausgabe'],
        PARTICIPATED_IN: ['nahm teil an', 'Teilnehmer'],
        SHOT_AT: ['gedreht in', 'Drehort von'],
        RECORDED_AT: ['aufgenommen in', 'Aufnahmeort von'],
        PLANNED_FOR: ['geplant für', 'geplant für'],
        PART_OF: ['Teil von', 'enthält'],
        PREQUEL_TO: ['Vorgeschichte zu', 'Fortsetzung'],
        SEQUEL_TO: ['Fortsetzung von', 'Vorgeschichte'],
        REUSES_MATERIAL_FROM: ['nutzt Material von', 'Material genutzt von'],
        PORTRAYS: ['porträtiert', 'porträtiert von'],
        RELATED_TO: ['verwandt', 'verwandt'],
        REFERENCES: ['verweist auf', 'verwiesen von'],
        BORN_IN: ['geboren in', 'Geburtsort von'],
    };

    const el = (name, attributes = {}, text) => {
        const node = document.createElementNS(SVG_NS, name);
        Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, value));
        if (text !== undefined) node.textContent = text;
        return node;
    };
    const html = (name, className, text) => {
        const node = document.createElement(name);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    };
    const shorten = (text, max = 30) => text.length > max ? text.slice(0, max - 1) + '…' : text;
    const typeKey = node => TYPES[node.entityType] ? node.entityType : 'OTHER';

    fetch('/api/explore/neighborhood?focus=' + encodeURIComponent('entity:' + slug))
        .then(response => response.ok ? response.json() : Promise.reject(new Error('request failed')))
        .then(render)
        .catch(() => { root.hidden = true; });

    function render(data) {
        const nodes = data.nodes || [];
        const focus = nodes.find(node => node.slug === data.focus);
        const linkByNeighbor = new Map();
        (data.links || []).forEach(link => {
            const neighbor = link.source === data.focus ? link.target : link.source;
            const [forward, reverse] = RELATIONS[link.type] || [link.label, link.label];
            linkByNeighbor.set(neighbor, {
                label: link.source === data.focus ? forward : reverse,
                strength: link.strength,
            });
        });

        const neighbors = nodes
            .filter(node => node.slug !== data.focus && (!include || include.has(node.slug)))
            .sort((a, b) => {
                const order = TYPE_ORDER.indexOf(typeKey(a)) - TYPE_ORDER.indexOf(typeKey(b));
                return order || a.title.localeCompare(b.title, 'de');
            });

        if (!focus || neighbors.length < 1) {
            root.hidden = true;
            return;
        }

        if (neighbors.length >= 2) {
            const shown = pickForDiagram(neighbors, linkByNeighbor);
            drawDiagram(focus, shown, linkByNeighbor);
            drawNote(shown.length, neighbors.length);
        } else {
            svg.remove();
            legend.classList.add('connections-legend--alone');
        }
        drawLegend(neighbors, linkByNeighbor);
        root.hidden = false;
    }

    // A hub like a band with 60 connections would turn the ring into an unreadable label pile, so
    // the diagram shows the strongest few, mixed evenly across entity types; the legend lists all.
    function pickForDiagram(neighbors, linkByNeighbor) {
        if (neighbors.length <= MAX_DIAGRAM_NODES) return neighbors;
        const queues = new Map();
        neighbors.forEach(node => {
            const key = typeKey(node);
            if (!queues.has(key)) queues.set(key, []);
            queues.get(key).push(node);
        });
        queues.forEach(queue => queue.sort((a, b) =>
            (linkByNeighbor.get(b.slug)?.strength || 0) - (linkByNeighbor.get(a.slug)?.strength || 0)));
        const picked = [];
        while (picked.length < MAX_DIAGRAM_NODES && [...queues.values()].some(queue => queue.length)) {
            for (const queue of queues.values()) {
                if (queue.length && picked.length < MAX_DIAGRAM_NODES) picked.push(queue.shift());
            }
        }
        return neighbors.filter(node => picked.includes(node));
    }

    function drawNote(shownCount, totalCount) {
        root.querySelector('.connections-note')?.remove();
        if (shownCount >= totalCount) return;
        svg.after(html('p', 'connections-note',
            `Im Diagramm: die ${shownCount} stärksten von ${totalCount} Verbindungen. Alle stehen unten in der Liste.`));
    }

    function drawDiagram(focus, neighbors, linkByNeighbor) {
        const count = neighbors.length;
        const WIDTH = 1200;
        const HEIGHT = count <= 6 ? 460 : count <= 12 ? 580 : 700;
        const cx = WIDTH / 2;
        const cy = HEIGHT / 2;
        const rx = WIDTH * 0.27;
        const ry = HEIGHT * 0.34;

        svg.setAttribute('viewBox', `0 0 ${WIDTH} ${HEIGHT}`);
        svg.replaceChildren();

        const items = el('g');
        neighbors.forEach((node, index) => {
            const angle = -Math.PI / 2 + (2 * Math.PI * (index + 0.5)) / count;
            const x = cx + Math.cos(angle) * rx;
            const y = cy + Math.sin(angle) * ry;
            const side = Math.cos(angle);
            const info = linkByNeighbor.get(node.slug);

            const group = el('g', { class: `connection-item connection-item--${typeKey(node).toLowerCase()}` });
            group.append(el('line', {
                class: 'connection-edge', x1: cx, y1: cy, x2: x, y2: y,
                'stroke-width': 1 + (info?.strength || 50) / 100,
            }));
            group.append(marker(typeKey(node), x, y));

            const nearVertical = Math.abs(side) < 0.1;
            const anchor = nearVertical ? 'middle' : side > 0 ? 'start' : 'end';
            const labelX = nearVertical ? x : x + (side > 0 ? 18 : -18);
            const labelY = nearVertical ? y + (Math.sin(angle) < 0 ? -26 : 30) : y - 2;
            const label = el('text', { class: 'connection-label', x: labelX, y: labelY, 'text-anchor': anchor });
            label.append(el('tspan', { class: 'connection-title', x: labelX }, shorten(node.title)));
            if (info) label.append(el('tspan', { class: 'connection-role', x: labelX, dy: 15 }, info.label));
            group.append(label);
            group.append(el('title', {}, info ? `${node.title} – ${info.label}` : node.title));

            if (node.url && !node.ghost) {
                const link = el('a', { href: node.url, class: 'connection-link' });
                link.setAttribute('aria-label', info ? `${node.title}, ${info.label}` : node.title);
                link.append(group);
                items.append(link);
            } else {
                items.append(group);
            }
        });
        svg.append(items);

        const center = el('g', { class: 'connection-focus' });
        const plate = el('rect', { class: 'connection-focus-plate', rx: 2 });
        const title = el('text', { x: cx, y: cy + 4, 'text-anchor': 'middle' }, shorten(focus.title, 36));
        center.append(plate, title);
        svg.append(center);
        const plateWidth = title.textContent.length * 10.4 + 32;
        plate.setAttribute('x', cx - plateWidth / 2);
        plate.setAttribute('y', cy - 20);
        plate.setAttribute('width', plateWidth);
        plate.setAttribute('height', 38);
    }

    function marker(type, x, y) {
        const className = `connection-marker connection-marker--${type.toLowerCase()}`;
        if (type === 'PROJECT') return el('rect', { class: className, x: x - 7, y: y - 7, width: 14, height: 14 });
        if (type === 'EVENT') return el('polygon', { class: className, points: `${x},${y - 9} ${x + 9},${y} ${x},${y + 9} ${x - 9},${y}` });
        if (type === 'PLACE') return el('polygon', { class: className, points: `${x},${y - 9} ${x + 8},${y + 6} ${x - 8},${y + 6}` });
        return el('circle', { class: className, cx: x, cy: y, r: 7 });
    }

    function drawLegend(neighbors, linkByNeighbor) {
        legend.replaceChildren();
        const groups = new Map();
        neighbors.forEach(node => {
            const key = typeKey(node);
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(node);
        });
        TYPE_ORDER.concat('OTHER').forEach(key => {
            const members = groups.get(key);
            if (!members) return;
            const [singular, plural] = TYPES[key] || ['Weitere', 'Weitere'];
            const section = html('section', `connections-group connections-group--${key.toLowerCase()}`);
            section.append(html('h3', '', members.length === 1 ? singular : plural));
            const entry = node => {
                const item = html('li');
                const target = node.url && !node.ghost ? html('a', '', node.title) : html('span', '', node.title);
                if (target.tagName === 'A') target.href = node.url;
                item.append(target);
                const info = linkByNeighbor.get(node.slug);
                if (info) item.append(html('small', '', info.label));
                return item;
            };
            const list = html('ul');
            members.slice(0, LEGEND_VISIBLE).forEach(node => list.append(entry(node)));
            section.append(list);
            if (members.length > LEGEND_VISIBLE) {
                const more = html('details', 'connections-more');
                more.append(html('summary', '', `${members.length - LEGEND_VISIBLE} weitere anzeigen`));
                const rest = html('ul');
                members.slice(LEGEND_VISIBLE).forEach(node => rest.append(entry(node)));
                more.append(rest);
                section.append(more);
            }
            legend.append(section);
        });
    }
}
