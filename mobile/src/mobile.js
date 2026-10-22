// Mobile shell for Preset Editor: bottom tab bar, one screen at a time,
// Android back button support, native export in the Android app and PWA registration.
// Runs after the desktop script, so its top-level functions (setDrawer, setWorkspaceMode,
// setActiveTab, isRussianUi, ...) are available as globals.
(function () {
  'use strict';

  const body = document.body;
  const mq = window.matchMedia('(max-width: 820px)');
  const isNative = Boolean(window.Capacitor?.isNativePlatform?.());
  const call = (name, ...args) => (typeof window[name] === 'function' ? window[name](...args) : undefined);
  const ru = () => Boolean(call('isRussianUi'));
  const tr = (ruText, enText) => (ru() ? ruText : enText);

  const ICONS = {
    library: '<path d="M4 19.5V5a2 2 0 0 1 2-2h12v15H6.5A2.5 2.5 0 0 0 4 20.5 2.5 2.5 0 0 0 6.5 23H18"/><path d="M8 7h6"/>',
    prompts: '<path d="M8 6h13M8 12h13M8 18h13"/><circle cx="3.5" cy="6" r="1"/><circle cx="3.5" cy="12" r="1"/><circle cx="3.5" cy="18" r="1"/>',
    editor: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
    tools: '<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
    back: '<path d="M15 18l-6-6 6-6"/>'
  };
  const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name]}</svg>`;

  const TABS = [
    { id: 'library', ru: 'Библиотека', en: 'Library' },
    { id: 'prompts', ru: 'Промпты', en: 'Prompts' },
    { id: 'editor', ru: 'Редактор', en: 'Editor' },
    { id: 'tools', ru: 'Инструменты', en: 'Tools' },
    { id: 'settings', ru: 'Настройки', en: 'Settings' }
  ];

  const TOOLS = [
    { id: 'inspiration', ru: 'Вдохновение', en: 'Inspiration', hintRu: 'Промпты из другого пресета', hintEn: 'Prompts from another preset' },
    { id: 'notes', ru: 'Заметки', en: 'Notes', hintRu: 'Черновики и идеи', hintEn: 'Drafts and ideas' },
    { id: 'favorites', ru: 'Избранное', en: 'Favorites', hintRu: 'Сохранённые промпты', hintEn: 'Saved prompts' },
    { id: 'snippets', ru: 'Сниппеты', en: 'Snippets', hintRu: 'Готовые вставки', hintEn: 'Reusable inserts' },
    { id: 'variables', ru: 'Переменные', en: 'Variables', hintRu: 'getvar / setvar в пресете', hintEn: 'getvar / setvar usage' },
    { id: 'directives', ru: 'Директивы', en: 'Directives', hintRu: 'Служебные пометки промптов', hintEn: 'Prompt directives' },
    { id: 'preview', ru: 'Превью', en: 'Preview', hintRu: 'Как соберётся пресет', hintEn: 'Assembled preset' },
    { id: 'release', ru: 'Проверка', en: 'Release Check', hintRu: 'Готов ли пресет к публикации', hintEn: 'Is the preset ready' }
  ];

  let screen = 'list';
  let tabBar = null;
  let toolsSheet = null;
  let libDetailOpen = false;

  // ---- screens ---------------------------------------------------------------

  function setScreen(next, { push = true } = {}) {
    const changed = screen !== next;
    screen = next;
    body.classList.toggle('m-screen-editor', next === 'editor');
    body.classList.toggle('m-screen-list', next === 'list');
    if (changed && push && next === 'editor') pushLayer();
    if (changed) window.scrollTo(0, 0);
    updateTabBar();
  }

  function goBuilder(nextScreen) {
    closeToolsSheet();
    closeLibDetail();
    call('setDrawer', null);
    if (body.classList.contains('library-open')) call('setWorkspaceMode', 'builder');
    setScreen(nextScreen);
  }

  function activeTabId() {
    if (toolsSheet?.classList.contains('open')) return 'tools';
    if (body.classList.contains('library-open')) return 'library';
    if (body.classList.contains('settings-open')) return 'settings';
    if (body.classList.contains('drawer-open')) return 'tools';
    return screen === 'editor' ? 'editor' : 'prompts';
  }

  function onTab(id) {
    if (id === 'library') {
      closeToolsSheet();
      call('setDrawer', null);
      call('setWorkspaceMode', 'library');
      pushLayer();
    } else if (id === 'prompts') {
      goBuilder('list');
    } else if (id === 'editor') {
      goBuilder('editor');
    } else if (id === 'tools') {
      if (toolsSheet?.classList.contains('open')) closeToolsSheet();
      else openToolsSheet();
    } else if (id === 'settings') {
      closeToolsSheet();
      call('setDrawer', 'settings');
      pushLayer();
    }
    updateTabBar();
  }

  // ---- bottom tab bar ----------------------------------------------------------

  function buildTabBar() {
    tabBar = document.createElement('nav');
    tabBar.className = 'm-tabbar';
    tabBar.setAttribute('aria-label', 'Navigation');
    tabBar.setAttribute('data-i18n-skip', '');
    TABS.forEach(tab => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'm-tab';
      button.dataset.tab = tab.id;
      button.innerHTML = `${icon(tab.id)}<span></span>`;
      button.addEventListener('click', () => onTab(tab.id));
      tabBar.appendChild(button);
    });
    body.appendChild(tabBar);
  }

  function updateTabBar() {
    if (!tabBar) return;
    const active = activeTabId();
    tabBar.querySelectorAll('.m-tab').forEach(button => {
      const tab = TABS.find(item => item.id === button.dataset.tab);
      button.querySelector('span').textContent = tr(tab.ru, tab.en);
      button.classList.toggle('active', button.dataset.tab === active);
      button.setAttribute('aria-current', button.dataset.tab === active ? 'page' : 'false');
    });
  }

  // ---- tools sheet ---------------------------------------------------------------

  function buildToolsSheet() {
    toolsSheet = document.createElement('div');
    toolsSheet.className = 'm-sheet-backdrop';
    toolsSheet.setAttribute('data-i18n-skip', '');
    toolsSheet.innerHTML = '<div class="m-sheet" role="dialog" aria-modal="true"><div class="m-sheet-grip"></div><div class="m-sheet-title"></div><div class="m-sheet-grid"></div></div>';
    toolsSheet.addEventListener('click', event => {
      if (event.target === toolsSheet) closeToolsSheet();
    });
    body.appendChild(toolsSheet);
  }

  function openToolsSheet() {
    toolsSheet.querySelector('.m-sheet-title').textContent = tr('Инструменты', 'Tools');
    const grid = toolsSheet.querySelector('.m-sheet-grid');
    grid.innerHTML = '';
    TOOLS.forEach(tool => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'm-tool';
      const title = document.createElement('strong');
      title.textContent = tr(tool.ru, tool.en);
      const hint = document.createElement('span');
      hint.textContent = tr(tool.hintRu, tool.hintEn);
      button.append(title, hint);
      button.addEventListener('click', () => {
        closeToolsSheet();
        call('setDrawer', tool.id);
        pushLayer();
        updateTabBar();
      });
      grid.appendChild(button);
    });
    toolsSheet.classList.add('open');
    pushLayer();
    updateTabBar();
  }

  function closeToolsSheet() {
    if (!toolsSheet?.classList.contains('open')) return false;
    toolsSheet.classList.remove('open');
    updateTabBar();
    return true;
  }

  // ---- library details as a sheet -------------------------------------------

  function openLibDetail() {
    if (libDetailOpen) return;
    libDetailOpen = true;
    body.classList.add('m-lib-detail');
    pushLayer();
  }

  function closeLibDetail() {
    if (!libDetailOpen) return false;
    libDetailOpen = false;
    body.classList.remove('m-lib-detail');
    return true;
  }

  function addLibDetailCloseButton() {
    const content = document.getElementById('libraryDetailsContent');
    if (!content || content.querySelector('.m-sheet-close')) return;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn m-sheet-close';
    button.innerHTML = `${icon('back')}<span></span>`;
    button.addEventListener('click', () => {
      closeLibDetail();
      updateCloseLabels();
    });
    content.prepend(button);
  }

  // ---- editor back button ------------------------------------------------------

  function addEditorBackButton() {
    const head = document.querySelector('.editor-panel .editor-head');
    if (!head || head.querySelector('.m-back')) return;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn m-back';
    button.innerHTML = `${icon('back')}<span></span>`;
    button.addEventListener('click', () => goBuilder('list'));
    head.prepend(button);

    const drawerHead = document.querySelector('.drawer-panel .panel-head');
    if (drawerHead && !drawerHead.querySelector('.m-back')) {
      const drawerBack = document.createElement('button');
      drawerBack.type = 'button';
      drawerBack.className = 'btn m-back';
      drawerBack.innerHTML = `${icon('back')}<span></span>`;
      drawerBack.addEventListener('click', () => {
        call('setDrawer', null);
        updateTabBar();
      });
      drawerHead.prepend(drawerBack);
    }
  }

  function updateCloseLabels() {
    document.querySelectorAll('.editor-panel .m-back span').forEach(el => { el.textContent = tr('Промпты', 'Prompts'); });
    document.querySelectorAll('.drawer-panel .m-back span').forEach(el => { el.textContent = tr('Назад', 'Back'); });
    document.querySelectorAll('.m-sheet-close span').forEach(el => { el.textContent = tr('К списку', 'Back to list'); });
  }

  // ---- taps that should change the screen ---------------------------------

  function wireNavigation() {
    document.getElementById('promptList')?.addEventListener('click', event => {
      if (!mq.matches || body.classList.contains('reorder-mode')) return;
      if (!event.target.closest('.prompt-row')) return;
      if (event.target.closest('button, input, label, select, .drag-handle, .row-actions, .favorite-btn, .toggle')) return;
      setTimeout(() => {
        call('setActiveTab', 'prompt', { silent: true });
        setScreen('editor');
      }, 0);
    });

    ['topPresetModeBtn', 'topPromptModeBtn', 'topInfoModeBtn', 'presetInfoBtn', 'topAddPromptBtn', 'editorTabPresetInfo', 'editorTabPrompt'].forEach(id => {
      document.getElementById(id)?.addEventListener('click', () => {
        if (mq.matches) setTimeout(() => goBuilder('editor'), 0);
      });
    });

    ['openLibraryPresetBtn', 'newPresetBtn'].forEach(id => {
      document.getElementById(id)?.addEventListener('click', () => {
        if (!mq.matches) return;
        setTimeout(() => {
          closeLibDetail();
          if (!body.classList.contains('library-open')) setScreen('list', { push: false });
          updateTabBar();
        }, 50);
      });
    });

    document.getElementById('libraryCatalog')?.addEventListener('click', event => {
      if (!mq.matches) return;
      if (event.target.closest('.library-item') && (typeof libraryMode === 'undefined' || libraryMode !== 'seriesCatalog')) {
        setTimeout(() => {
          addLibDetailCloseButton();
          updateCloseLabels();
          openLibDetail();
        }, 0);
      }
    });

    // The topbar overflow menu is the only home for New/Import/Export on a phone.
    document.getElementById('topbarOverflowMenu')?.addEventListener('click', event => {
      if (event.target.closest('button')) closeToolsSheet();
    });
  }

  // ---- Android back button / back gesture -----------------------------------

  let layers = 0;
  function pushLayer() {
    if (!mq.matches) return;
    layers += 1;
    history.pushState({ presetEditorLayer: layers }, '');
  }

  function closeTopLayer() {
    const modal = document.querySelector('.modal-backdrop.open');
    if (modal) {
      const close = modal.querySelector('button[id^="close"], button[id^="cancel"]');
      if (close) { close.click(); return true; }
    }
    if (closeToolsSheet()) return true;
    if (closeLibDetail()) return true;
    if (body.classList.contains('drawer-open')) { call('setDrawer', null); return true; }
    if (body.classList.contains('library-open')) { call('setWorkspaceMode', 'builder'); setScreen('list', { push: false }); return true; }
    if (screen === 'editor') { setScreen('list', { push: false }); return true; }
    return false;
  }

  window.addEventListener('popstate', () => {
    layers = Math.max(0, layers - 1);
    if (!mq.matches) return;
    closeTopLayer();
    updateTabBar();
  });

  // ---- native export in the Android app ------------------------------------

  function installNativeDownload() {
    if (!isNative) return;
    const plugins = window.Capacitor?.Plugins || {};
    const { Filesystem, Share } = plugins;
    if (!Filesystem) return;
    window.download = async function download(fileName, content, type) {
      const safeName = String(fileName || 'preset.json').replace(/[\\/:*?"<>|]/g, '_');
      try {
        let savedTo = '';
        try {
          await Filesystem.writeFile({ path: `PresetEditor/${safeName}`, data: String(content), directory: 'DOCUMENTS', encoding: 'utf8', recursive: true });
          savedTo = `Documents/PresetEditor/${safeName}`;
        } catch (error) {
          // Public Documents can be unavailable on some Android versions; sharing still works.
        }
        await Filesystem.writeFile({ path: safeName, data: String(content), directory: 'CACHE', encoding: 'utf8' });
        const { uri } = await Filesystem.getUri({ path: safeName, directory: 'CACHE' });
        if (Share) {
          await Share.share({ title: safeName, url: uri, dialogTitle: tr('Отправить или сохранить файл', 'Share or save file') }).catch(() => {});
        }
        if (savedTo) call('toast', `${tr('Сохранено', 'Saved')}: ${savedTo}`);
      } catch (error) {
        call('toast', tr('Не удалось сохранить файл', 'Could not save file'));
      }
    };
  }

  // ---- save when the app goes to background ----------------------------------
  // Phones kill background apps without warning, so flush the autosave draft
  // whenever the page is hidden instead of waiting for the autosave timer.

  function flushAutosave() {
    try {
      if (typeof autosaveReady !== 'undefined' && autosaveReady) call('writeAutosave');
    } catch (error) {
      // Autosave is best-effort.
    }
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushAutosave();
  });
  window.addEventListener('pagehide', flushAutosave);

  // ---- init --------------------------------------------------------------------

  function applyMode() {
    body.classList.toggle('m', mq.matches);
    if (mq.matches) {
      if (!body.classList.contains('m-screen-editor')) setScreen(screen, { push: false });
      // The desktop UI can collapse panels; on a phone everything is shown full width.
      body.classList.remove('nav-collapsed', 'preset-list-collapsed', 'right-panel-collapsed');
      document.querySelector('.editor-panel')?.classList.remove('collapsed');
    }
    updateTabBar();
  }

  function init() {
    if (isNative) body.classList.add('m-native');
    buildTabBar();
    buildToolsSheet();
    addEditorBackButton();
    addLibDetailCloseButton();
    updateCloseLabels();
    wireNavigation();
    installNativeDownload();
    applyMode();
    mq.addEventListener?.('change', applyMode);
    new MutationObserver(() => {
      if (!mq.matches) return;
      if (body.classList.contains('right-panel-collapsed')) {
        // Desktop "collapse editor" has no meaning when the editor is its own screen.
        try { rightPanelCollapsed = false; } catch (error) { /* older desktop builds */ }
        body.classList.remove('right-panel-collapsed');
        document.querySelector('.editor-panel')?.classList.remove('collapsed');
      }
      if (!body.classList.contains('library-open') && libDetailOpen) closeLibDetail();
      updateTabBar();
    }).observe(body, { attributes: true, attributeFilter: ['class'] });

    if ('serviceWorker' in navigator && !isNative && /^https?:$/.test(location.protocol)) {
      navigator.serviceWorker.register('sw.js').catch(() => {});
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
