const register = document.querySelector('.akte-register');

if (register) {
    const links = [...register.querySelectorAll('.akte-register-link')];
    const sections = links
        .map(link => document.querySelector(link.getAttribute('href')))
        .filter(Boolean);

    let activeId = null;

    const activate = (id) => {
        if (id === activeId) return;
        activeId = id;
        let activeLink = null;
        links.forEach(link => {
            const isActive = link.getAttribute('href') === '#' + id;
            link.classList.toggle('is-active', isActive);
            if (isActive) activeLink = link;
        });
        if (activeLink) activeLink.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) activate(entry.target.id);
        });
    }, { rootMargin: '-35% 0px -55% 0px' });

    sections.forEach(section => observer.observe(section));
}
