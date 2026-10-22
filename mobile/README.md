# Preset Editor — мобильная версия

Мобильная версия собирается из того же `index.html`, что и ПК-версия на Electron. Логика редактора не переписывается: сборка добавляет к нему мобильную оболочку.

- `src/workspace-shim.js` повторяет API `window.presetWorkspace` из `main.js`/`preload.js`, только файлы рабочей области (Presets, Metadata, Assets/Covers, Series, Backups) хранятся в IndexedDB на телефоне.
- `src/mobile.css` и `src/mobile.js` включаются на экранах уже 820px: нижние вкладки (Библиотека, Промпты, Редактор, Инструменты, Настройки), на экране одна панель, инструменты открываются шторкой, кнопка «назад» на Android закрывает текущий экран, черновик сохраняется при сворачивании приложения.
- `src/sw.js` и `src/manifest.webmanifest` дают установку на домашний экран и работу офлайн.
- `android/` содержит проект Capacitor, который упаковывает тот же бандл в APK. Экспорт JSON в APK открывает системное меню «Поделиться» и дополнительно пытается сохранить копию в `Documents/PresetEditor`.

## Как обновить после изменений ПК-версии

1. Скопируйте новый `app/index.html` из Electron-приложения в `source/index.desktop.html`.
2. Поднимите версию в `package.json`.
3. Закоммитьте изменения. GitHub Actions сам соберёт APK и обновит сайт.

Если `build.mjs` пишет `Patch point not found`, значит в новом `index.html` пропал `<meta name="viewport">`, `</head>` или `</body>`.

## Сборка

```bash
npm ci
node build.mjs            # PWA в www/
npm run serve             # открыть http://localhost:8080 (с телефона в той же сети по IP компьютера)
npx cap sync android      # скопировать www/ в Android-проект
cd android && ./gradlew assembleDebug   # нужен Android SDK (Android Studio)
```

Иконки и заставка для PWA и Android пересобираются из `src/icons/gear.svg` командой `node tools/make-icons.mjs`.

## GitHub Actions

- `mobile-android.yml` собирает APK при каждом пуше в `mobile/`. Файл лежит в артефакте `PresetEditor-android` на странице запуска.
- `mobile-pages.yml` публикует PWA на GitHub Pages при пуше в `main`.

APK подписывается ключом из секретов репозитория `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` и `ANDROID_KEY_PASSWORD`. Без них собирается debug-APK. Ключ должен оставаться одним и тем же: Android ставит обновление поверх старой версии только при той же подписи, а удаление приложения стирает пресеты, сохранённые внутри него.

## Данные

Пресеты на телефоне хранятся отдельно от ПК и между собой не синхронизируются. Переносите их через «Экспорт JSON» и «Импорт пресета». У PWA в браузере и у APK тоже разные хранилища.
