const svg = document.querySelector('#band-network-canvas');
const emptyState = document.querySelector('.band-network-empty');

if (svg && window.d3) {
    const d3 = window.d3;
    const WIDTH = 1400;
    const HEIGHT = 760;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const goTo = node => window.location.assign('/explore?focus=' + encodeURIComponent('entity:' + node.slug));

    fetch('/api/explore/bands')
        .then(response => response.ok ? response.json() : Promise.reject(new Error('request failed')))
        .then(render)
        .catch(() => { if (emptyState) emptyState.hidden = false; });

    function render(data) {
        const nodes = (data.nodes || []).map(node => ({ ...node }));
        const links = (data.links || []).map(link => ({ ...link }));
        if (nodes.length === 0) {
            if (emptyState) emptyState.hidden = false;
            return;
        }

        const degree = new Map();
        links.forEach(link => {
            degree.set(link.source, (degree.get(link.source) || 0) + 1);
            degree.set(link.target, (degree.get(link.target) || 0) + 1);
        });
        const radius = node => 4 + Math.min(degree.get(node.slug) || 0, 8);

        const svgSel = d3.select(svg);
        const linkSelection = svgSel.append('g').attr('class', 'band-network-links')
            .selectAll('line')
            .data(links)
            .join('line')
            .attr('class', 'band-network-link')
            .attr('stroke-width', link => Math.min(1 + link.weight * 0.4, 3));

        const nodeSelection = svgSel.append('g').attr('class', 'band-network-nodes')
            .selectAll('g')
            .data(nodes, node => node.slug)
            .join('g')
            .attr('class', 'band-network-node')
            .attr('tabindex', 0)
            .attr('role', 'button')
            .attr('aria-label', node => `${node.title} bei Entdecken öffnen`)
            .on('click', (_, node) => goTo(node))
            .on('keydown', (event, node) => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    goTo(node);
                }
            });

        nodeSelection.append('circle').attr('r', radius);
        nodeSelection.append('text')
            .attr('x', node => radius(node) + 5)
            .attr('y', 4)
            .text(node => node.title);

        const simulation = d3.forceSimulation(nodes)
            .force('link', d3.forceLink(links).id(node => node.slug).distance(300).strength(0.2))
            .force('charge', d3.forceManyBody().strength(-2400))
            .force('center', d3.forceCenter(WIDTH / 2, HEIGHT / 2))
            .force('collision', d3.forceCollide().radius(node => radius(node) + 16));

        // The hover reheat's repulsion can otherwise fling a neighbor node past the edge of the
        // viewBox (only its connecting line still visible, running off the canvas). Clamp every
        // node back inside a safety margin on every tick rather than relying on force tuning alone.
        const EDGE_PADDING = 70;
        const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
        const keepInBounds = () => {
            nodes.forEach(node => {
                node.x = clamp(node.x, EDGE_PADDING, WIDTH - EDGE_PADDING);
                node.y = clamp(node.y, EDGE_PADDING, HEIGHT - EDGE_PADDING);
            });
        };

        const applyPositions = () => {
            keepInBounds();
            linkSelection
                .attr('x1', link => link.source.x).attr('y1', link => link.source.y)
                .attr('x2', link => link.target.x).attr('y2', link => link.target.y);
            nodeSelection.attr('transform', node => `translate(${node.x},${node.y})`);
        };

        if (reducedMotion) {
            simulation.stop();
            for (let i = 0; i < 300; i++) simulation.tick();
            applyPositions();
        } else {
            simulation.on('tick', applyPositions);
        }

        // Hover only makes sense with a real pointer - on touch, "hover" fires on tap and never
        // really leaves, which would leave the network stuck highlighted/dimmed.
        const supportsHover = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
        if (supportsHover) {
            const neighborSlugs = new Map(nodes.map(node => [node.slug, new Set([node.slug])]));
            links.forEach(link => {
                const sourceSlug = link.source.slug ?? link.source;
                const targetSlug = link.target.slug ?? link.target;
                neighborSlugs.get(sourceSlug).add(targetSlug);
                neighborSlugs.get(targetSlug).add(sourceSlug);
            });
            const touches = (link, slug) => (link.source.slug ?? link.source) === slug
                || (link.target.slug ?? link.target) === slug;

            nodeSelection
                .on('mouseenter', (_, node) => {
                    const connected = neighborSlugs.get(node.slug);
                    nodeSelection.classed('is-active', other => other.slug === node.slug)
                        .classed('is-dimmed', other => !connected.has(other.slug));
                    linkSelection.classed('is-active', link => touches(link, node.slug))
                        .classed('is-dimmed', link => !touches(link, node.slug));
                    // Pin the hovered node to where it already is so it can't drift out from under
                    // the cursor - everything else keeps reacting to it via the reheated simulation.
                    node.fx = node.x;
                    node.fy = node.y;
                    if (!reducedMotion) simulation.alphaTarget(0.15).restart();
                })
                .on('mouseleave', (_, node) => {
                    nodeSelection.classed('is-active', false).classed('is-dimmed', false);
                    linkSelection.classed('is-active', false).classed('is-dimmed', false);
                    node.fx = null;
                    node.fy = null;
                    if (!reducedMotion) simulation.alphaTarget(0);
                });
        }
    }
}
