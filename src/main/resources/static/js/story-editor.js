document.querySelectorAll('.story-block-form').forEach((form) => {
    const typeSelect = form.querySelector('[data-block-type-select]');
    const purpose = form.querySelector('[data-block-purpose]');
    const entityField = form.querySelector('.story-field--entity');
    const albumPathField = form.querySelector('.story-field--album-path');
    const photoLimitField = form.querySelector('.story-field--photo-limit');
    const headingField = form.querySelector('.story-field--heading');
    const dateField = form.querySelector('.story-field--date');
    const textField = form.querySelector('.story-field--text');
    const entitySelect = entityField?.querySelector('select');
    const albumPathInput = albumPathField?.querySelector('input');
    const photoLimitInput = photoLimitField?.querySelector('input');
    const headingInput = headingField?.querySelector('input');
    const textInput = textField?.querySelector('textarea');
    const textLabel = textField?.querySelector('[data-field-label]');
    const textHelp = textField?.querySelector('[data-field-help]');

    const setVisible = (field, control, visible) => {
        if (!field || !control) return;
        field.hidden = !visible;
        control.disabled = !visible;
    };

    const refresh = () => {
        const type = typeSelect.value;
        const chronological = form.dataset.storyType === 'CHRONICLE';
        form.dataset.blockType = type;

        setVisible(entityField, entitySelect, type === 'ENTRY' || type === 'GALLERY');
        setVisible(albumPathField, albumPathInput, type === 'GALLERY');
        setVisible(photoLimitField, photoLimitInput, type === 'GALLERY');
        setVisible(headingField, headingInput, type === 'TEXT' || type === 'GALLERY' || type === 'SECTION');
        setVisible(dateField, dateField?.querySelector('input'), chronological && type !== 'SECTION');
        setVisible(textField, textInput, type !== 'ENTRY');
        entitySelect.required = type === 'ENTRY';
        headingInput.required = type === 'SECTION';

        const copy = {
            ENTRY: ['Archivkarte', 'Ein konkreter Archivfund. Bild, Titel, Kurzbeschreibung und Link kommen vollständig aus dem Archiv.', '', ''],
            TEXT: ['Freies Kapitel', 'Ein eigenständiger redaktioneller Abschnitt ohne Verknüpfung zu einem Archiveintrag.', 'Kapiteltext', 'Erzähle diesen Abschnitt frei.'],
            GALLERY: ['Fotostrecke', 'Eine visuelle Passage aus Fotos – entweder eines Archiveintrags oder direkt eines Piwigo-Albums.', 'Einleitung oder Bildlegende (optional)', 'Gib der Bildstrecke einen kurzen Kontext.'],
            QUOTE: ['Zitat', 'Eine einzelne Aussage als bewusster typografischer Einschnitt.', 'Zitat', 'Zitat oder prägnante Aussage'],
            SECTION: ['Neuer Abschnitt', 'Bündelt alle folgenden Kapitel bis zum nächsten Abschnitt unter einer gemeinsamen Überschrift.', 'Abschnittseinleitung (optional)', 'Worum geht es in diesem Abschnitt?']
        }[type];

        purpose.innerHTML = `<strong>${copy[0]}</strong><span>${copy[1]}</span>`;
        textLabel.textContent = copy[2];
        textInput.placeholder = copy[3];
        headingField.querySelector('[data-field-label]').textContent = type === 'SECTION' ? 'Abschnittstitel' : 'Überschrift';
        textHelp.textContent = type === 'SECTION' ? 'Die folgenden Kapitel werden diesem Abschnitt zugeordnet.' : '';
    };

    typeSelect.addEventListener('change', refresh);
    refresh();
});
