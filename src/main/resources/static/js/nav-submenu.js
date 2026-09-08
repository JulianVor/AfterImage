document.querySelectorAll('.nav-item--has-submenu').forEach(item => {
    const toggle = item.querySelector('.nav-toggle');

    const close = () => {
        item.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
    };

    toggle.addEventListener('click', event => {
        event.stopPropagation();
        const willOpen = !item.classList.contains('is-open');
        document.querySelectorAll('.nav-item--has-submenu.is-open').forEach(other => {
            if (other !== item) other.classList.remove('is-open');
        });
        item.classList.toggle('is-open', willOpen);
        toggle.setAttribute('aria-expanded', String(willOpen));
    });

    item.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            close();
            toggle.focus();
        }
    });
});

document.addEventListener('click', event => {
    document.querySelectorAll('.nav-item--has-submenu.is-open').forEach(item => {
        if (!item.contains(event.target)) {
            item.classList.remove('is-open');
            item.querySelector('.nav-toggle').setAttribute('aria-expanded', 'false');
        }
    });
});
