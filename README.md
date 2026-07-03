# Preset Editor

Preset Editor - настольный редактор JSON-пресетов для SillyTavern и похожих prompt/preset workflow. Приложение сделано для удобной ручной сборки, проверки и оформления больших наборов prompt-блоков без постоянного редактирования JSON в текстовом редакторе.

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

- исправлено написание `что угодно`;
- release-check больше не считает ошибкой пустые prompt-блоки и prompt-блоки без имени;
- сборка установщика работает без ошибки `winCodeSign` на Windows-системах, где нет прав на создание symlink.

## Где лежит готовая сборка

После сборки готовый установщик находится здесь:

```text
PresetEditorApp/dist/Preset Editor Setup 0.1.1.exe
```

Portable-версия без установки находится здесь:

```text
PresetEditorApp/dist/win-unpacked/Preset Editor.exe
```

Папки `dist`, `dist-dropdown` и `node_modules` не коммитятся в Git. Установщик лучше прикреплять к GitHub Release, а не хранить прямо в репозитории.

## Запуск из исходников

Нужен Node.js и npm.

```powershell
cd PresetEditorApp
npm install
npm start
```

## Сборка установщика

```powershell
cd PresetEditorApp
npm install
npm run dist
```

Команда создаст:

```text
PresetEditorApp/dist/Preset Editor Setup 0.1.1.exe
PresetEditorApp/dist/Preset Editor Setup 0.1.1.exe.blockmap
PresetEditorApp/dist/win-unpacked/Preset Editor.exe
```

В `package.json` сборка настроена так:

```json
"dist": "electron-builder --win nsis --config.win.signAndEditExecutable=false"
```

Флаг `--config.win.signAndEditExecutable=false` нужен, чтобы Electron Builder не падал на распаковке `winCodeSign` из-за Windows-прав на symlink.

## Как вручную выложить на GitHub

### 1. Создать репозиторий

1. Открой GitHub.
2. Нажми `New repository`.
3. Введи имя репозитория, например `preset-editor`.
4. Не добавляй README, `.gitignore` и license на сайте, потому что они уже есть локально.
5. Нажми `Create repository`.

### 2. Подключить локальный проект

В корневой папке проекта:

```powershell
cd "C:\Users\karas\OneDrive\Документы\Пресет\Пресет"
git remote add origin https://github.com/USERNAME/REPO.git
git push -u origin main
git push origin v0.1.1
```

Замени `USERNAME/REPO` на свой GitHub-путь.

### 3. Создать Release

1. Открой репозиторий на GitHub.
2. Перейди в `Releases`.
3. Нажми `Draft a new release`.
4. В поле `Choose a tag` выбери `v0.1.1`.
5. Title: `Preset Editor 0.1.1`.
6. В описание можно вставить:

```text
Preset Editor 0.1.1

Changes:
- Fixed spelling of "что угодно".
- Empty prompt content and unnamed prompt blocks are no longer release-check warnings.
- Windows installer build now avoids the winCodeSign symlink issue.
```

7. В `Attach binaries` прикрепи:

```text
PresetEditorApp/dist/Preset Editor Setup 0.1.1.exe
```

8. Нажми `Publish release`.

## Что не нужно загружать на GitHub

Не загружай вручную в код репозитория:

- `PresetEditorApp/node_modules`
- `PresetEditorApp/dist`
- `PresetEditorApp/dist-dropdown`
- `.exe`-файлы установщика

Установщик прикрепляется только к GitHub Release.

## Структура проекта

```text
PresetEditorApp/
  app/index.html              Electron UI
  pwa/                        PWA build
  main.js                     Electron main process
  preload.js                  Electron preload bridge
  package.json                scripts and build config
preset_editor_v2.html         standalone/prototype editor page
Template.json                 base preset template
```

## Лицензия

Пока проект помечен как `UNLICENSED` в `package.json`.

