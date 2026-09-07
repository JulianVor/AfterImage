const register = document.querySelector('.akte-register');

if (register) {
    const links = [...register.querySelectorAll('.akte-register-link')];
    const sections = links
        .map(link => document.querySelector(link.getAttribute('href')))
        .filter(Boolean);

    const activate = (id) => {
        links.forEach(link => link.classList.toggle('is-active', link.getAttribute('href') === '#' + id));
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) activate(entry.target.id);
        });
    }, { rootMargin: '-35% 0px -55% 0px' });

    sections.forEach(section => observer.observe(section));
}
