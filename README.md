<img width="1000" height="100" alt="divider_dual_energy_tracks" src="https://github.com/user-attachments/assets/2ce59149-332a-4a52-a3dd-e86baf2924e7" /><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 120" width="100%" height="120" fill="none">
  <defs>
    <linearGradient id="divider-cyber-a" x1="0" y1="0" x2="1200" y2="0" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#06b6d4"/>
      <stop offset="50%" stop-color="#8b5cf6"/>
      <stop offset="100%" stop-color="#ec4899"/>
    </linearGradient>
  </defs>

  <!-- symmetric geometry -->
  <path d="M0 60 H420 L480 20 H720 L780 60 H1200"
        stroke="#1f2937" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>

  <path d="M0 60 H420 L480 20 H720 L780 60 H1200"
        stroke="url(#divider-cyber-a)" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"
        stroke-dasharray="180 1600">
    <animate attributeName="stroke-dashoffset" values="1780;0" dur="3s" repeatCount="indefinite"/>
  </path>
# Preset Editor

Preset Editor - настольный редактор JSON-пресетов для SillyTavern и похожих prompt/preset workflow. Приложение сделано для удобной ручной сборки, проверки и оформления больших наборов prompt-блоков без постоянного редактирования JSON в текстовом редакторе.
<img width="1918" height="935" alt="photo_2026-07-03_12-33-57" src="https://github.com/user-attachments/assets/2867f308-1c6a-4717-875a-f4ec5dcab5bd" />
<img width="1711" height="1042" alt="image" src="https://github.com/user-attachments/assets/cefb299b-53e6-4b27-bf70-4579050c358c" />
<img width="1913" height="1068" alt="image" src="https://github.com/user-attachments/assets/43ad1f5c-c9b9-4ffa-8ba6-98112e2352f6" />
### Важно
Windows может показать предупреждение SmartScreen, потому что приложение пока не подписано сертификатом.
Если вы доверяете этому релизу, нажмите "Подробнее" -> "Выполнить в любом случае".

Windows may show a SmartScreen warning because the app is unsigned.
Click More info -> Run anyway if you trust this release.
## Что умеет

- Открывать и редактировать JSON-пресеты с `prompts` и `prompt_order`.
- Управлять prompt-блоками: имя, роль, включение, порядок, глубина injection, позиция, содержимое.
- Импортировать и экспортировать готовый preset JSON.
- Работать с библиотекой пресетов: статусы, теги, поиск, сортировка, серии, обложки.
- Показывать Preview пресета и собирать итоговый preview включенных prompt-блоков.
- Проверять release-состояние пресета: имя, описание, теги, обложка, большие prompt-блоки, секции, отключенные блоки, директивы и переменные.
- Сканировать `getvar` / `setvar`-макросы и prompt-директивы.
- Вести избранные prompt-блоки и заметки.
- Работать как desktop-приложение на Electron и как PWA-версия из папки `PresetEditorApp/pwa`.

## Актуальная версия

Текущая версия: `0.1.1`.

В этой версии:
- release-check больше не считает ошибкой пустые prompt-блоки и prompt-блоки без имени;

## Лицензия
Пока проект помечен как `UNLICENSED` в `package.json`.



# Preset Editor
Preset Editor is a desktop editor for JSON presets designed for SillyTavern and similar prompt/preset workflows. The application is built for convenient manual assembly, validation, and formatting of large sets of prompt blocks, eliminating the need for constant JSON editing in a text editor.

## Features
Open and edit JSON presets with prompts and prompt_order.
Manage prompt blocks: name, role, enable/disable, order, injection depth, position, and content.
Import and export completed preset JSON.
Work with a preset library: statuses, tags, search, sorting, series, and covers.
Display a preset Preview and compile a final preview of enabled prompt blocks.
Check the release readiness of a preset: name, description, tags, cover, large prompt blocks, sections, disabled blocks, directives, and variables.
Scan for getvar / setvar macros and prompt directives.
Maintain favorite prompt blocks and notes.
Operate as a desktop application on Electron and as a PWA version from the PresetEditorApp/pwa folder.

## Current Version
Current version: 0.1.1.

In this version:
The release check no longer treats empty prompt blocks or prompt blocks without a name as errors.

## License
For now, the project is marked as UNLICENSED in package.json.
