const root = document.querySelector('.connections');
const svgElement = document.querySelector('#memory-canvas');

if (root && svgElement && window.d3) {
    const d3 = window.d3;
    const WIDTH = 1400;
    const HEIGHT = 720;
    const CENTER = { x: WIDTH / 2, y: HEIGHT / 2 };
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const clusterOrder = new Map([
        'members', 'people', 'music-videos', 'projects', 'bands', 'events', 'places', 'objects', 'moments', 'other'
    ].map((type, index) => [type, index]));
    const ui = {
        summary: document.querySelector('#thread-summary'),
        status: document.querySelector('#network-status'),
        focusType: document.querySelector('#focus-type'),
        focusTitle: document.querySelector('#focus-title'),
        focusMeta: document.querySelector('#focus-meta'),
        focusDetail: document.querySelector('#focus-detail'),
        focusPortrait: document.querySelector('#focus-portrait'),
        focusPortraitImg: document.querySelector('#focus-portrait-img'),
        relationCount: document.querySelector('#relation-count'),
        relationList: document.querySelector('#relation-list'),
        clusterBack: document.querySelector('#cluster-back'),
        viewReset: document.querySelector('#view-reset')
    };
    let graph = null;
    let requestNumber = 0;
    let expandedCluster = null;
    let currentLayout = null;
    let currentZoom = null;
    let previousPositions = new Map();

    const relationClass = type => {
        if (type === 'REUSES_MATERIAL_FROM') return 'reuse';
        if (type === 'PREQUEL_TO' || type === 'SEQUEL_TO' || type === 'EDITION_OF') return 'chronology';
        if (type === 'RELATED_TO' || type === 'REFERENCES' || type === 'ASSOCIATED_WITH') return 'concept';
        return 'production';
    };

    const translations = {
        PROJECT: 'Projekt', PERSON: 'Person', BAND: 'Band', PLACE: 'Ort', EVENT: 'Ereignis',
        OBJECT: 'Objekt', MOMENT: 'Moment', MUSIC_VIDEO: 'Musikvideo', LIVE_VIDEO: 'Livevideo',
        PHOTO_SERIES: 'Fotoserie', DESIGN_PROJECT: 'Designprojekt', FESTIVAL_IDENTITY: 'Festival-Identität',
        FEATURES: 'Mit Band', FEATURES_PERSON: 'Mitwirkende Person', PRODUCED_BY: 'Produziert von',
        DIRECTED_BY: 'Regie von', SHOT_BY: 'Kamera von', EDITED_BY: 'Schnitt von',
        COLORED_BY: 'Farbkorrektur von', WRITTEN_BY: 'Geschrieben von', MEMBER_OF: 'Mitglied von',
        FORMER_MEMBER_OF: 'Ehemaliges Mitglied von', ASSOCIATED_WITH: 'Verbunden mit',
        PERFORMED_AT: 'Aufgetreten bei', HEADLINED: 'Headliner bei', ORGANIZED_BY: 'Organisiert von',
        FOUNDED_BY: 'Gegründet von', HELD_AT: 'Veranstaltet in', RECORDED_AT_EVENT: 'Aufgenommen bei',
        EDITION_OF: 'Ausgabe von', PARTICIPATED_IN: 'Teilgenommen an', SHOT_AT: 'Gedreht in',
        RECORDED_AT: 'Aufgenommen in', PLANNED_FOR: 'Geplant für', PART_OF: 'Teil von',
        PREQUEL_TO: 'Vorgänger von', SEQUEL_TO: 'Fortsetzung von',
        REUSES_MATERIAL_FROM: 'Verwendet Material von', PORTRAYS: 'Porträtiert',
        RELATED_TO: 'Verwandt mit', REFERENCES: 'Verweist auf', BORN_IN: 'Geboren in'
    };
    const label = value => translations[value] || (value || '')
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/(^|\s)\S/g, character => character.toUpperCase());

    const otherSlug = link => link.source === graph.focus ? link.target : link.source;

    function clusterFor(node) {
        const focus = graph.nodes.find(candidate => candidate.slug === graph.focus);
        const relationTypes = new Set(graph.links
            .filter(link => otherSlug(link) === node.slug)
            .map(link => link.type));
        const isMember = relationTypes.has('MEMBER_OF') || relationTypes.has('FORMER_MEMBER_OF');

        if (node.entityType === 'PERSON') {
            if (focus?.entityType === 'BAND') {
                return isMember
                    ? { key: 'members', label: 'Bandmitglieder' }
                    : { key: 'people', label: 'Menschen rund um die Band' };
            }
            if (focus?.entityType === 'PROJECT') return { key: 'people', label: 'Besetzung und Team' };
            if (focus?.entityType === 'EVENT') return { key: 'people', label: 'Mitwirkende und Organisation' };
            return { key: isMember ? 'members' : 'people', label: isMember ? 'Bandmitglieder' : 'Menschen und Rollen' };
        }
        if (node.entityType === 'PROJECT' && (node.subtype || '').includes('MUSIC_VIDEO')) {
            return { key: 'music-videos', label: 'Musikvideos' };
        }
        if (node.entityType === 'PROJECT') return { key: 'projects', label: 'Werke und Projekte' };
        if (node.entityType === 'BAND') return { key: 'bands', label: 'Verbundene Bands' };
        if (node.entityType === 'EVENT') return { key: 'events', label: 'Ereignisse' };
        if (node.entityType === 'PLACE') return { key: 'places', label: 'Orte' };
        if (node.entityType === 'OBJECT') return { key: 'objects', label: 'Objekte' };
        if (node.entityType === 'MOMENT') return { key: 'moments', label: 'Momente' };
        return { key: 'other', label: 'Weitere Verbindungen' };
    }

    function groupedNeighbors(nodes) {
        const groups = new Map();
        nodes.filter(node => node.slug !== graph.focus).forEach(node => {
            const cluster = clusterFor(node);
            if (!groups.has(cluster.key)) groups.set(cluster.key, { ...cluster, nodes: [] });
            groups.get(cluster.key).nodes.push({ ...node, clusterKey: cluster.key, clusterLabel: cluster.label });
        });
        return [...groups.values()]
            .sort((left, right) => (clusterOrder.get(left.key) ?? 99) - (clusterOrder.get(right.key) ?? 99))
            .map(group => ({
                ...group,
                totalCount: group.nodes.length,
                nodes: group.nodes.sort((left, right) => left.title.localeCompare(right.title))
            }));
    }

    function overviewGroups(nodes) {
        const groups = groupedNeighbors(nodes);
        const visiblePerGroup = groups.length <= 3 ? 4 : groups.length === 4 ? 3 : 2;
        return groups.map(group => {
            const visible = group.nodes.slice(0, visiblePerGroup);
            const hiddenCount = group.nodes.length - visible.length;
            if (hiddenCount > 0) {
                visible.push({
                    slug: `__more:${group.key}`,
                    title: `+${hiddenCount} weitere`,
                    entityType: 'CLUSTER',
                    clusterKey: group.key,
                    clusterLabel: group.label,
                    hiddenCount,
                    more: true,
                    ghost: false
                });
            }
            return { ...group, hiddenCount, nodes: visible };
        });
    }

    async function load(focus, push = false) {
        const currentRequest = ++requestNumber;
        root.classList.add('is-loading');
        ui.status.hidden = true;
        try {
            const query = focus ? `?focus=${encodeURIComponent(focus)}` : '';
            const response = await fetch(`/api/explore/neighborhood${query}`, {
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) throw new Error('Diese Spur konnte nicht geladen werden.');
            const nextGraph = await response.json();
            if (currentRequest !== requestNumber) return;
            const focusChanged = graph?.focus !== nextGraph.focus;
            graph = nextGraph;
            if (focusChanged) expandedCluster = null;
            if (push && graph.focus) {
                history.pushState({ focus: graph.focus }, '',
                    `/explore?focus=${encodeURIComponent(`entity:${graph.focus}`)}`);
            }
            render();
        } catch (error) {
            if (currentRequest !== requestNumber) return;
            ui.status.textContent = error.message || 'Diese Spur konnte nicht geladen werden.';
            ui.status.hidden = false;
        } finally {
            if (currentRequest === requestNumber) root.classList.remove('is-loading');
        }
    }

    function titleLines(title, maximum = 24) {
        const words = title.trim().split(/\s+/);
        const lines = [''];
        for (const word of words) {
            const current = lines.at(-1);
            if (current && `${current} ${word}`.length > maximum && lines.length < 2) {
                lines.push(word);
            } else {
                lines[lines.length - 1] = current ? `${current} ${word}` : word;
            }
        }
        if (lines[1]?.length > maximum + 3) lines[1] = `${lines[1].slice(0, maximum)}…`;
        return lines;
    }

    function ringPositions(neighbors) {
        return neighbors.map((node, index) => {
            const angle = -Math.PI / 2 + ((index + .5) / neighbors.length) * Math.PI * 2;
            return {
                ...node,
                x: CENTER.x + Math.cos(angle) * 520,
                y: CENTER.y + Math.sin(angle) * 245
            };
        });
    }

    function clusterAngles(count) {
        if (count === 2) return [Math.PI, 0];
        if (count === 3) return [Math.PI, -.78, .78];
        if (count === 4) return [-2.38, -.76, .76, 2.38];
        return Array.from({ length: count }, (_, index) => -Math.PI + (index * Math.PI * 2) / count);
    }

    function clusterBounds(group, nodes, expanded = false) {
        const members = nodes.filter(node => node.clusterKey === group.key);
        return {
            ...group,
            expanded,
            x: Math.min(...members.map(node => node.x - 94)) - 24,
            y: Math.min(...members.map(node => node.y - 41)) - 42,
            width: Math.max(...members.map(node => node.x + 94)) - Math.min(...members.map(node => node.x - 94)) + 48,
            height: Math.max(...members.map(node => node.y + 41)) - Math.min(...members.map(node => node.y - 41)) + 66
        };
    }

    function expandedLayout(focus, group) {
        const count = group.nodes.length;
        const columns = Math.min(5, Math.max(2, Math.ceil(Math.sqrt(count * 1.55))));
        const rows = Math.ceil(count / columns);
        const gridCenterX = 870;
        const startX = gridCenterX - ((columns - 1) * 212) / 2;
        const startY = CENTER.y - ((rows - 1) * 106) / 2;
        const nodes = [{ ...focus, x: 170, y: CENTER.y, focus: true },
            ...group.nodes.map((node, index) => ({
                ...node,
                x: startX + (index % columns) * 212,
                y: startY + Math.floor(index / columns) * 106
            }))];
        const cluster = clusterBounds(group, nodes, true);
        const bounds = {
            x: Math.min(28, cluster.x, 170 - 118),
            y: Math.min(28, cluster.y, CENTER.y - 56),
            width: Math.max(WIDTH - 28, cluster.x + cluster.width, 170 + 118) - Math.min(28, cluster.x, 170 - 118),
            height: Math.max(HEIGHT - 28, cluster.y + cluster.height, CENTER.y + 56) - Math.min(28, cluster.y, CENTER.y - 56)
        };
        return { mode: 'expanded', nodes, clusters: [cluster], bounds, group };
    }

    function overviewLayout(focus, groups) {
        const placed = [{ ...focus, ...CENTER, focus: true }];
        const visible = groups.flatMap(group => group.nodes);
        if (groups.length <= 1) {
            placed.push(...ringPositions(visible));
            return {
                mode: 'overview',
                nodes: placed,
                clusters: [],
                bounds: { x: 28, y: 28, width: WIDTH - 56, height: HEIGHT - 56 },
                groups
            };
        }

        const angles = clusterAngles(groups.length);
        groups.forEach((group, groupIndex) => {
            const angle = angles[groupIndex];
            const horizontalSide = Math.abs(Math.cos(angle)) >= Math.abs(Math.sin(angle));
            const columns = horizontalSide ? Math.min(2, group.nodes.length) : Math.min(3, group.nodes.length);
            const rows = Math.ceil(group.nodes.length / columns);
            const rawAnchorX = CENTER.x + Math.cos(angle) * (horizontalSide ? 500 : 430);
            const rawAnchorY = CENTER.y + Math.sin(angle) * (horizontalSide ? 230 : 275);
            const horizontalExtent = ((columns - 1) / 2) * 212 + 118;
            const upperExtent = ((rows - 1) / 2) * 106 + 83;
            const lowerExtent = ((rows - 1) / 2) * 106 + 65;
            const anchorX = Math.max(horizontalExtent, Math.min(WIDTH - horizontalExtent, rawAnchorX));
            const anchorY = Math.max(upperExtent, Math.min(HEIGHT - lowerExtent, rawAnchorY));
            group.nodes.forEach((node, index) => {
                placed.push({
                    ...node,
                    x: anchorX + (index % columns - (columns - 1) / 2) * 212,
                    y: anchorY + (Math.floor(index / columns) - (rows - 1) / 2) * 106
                });
            });
        });
        return {
            mode: 'overview',
            nodes: placed,
            clusters: groups.map(group => clusterBounds(group, placed)),
            bounds: { x: 0, y: 0, width: WIDTH, height: HEIGHT },
            groups
        };
    }

    function positions(nodes) {
        const focus = nodes.find(node => node.slug === graph.focus);
        const allGroups = groupedNeighbors(nodes);
        const expanded = expandedCluster && allGroups.find(group => group.key === expandedCluster);
        if (expanded && focus) return expandedLayout(focus, expanded);
        expandedCluster = null;
        return overviewLayout(focus, overviewGroups(nodes));
    }

    function linkPath(link, positionsBySlug, index) {
        const source = positionsBySlug.get(link.source);
        const target = positionsBySlug.get(link.target);
        if (!source || !target) return '';
        const dx = target.x - source.x;
        const dy = target.y - source.y;
        const length = Math.max(1, Math.hypot(dx, dy));
        const curve = ((index % 3) - 1) * 9;
        const middleX = (source.x + target.x) / 2 - (dy / length) * curve;
        const middleY = (source.y + target.y) / 2 + (dx / length) * curve;
        return `M${source.x},${source.y} Q${middleX},${middleY} ${target.x},${target.y}`;
    }

    function fitTransform(layout) {
        if (layout.mode === 'overview') return d3.zoomIdentity;
        const padding = 42;
        const scale = Math.min(1, (WIDTH - padding * 2) / layout.bounds.width,
            (HEIGHT - padding * 2) / layout.bounds.height);
        const x = (WIDTH - layout.bounds.width * scale) / 2 - layout.bounds.x * scale;
        const y = (HEIGHT - layout.bounds.height * scale) / 2 - layout.bounds.y * scale;
        return d3.zoomIdentity.translate(x, y).scale(scale);
    }

    function resetView(animate = true) {
        if (!currentZoom || !currentLayout) return;
        const svg = d3.select(svgElement);
        const target = fitTransform(currentLayout);
        const selection = animate && !reducedMotion
            ? svg.transition().duration(420).ease(d3.easeCubicOut)
            : svg;
        selection.call(currentZoom.transform, target);
    }

    function expandCluster(key) {
        expandedCluster = key;
        render();
    }

    function showOverview() {
        if (!expandedCluster) return;
        expandedCluster = null;
        render();
    }

    function nodeOrigin(node) {
        return previousPositions.get(node.slug)
            || previousPositions.get(`__more:${node.clusterKey}`)
            || previousPositions.get(graph.focus)
            || CENTER;
    }

    function renderNetwork() {
        const layout = positions(graph.nodes);
        currentLayout = layout;
        const nodes = layout.nodes;
        const positionsBySlug = new Map(nodes.filter(node => !node.more).map(node => [node.slug, node]));
        const visibleLinks = graph.links.filter(link => positionsBySlug.has(link.source) && positionsBySlug.has(link.target));
        const svg = d3.select(svgElement);
        svg.selectAll('*').remove();
        svg.append('title').attr('id', 'network-canvas-title').text('Direkte Verbindungen');
        svg.append('desc').attr('id', 'network-canvas-desc')
            .text('Der gewählte Archiveintrag ist von direkt verbundenen öffentlichen Einträgen umgeben und nach ihrer semantischen Rolle gruppiert. Gruppen lassen sich öffnen; die Ansicht kann verschoben und vergrößert werden.');

        const defs = svg.append('defs');
        const glow = defs.append('filter').attr('id', 'focus-glow').attr('x', '-50%').attr('y', '-50%')
            .attr('width', '200%').attr('height', '200%');
        glow.append('feGaussianBlur').attr('stdDeviation', 12).attr('result', 'blur');
        glow.append('feFlood').attr('flood-color', '#c8431c').attr('flood-opacity', .22);
        glow.append('feComposite').attr('in2', 'blur').attr('operator', 'in');
        const merge = glow.append('feMerge');
        merge.append('feMergeNode');
        merge.append('feMergeNode').attr('in', 'SourceGraphic');

        const scene = svg.append('g').attr('class', 'network-scene');
        currentZoom = d3.zoom()
            .scaleExtent([.45, 2.8])
            .filter(event => {
                const target = event.target instanceof Element ? event.target : null;
                return !target?.closest('.memory-node') && (!event.ctrlKey || event.type === 'wheel');
            })
            .on('zoom', event => scene.attr('transform', event.transform));
        svg.call(currentZoom).on('dblclick.zoom', null);

        const field = scene.append('g').attr('class', 'network-field').attr('aria-hidden', 'true');
        if (layout.clusters.length) {
            const clusterGroups = field.selectAll('g').data(layout.clusters).join('g')
                .attr('class', group => `network-cluster cluster-${group.key} ${group.expanded ? 'is-expanded' : ''}`)
                .attr('opacity', reducedMotion ? 1 : 0);
            clusterGroups.append('rect')
                .attr('x', group => group.x).attr('y', group => group.y)
                .attr('width', group => group.width).attr('height', group => group.height).attr('rx', 4);
            clusterGroups.append('text').attr('x', group => group.x + 14).attr('y', group => group.y + 19)
                .text(group => `${group.label} · ${group.totalCount}`);
            if (!reducedMotion) clusterGroups.transition().duration(360).attr('opacity', 1);
        } else {
            field.append('ellipse').attr('cx', CENTER.x).attr('cy', CENTER.y).attr('rx', 520).attr('ry', 245);
        }

        const paths = scene.append('g').attr('class', 'network-links').selectAll('path')
            .data(visibleLinks, link => `${link.source}:${link.type}:${link.target}`).join('path')
            .attr('class', link => `memory-line ${relationClass(link.type)}`)
            .attr('d', (link, index) => linkPath(link, positionsBySlug, index))
            .attr('pathLength', 1);
        paths.append('title').text(link => label(link.type));
        if (!reducedMotion) {
            paths.attr('stroke-dasharray', 1).attr('stroke-dashoffset', 1)
                .transition().delay(120).duration(520).ease(d3.easeCubicOut).attr('stroke-dashoffset', 0)
                .on('end', function () { d3.select(this).attr('stroke-dasharray', null); });
        }

        const groups = scene.append('g').attr('class', 'network-nodes').selectAll('g')
            .data(nodes, node => node.slug).join('g')
            .attr('class', node => `memory-node ${node.focus ? 'is-focus' : ''} ${node.ghost ? 'is-ghost' : ''} ${node.more ? 'is-more' : ''}`)
            .attr('transform', node => {
                if (reducedMotion) return `translate(${node.x},${node.y})`;
                const origin = nodeOrigin(node);
                return `translate(${origin.x},${origin.y})`;
            })
            .attr('opacity', reducedMotion ? 1 : 0)
            .attr('tabindex', node => node.focus && !node.url ? null : 0)
            .attr('role', node => node.focus && !node.url ? 'group' : 'button')
            .attr('aria-label', node => node.focus
                ? node.url ? `${node.title}, Detailseite öffnen` : `Aktueller Fokus: ${node.title}`
                : node.more
                    ? `Alle Einträge aus ${node.clusterLabel} anzeigen; ${node.hiddenCount} weitere Einträge`
                    : `${node.title} erkunden, ${label(node.entityType)}`)
            .on('click', (_, node) => {
                if (node.more) expandCluster(node.clusterKey);
                else if (node.focus && node.url) window.location.assign(node.url);
                else if (!node.focus) load(`entity:${node.slug}`, true);
            })
            .on('keydown', (event, node) => {
                if ((!node.focus || node.url) && (event.key === 'Enter' || event.key === ' ')) {
                    event.preventDefault();
                    if (node.more) expandCluster(node.clusterKey);
                    else if (node.focus && node.url) window.location.assign(node.url);
                    else load(`entity:${node.slug}`, true);
                }
            });

        groups.append('rect')
            .attr('x', node => node.focus ? -118 : -94)
            .attr('y', node => node.focus ? -56 : -41)
            .attr('width', node => node.focus ? 236 : 188)
            .attr('height', node => node.focus ? 112 : 82)
            .attr('rx', 2);
        groups.append('circle').attr('class', 'node-mark')
            .attr('cx', node => node.focus ? -94 : -72)
            .attr('cy', node => node.focus ? -32 : -20).attr('r', node => node.more ? 5 : 3);
        groups.append('text').attr('class', 'node-type')
            .attr('x', node => node.focus ? -84 : -62)
            .attr('y', node => node.focus ? -28 : -16)
            .text(node => node.more ? 'Gruppe öffnen' : label(node.entityType));
        groups.each(function (node) {
            const text = d3.select(this).append('text').attr('class', 'node-title')
                .attr('x', node.focus ? -94 : -72)
                .attr('y', node.focus ? 5 : 8);
            titleLines(node.title, node.focus ? 29 : 22).forEach((line, index) => text.append('tspan')
                .attr('x', node.focus ? -94 : -72).attr('dy', index ? 20 : 0).text(line));
        });
        groups.append('text').attr('class', 'node-year')
            .attr('x', node => node.focus ? 94 : 72)
            .attr('y', node => node.focus ? 36 : 25)
            .attr('text-anchor', 'end')
            .text(node => node.more ? 'Alle ansehen →' : node.year || '');

        if (!reducedMotion) {
            groups.transition().duration(540).ease(d3.easeCubicOut)
                .attr('transform', node => `translate(${node.x},${node.y})`).attr('opacity', 1);
        }
        previousPositions = new Map(nodes.map(node => [node.slug, { x: node.x, y: node.y }]));
        ui.clusterBack.hidden = layout.mode !== 'expanded';
        resetView(layout.mode === 'expanded');
    }

    function renderPanel() {
        const focus = graph.nodes.find(node => node.slug === graph.focus);
        if (!focus) return;
        const count = graph.links.length;
        const connectedEntries = Math.max(0, graph.nodes.length - 1);
        const semanticGroups = groupedNeighbors(graph.nodes);
        const activeGroup = expandedCluster && semanticGroups.find(group => group.key === expandedCluster);
        ui.summary.textContent = activeGroup
            ? `Alle ${activeGroup.totalCount} Einträge aus „${activeGroup.label}“ rund um ${focus.title}`
            : `${connectedEntries} verbundene ${connectedEntries === 1 ? 'Eintrag' : 'Einträge'} in ${semanticGroups.length} semantischen ${semanticGroups.length === 1 ? 'Gruppe' : 'Gruppen'} rund um ${focus.title}`;
        ui.focusType.textContent = label(focus.entityType);
        ui.focusTitle.textContent = focus.title;
        ui.focusMeta.textContent = [focus.subtype ? label(focus.subtype) : null, focus.year].filter(Boolean).join(' · ')
            || 'Archiveintrag';
        ui.focusDetail.hidden = !focus.url;
        if (focus.url) {
            ui.focusDetail.href = focus.url;
            ui.focusDetail.textContent = `${label(focus.entityType)} ansehen →`;
        }
        if (ui.focusPortrait && ui.focusPortraitImg) {
            ui.focusPortrait.hidden = !focus.imageUrl;
            if (focus.imageUrl) {
                ui.focusPortraitImg.src = focus.imageUrl;
                ui.focusPortraitImg.alt = focus.title;
            }
        }
        ui.relationCount.textContent = count;
        ui.relationList.replaceChildren();

        if (!count) {
            const empty = document.createElement('li');
            empty.className = 'thread-empty';
            empty.textContent = 'Dieser Eintrag hat noch keine direkten öffentlichen Verbindungen.';
            ui.relationList.append(empty);
            return;
        }

        const nodeBySlug = new Map(graph.nodes.map(node => [node.slug, node]));
        const sortedLinks = [...graph.links].sort((left, right) => {
            const leftNode = nodeBySlug.get(otherSlug(left));
            const rightNode = nodeBySlug.get(otherSlug(right));
            const leftCluster = leftNode ? clusterFor(leftNode) : { key: 'other' };
            const rightCluster = rightNode ? clusterFor(rightNode) : { key: 'other' };
            return (clusterOrder.get(leftCluster.key) ?? 99) - (clusterOrder.get(rightCluster.key) ?? 99)
                || (leftNode?.title || '').localeCompare(rightNode?.title || '')
                || (left.label || '').localeCompare(right.label || '');
        });
        let previousCluster = null;
        sortedLinks.forEach(link => {
            const relatedSlug = otherSlug(link);
            const related = nodeBySlug.get(relatedSlug);
            if (!related) return;
            const cluster = clusterFor(related);
            if (cluster.key !== previousCluster) {
                const group = semanticGroups.find(candidate => candidate.key === cluster.key);
                const heading = document.createElement('li');
                heading.className = 'thread-group-label';
                const open = document.createElement('button');
                open.type = 'button';
                open.className = 'thread-group-open';
                open.addEventListener('click', () => expandCluster(cluster.key));
                const name = document.createElement('span');
                name.textContent = cluster.label;
                const size = document.createElement('small');
                size.textContent = group?.totalCount || '';
                open.append(name, size);
                heading.append(open);
                ui.relationList.append(heading);
                previousCluster = cluster.key;
            }
            const item = document.createElement('li');
            const button = document.createElement('button');
            button.type = 'button';
            button.className = related.imageUrl ? 'thread-card thread-card--with-image' : 'thread-card';
            button.addEventListener('click', () => load(`entity:${related.slug}`, true));
            let body = button;
            if (related.imageUrl) {
                const thumb = document.createElement('img');
                thumb.className = 'thread-card-thumb';
                thumb.src = related.imageUrl;
                thumb.alt = '';
                thumb.loading = 'lazy';
                body = document.createElement('div');
                body.className = 'thread-card-body';
                button.append(thumb, body);
            }
            const relation = document.createElement('span');
            relation.textContent = `${link.source === graph.focus ? '→' : '←'} ${label(link.type)}`;
            const title = document.createElement('strong');
            title.textContent = related.title;
            const meta = document.createElement('small');
            meta.textContent = [label(related.entityType), related.year].filter(Boolean).join(' · ');
            body.append(relation, title, meta);
            item.append(button);
            ui.relationList.append(item);
        });
    }

    function render() {
        if (!graph?.focus) return;
        renderNetwork();
        renderPanel();
    }

    ui.clusterBack?.addEventListener('click', showOverview);
    ui.viewReset?.addEventListener('click', () => resetView(true));
    window.addEventListener('popstate', () => load(new URLSearchParams(location.search).get('focus'), false));
    load(new URLSearchParams(location.search).get('focus') || (root.dataset.focus ? `entity:${root.dataset.focus}` : null));
}
