// Browser/mobile replacement for the Electron workspace bridge (main.js + preload.js).
// Same API as window.presetWorkspace in the desktop app, but files live in IndexedDB
// under the same relative layout: Presets/, Metadata/, Assets/Covers/, Series/, Backups/.
(function () {
  'use strict';

  const DB_NAME = 'preset-editor-workspace';
  const STORE = 'files';
  const ROOT_LABEL = 'Хранилище приложения (IndexedDB)';
  const METADATA_SCHEMA_VERSION = 3;
  const DEFAULT_COVER_FRAME = { width: 1200, height: 675 };
  const DEFAULT_COVER_CROP = { x: 0, y: 0, width: 1, height: 1, rotation: 0 };

  // ---- tiny virtual file system -------------------------------------------------

  let dbPromise = null;
  function openDb() {
    if (!dbPromise) {
      dbPromise = new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, 1);
        request.onupgradeneeded = () => request.result.createObjectStore(STORE);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      });
    }
    return dbPromise;
  }

  async function tx(mode, fn) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, mode);
      const store = transaction.objectStore(STORE);
      let result;
      Promise.resolve(fn(store, value => { result = value; })).catch(reject);
      transaction.oncomplete = () => resolve(result);
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
  }

  function enoent(filePath) {
    const error = new Error(`ENOENT: ${filePath}`);
    error.code = 'ENOENT';
    return error;
  }

  const vfs = {
    async read(filePath) {
      const value = await tx('readonly', (store, done) => {
        const req = store.get(filePath);
        req.onsuccess = () => done(req.result);
      });
      if (value === undefined) throw enoent(filePath);
      return value;
    },
    async write(filePath, content) {
      await tx('readwrite', store => { store.put(String(content), filePath); });
    },
    async remove(filePath) {
      await tx('readwrite', store => { store.delete(filePath); });
    },
    async exists(filePath) {
      const key = await tx('readonly', (store, done) => {
        const req = store.getKey(filePath);
        req.onsuccess = () => done(req.result);
      });
      return key !== undefined;
    },
    async list(dir) {
      const prefix = dir.endsWith('/') ? dir : `${dir}/`;
      const keys = await tx('readonly', (store, done) => {
        const req = store.getAllKeys();
        req.onsuccess = () => done(req.result);
      });
      return keys
        .filter(key => typeof key === 'string' && key.startsWith(prefix) && !key.slice(prefix.length).includes('/'))
        .map(key => key.slice(prefix.length));
    }
  };

  // ---- helpers ported from main.js ---------------------------------------------

  function randomUUID() {
    if (crypto.randomUUID) return crypto.randomUUID();
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  function isPresetId(value) {
    return typeof value === 'string'
      && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  }

  function ensurePresetId(value) {
    return isPresetId(value) ? value : randomUUID();
  }

  const metadataPath = presetId => `Metadata/${presetId}.meta.json`;
  const workspacePresetRelativePath = presetId => `Presets/${presetId}.json`;
  const seriesPath = seriesId => `Series/${seriesId}.series.json`;

  function basename(value) {
    return String(value || '').split(/[\\/]/).pop();
  }

  function extname(value) {
    const name = basename(value);
    const index = name.lastIndexOf('.');
    return index > 0 ? name.slice(index) : '';
  }

  function safeJsonFileName(fileName) {
    let name = basename(fileName || 'preset.json')
      .replace(/[<>:"/\\|?*\x00-\x1F]/g, '_')
      .trim();
    if (!name) name = 'preset.json';
    if (!name.toLowerCase().endsWith('.json')) name += '.json';
    return name;
  }

  function normalizeRelative(value) {
    const parts = [];
    String(value || '').replace(/\\/g, '/').split('/').forEach(part => {
      if (!part || part === '.') return;
      if (part === '..') parts.pop();
      else parts.push(part);
    });
    return parts.join('/');
  }

  function safeWorkspaceRelativePath(relativePath, fallbackPath) {
    const raw = String(relativePath || '');
    const safe = raw && !raw.includes('\0') ? normalizeRelative(raw) : '';
    return safe || fallbackPath;
  }

  function timestampForFile(date = new Date()) {
    return date.toISOString().replace(/[:.]/g, '-').replace('T', '_').replace('Z', '');
  }

  function backupFileName(fileName) {
    const safeName = safeJsonFileName(fileName);
    const ext = extname(safeName) || '.json';
    const base = safeName.slice(0, -ext.length);
    return `${base}_${timestampForFile()}${ext}`;
  }

  function displayNameFromSource(sourceFileName) {
    const source = String(sourceFileName || 'Untitled Preset');
    const ext = extname(source);
    const base = basename(source).slice(0, ext ? -ext.length : undefined);
    return base.replace(/[-_]+/g, ' ').trim() || 'Untitled Preset';
  }

  function safeImageExtension(fileName, mime) {
    const ext = extname(fileName).toLowerCase();
    if (['.png', '.jpg', '.jpeg', '.webp', '.gif'].includes(ext)) return ext;
    if (mime === 'image/jpeg') return '.jpg';
    if (mime === 'image/webp') return '.webp';
    if (mime === 'image/gif') return '.gif';
    return '.png';
  }

  function clampNumber(value, min, max, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number)) return fallback;
    return Math.min(max, Math.max(min, number));
  }

  function normalizeCoverFrame(value) {
    return {
      width: Math.round(clampNumber(value?.width, 128, 4096, DEFAULT_COVER_FRAME.width)),
      height: Math.round(clampNumber(value?.height, 128, 4096, DEFAULT_COVER_FRAME.height))
    };
  }

  function normalizeCoverCrop(value) {
    return {
      x: clampNumber(value?.x, 0, 1, DEFAULT_COVER_CROP.x),
      y: clampNumber(value?.y, 0, 1, DEFAULT_COVER_CROP.y),
      width: clampNumber(value?.width, 0.02, 1, DEFAULT_COVER_CROP.width),
      height: clampNumber(value?.height, 0.02, 1, DEFAULT_COVER_CROP.height),
      rotation: 0
    };
  }

  function normalizeFavoritePromptEntry(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
    const content = typeof value.content === 'string' ? value.content : '';
    const promptId = String(value.promptId || value.identifier || '').trim();
    const name = String(value.name || promptId || '').trim();
    if (!name && !content) return null;
    const role = ['system', 'user', 'assistant'].includes(String(value.role)) ? String(value.role) : 'system';
    const addedAt = Number(value.addedAt);
    return {
      id: String(value.id || randomUUID()).trim() || randomUUID(),
      promptId,
      name: name || 'Untitled prompt',
      role,
      content,
      chars: content.length,
      enabled: typeof value.enabled === 'boolean' ? value.enabled : true,
      addedAt: Number.isFinite(addedAt) ? addedAt : Date.now()
    };
  }

  function normalizeFavoritePrompts(value) {
    if (!Array.isArray(value)) return [];
    const seenIds = new Set();
    const items = [];
    value.forEach(item => {
      const normalized = normalizeFavoritePromptEntry(item);
      if (!normalized || seenIds.has(normalized.id)) return;
      seenIds.add(normalized.id);
      items.push(normalized);
    });
    return items;
  }

  function cleanPresetObject(presetData) {
    const clean = presetData && typeof presetData === 'object' && !Array.isArray(presetData)
      ? JSON.parse(JSON.stringify(presetData))
      : {};
    delete clean.presetId;
    delete clean.metadata;
    delete clean.schemaVersion;
    delete clean.coverRelativePath;
    delete clean.favoritePrompts;
    delete clean.favoritePromptIds;
    return clean;
  }

  function getPresetPrompts(presetData) {
    return Array.isArray(presetData?.prompts) ? presetData.prompts : [];
  }

  function getOrderEntries(presetData) {
    const first = Array.isArray(presetData?.prompt_order) ? presetData.prompt_order[0] : null;
    return Array.isArray(first?.order) ? first.order : [];
  }

  function isPromptEnabledForStats(presetData, prompt) {
    const id = String(prompt?.identifier || '');
    const entry = getOrderEntries(presetData).find(item => String(item?.identifier || '') === id);
    if (entry && typeof entry.enabled === 'boolean') return entry.enabled;
    if (typeof prompt?.enabled === 'boolean') return prompt.enabled;
    return true;
  }

  function calculatePresetStats(presetData) {
    const prompts = getPresetPrompts(presetData);
    const enabledCount = prompts.filter(prompt => isPromptEnabledForStats(presetData, prompt)).length;
    const variableNames = new Set();
    prompts.forEach(prompt => {
      const content = typeof prompt?.content === 'string' ? prompt.content : '';
      const regex = /\{\{\s*(?:getvar|setvar|addvar|incvar|decvar|flushvar|getglobalvar|setglobalvar|addglobalvar|incglobalvar|decglobalvar|flushglobalvar)::\s*([^}:]+)[^}]*\}\}/gi;
      let match;
      while ((match = regex.exec(content))) {
        const name = String(match[1] || '').trim();
        if (name) variableNames.add(name.toLowerCase());
      }
    });
    return {
      promptCount: prompts.length,
      enabledCount,
      disabledCount: Math.max(0, prompts.length - enabledCount),
      variableCount: variableNames.size
    };
  }

  function normalizeStats(value) {
    const num = key => (Number.isFinite(Number(value?.[key])) ? Number(value[key]) : 0);
    return {
      promptCount: num('promptCount'),
      enabledCount: num('enabledCount'),
      disabledCount: num('disabledCount'),
      variableCount: num('variableCount')
    };
  }

  function statsEqual(a, b) {
    const left = normalizeStats(a);
    const right = normalizeStats(b);
    return left.promptCount === right.promptCount
      && left.enabledCount === right.enabledCount
      && left.disabledCount === right.disabledCount
      && left.variableCount === right.variableCount;
  }

  function normalizeMetadata(presetId, metadata) {
    const now = new Date().toISOString();
    const value = metadata && typeof metadata === 'object' ? metadata : {};
    return {
      schemaVersion: METADATA_SCHEMA_VERSION,
      presetId,
      sourceFileName: String(value.sourceFileName || ''),
      displayName: String(value.displayName || ''),
      author: String(value.author || ''),
      description: String(value.description || ''),
      tags: Array.isArray(value.tags)
        ? value.tags.map(tag => String(tag).trim()).filter(Boolean)
        : [],
      status: ['Draft', 'Ready', 'Template', 'Archived'].includes(value.status) ? value.status : 'Draft',
      authorLink: String(value.authorLink || ''),
      presetRelativePath: String(value.presetRelativePath || ''),
      coverRelativePath: String(value.coverRelativePath || ''),
      coverFrame: normalizeCoverFrame(value.coverFrame),
      coverCrop: normalizeCoverCrop(value.coverCrop),
      favoritePrompts: normalizeFavoritePrompts(value.favoritePrompts),
      favoritePromptIds: Array.isArray(value.favoritePromptIds)
        ? [...new Set(value.favoritePromptIds.map(item => String(item || '').trim()).filter(Boolean))]
        : [],
      seriesId: value.seriesId || null,
      stats: normalizeStats(value.stats),
      createdAt: String(value.createdAt || now),
      updatedAt: String(value.updatedAt || now)
    };
  }

  function normalizePresetIdList(value) {
    const source = Array.isArray(value) ? value : [];
    const seen = new Set();
    const ids = [];
    source.forEach(item => {
      const id = String(item || '').trim();
      if (!isPresetId(id) || seen.has(id)) return;
      seen.add(id);
      ids.push(id);
    });
    return ids;
  }

  function normalizeSeries(seriesId, series) {
    const now = new Date().toISOString();
    const value = series && typeof series === 'object' ? series : {};
    const safeId = isPresetId(seriesId) ? seriesId : ensurePresetId(value.seriesId);
    return {
      schemaVersion: 1,
      seriesId: safeId,
      name: String(value.name || 'Untitled Series'),
      description: String(value.description || ''),
      coverRelativePath: value.coverRelativePath ? String(value.coverRelativePath) : null,
      presetIds: normalizePresetIdList(value.presetIds),
      createdAt: String(value.createdAt || now),
      updatedAt: String(value.updatedAt || now)
    };
  }

  function makeDefaultPresetData() {
    return {
      impersonation_prompt: "[Write your next reply from the point of view of {{user}}, using the chat history so far as a guideline for the writing style of {{user}}. Don't write as {{char}} or system. Don't describe actions of {{char}}.]",
      new_chat_prompt: '[Start a new Chat]',
      new_group_chat_prompt: '[Start a new group chat. Group members: {{group}}]',
      new_example_chat_prompt: '[Example Chat]',
      continue_nudge_prompt: '[Continue your last message without repeating its original content.]',
      scenario_format: '{{scenario}}',
      personality_format: '{{personality}}',
      group_nudge_prompt: '[Write the next reply only as {{char}}.]',
      wi_format: '{0}',
      prompts: [],
      prompt_order: [{ character_id: 100001, order: [] }]
    };
  }

  async function readJson(filePath) {
    return JSON.parse(await vfs.read(filePath));
  }

  async function writeJson(filePath, value) {
    await vfs.write(filePath, JSON.stringify(value, null, 2));
  }

  // ---- series / metadata / preset storage --------------------------------------

  async function loadSeriesFile(seriesId) {
    try {
      const safeId = ensurePresetId(seriesId);
      return normalizeSeries(safeId, await readJson(seriesPath(safeId)));
    } catch (error) {
      if (error.code !== 'ENOENT') console.warn('Failed to load series:', error);
      return null;
    }
  }

  async function saveSeriesFile(seriesId, series) {
    const safeId = ensurePresetId(seriesId);
    const normalized = normalizeSeries(safeId, {
      ...series,
      seriesId: safeId,
      updatedAt: series?.updatedAt || new Date().toISOString()
    });
    await writeJson(seriesPath(safeId), normalized);
    return normalized;
  }

  async function listSeriesFiles() {
    const fileNames = await vfs.list('Series');
    const seriesList = [];
    for (const fileName of fileNames) {
      const match = fileName.match(/^(.+)\.series\.json$/);
      if (!match || !isPresetId(match[1])) continue;
      const series = await loadSeriesFile(match[1]);
      if (series) seriesList.push(series);
    }
    return seriesList.sort((a, b) => String(b.updatedAt || '').localeCompare(String(a.updatedAt || '')));
  }

  async function clearPresetFromOtherSeries(presetId, targetSeriesId = null) {
    const seriesList = await listSeriesFiles();
    for (const series of seriesList) {
      if (series.seriesId === targetSeriesId) continue;
      if (!series.presetIds.includes(presetId)) continue;
      await saveSeriesFile(series.seriesId, {
        ...series,
        presetIds: series.presetIds.filter(id => id !== presetId),
        updatedAt: new Date().toISOString()
      });
    }
  }

  async function loadMetadataFile(presetId) {
    try {
      return normalizeMetadata(presetId, await readJson(metadataPath(presetId)));
    } catch (error) {
      if (error.code !== 'ENOENT') console.warn('Failed to load preset metadata:', error);
      return null;
    }
  }

  async function saveMetadataFile(presetId, metadata) {
    const normalized = normalizeMetadata(presetId, metadata);
    await writeJson(metadataPath(presetId), normalized);
    return normalized;
  }

  async function setPresetSeriesMetadata(presetId, seriesId) {
    const metadata = await loadMetadataFile(presetId);
    if (!metadata) return null;
    return saveMetadataFile(presetId, {
      ...metadata,
      seriesId: seriesId || null,
      updatedAt: new Date().toISOString()
    });
  }

  async function loadWorkspacePresetData(presetId, metadata = null) {
    const safeId = ensurePresetId(presetId);
    const relativePath = metadata?.presetRelativePath || workspacePresetRelativePath(safeId);
    return readJson(safeWorkspaceRelativePath(relativePath, workspacePresetRelativePath(safeId)));
  }

  async function saveWorkspacePresetData(presetId, presetData) {
    const safeId = ensurePresetId(presetId);
    const clean = cleanPresetObject(presetData);
    await writeJson(workspacePresetRelativePath(safeId), clean);
    return {
      clean,
      presetRelativePath: workspacePresetRelativePath(safeId),
      stats: calculatePresetStats(clean)
    };
  }

  async function readCoverDataUrl(coverRelativePath) {
    if (!coverRelativePath) return '';
    const relative = normalizeRelative(coverRelativePath);
    if (!relative.startsWith('Assets/Covers/')) return '';
    try {
      return await vfs.read(relative);
    } catch (error) {
      return '';
    }
  }

  async function writeCover(prefix, fileName, dataUrl) {
    const match = String(dataUrl || '').match(/^data:(image\/(?:png|jpeg|webp|gif));base64,([A-Za-z0-9+/=]+)$/);
    if (!match) throw new Error('Unsupported cover image format');
    const target = `Assets/Covers/${prefix}${safeImageExtension(fileName, match[1])}`;
    const previous = await vfs.list('Assets/Covers');
    for (const name of previous) {
      const full = `Assets/Covers/${name}`;
      if (name.startsWith(`${prefix}.`) && full !== target) await vfs.remove(full);
    }
    await vfs.write(target, dataUrl);
    return target;
  }

  function workspaceInfo() {
    return {
      root: ROOT_LABEL,
      presetsDir: `${ROOT_LABEL}/Presets`,
      metadataDir: `${ROOT_LABEL}/Metadata`,
      coversDir: `${ROOT_LABEL}/Assets/Covers`,
      seriesDir: `${ROOT_LABEL}/Series`,
      backupsDir: `${ROOT_LABEL}/Backups`,
      isDefault: true
    };
  }

  // ---- public API (mirrors preload.js) ------------------------------------------

  const api = {
    async getWorkspaceInfo() {
      await openDb();
      if (navigator.storage?.persist) navigator.storage.persist().catch(() => {});
      return workspaceInfo();
    },

    async ensureWorkspaceStructure() {
      return workspaceInfo();
    },

    async chooseWorkspaceRoot() {
      return { canceled: true, workspaceInfo: workspaceInfo() };
    },

    async resetWorkspaceRoot() {
      return workspaceInfo();
    },

    async getPresetFileInfo(fileName) {
      const safeName = safeJsonFileName(fileName);
      return { fileName: safeName, targetPath: safeName, exists: await vfs.exists(safeName) };
    },

    async savePresetJson(payload) {
      const safeName = safeJsonFileName(payload?.fileName);
      const content = String(payload?.content || '');
      let backupRelativePath = '';
      const exists = await vfs.exists(safeName);
      if (exists && payload?.backup) {
        backupRelativePath = `Backups/${backupFileName(safeName)}`;
        await vfs.write(backupRelativePath, await vfs.read(safeName));
      }
      await vfs.write(safeName, content);
      return { fileName: safeName, targetPath: safeName, existed: exists, backupRelativePath };
    },

    async listLibraryPresets() {
      const itemsById = new Map();

      for (const fileName of await vfs.list('Metadata')) {
        const match = fileName.match(/^(.+)\.meta\.json$/);
        if (!match || !isPresetId(match[1])) continue;
        const presetId = match[1];
        let metadata = await loadMetadataFile(presetId);
        if (!metadata) continue;
        let presetData = null;
        let presetExists = false;
        let missingReason = '';
        try {
          presetData = await loadWorkspacePresetData(presetId, metadata);
          presetExists = true;
        } catch (error) {
          if (metadata.presetRelativePath && metadata.presetRelativePath !== workspacePresetRelativePath(presetId)) {
            try {
              presetData = await loadWorkspacePresetData(presetId, { presetRelativePath: workspacePresetRelativePath(presetId) });
              presetExists = true;
              metadata = { ...metadata, presetRelativePath: workspacePresetRelativePath(presetId) };
            } catch (fallbackError) {
              presetExists = false;
            }
          }
          if (!presetExists) missingReason = 'Metadata only - preset file missing';
        }
        const stats = presetData ? calculatePresetStats(presetData) : null;
        let normalized = normalizeMetadata(presetId, {
          ...metadata,
          presetRelativePath: metadata.presetRelativePath || (presetExists ? workspacePresetRelativePath(presetId) : ''),
          stats: stats || metadata.stats
        });
        if (presetExists && (!statsEqual(metadata.stats, stats) || !metadata.presetRelativePath)) {
          normalized = await saveMetadataFile(presetId, {
            ...normalized,
            presetRelativePath: normalized.presetRelativePath || workspacePresetRelativePath(presetId),
            stats,
            updatedAt: metadata.updatedAt
          });
        }
        itemsById.set(presetId, {
          presetId,
          metadata: normalized,
          coverDataUrl: await readCoverDataUrl(normalized.coverRelativePath),
          presetExists,
          missingReason
        });
      }

      for (const fileName of await vfs.list('Presets')) {
        const match = fileName.match(/^(.+)\.json$/);
        if (!match || !isPresetId(match[1]) || itemsById.has(match[1])) continue;
        const presetId = match[1];
        let presetData = null;
        try {
          presetData = await readJson(`Presets/${fileName}`);
        } catch (error) {
          continue;
        }
        const metadata = await saveMetadataFile(presetId, {
          sourceFileName: fileName,
          presetRelativePath: workspacePresetRelativePath(presetId),
          displayName: displayNameFromSource(fileName),
          stats: calculatePresetStats(presetData),
          updatedAt: new Date().toISOString()
        });
        itemsById.set(presetId, { presetId, metadata, coverDataUrl: '', presetExists: true, missingReason: '' });
      }

      return [...itemsById.values()]
        .sort((a, b) => String(b.metadata.updatedAt || '').localeCompare(String(a.metadata.updatedAt || '')));
    },

    async loadWorkspacePreset(presetId) {
      const safeId = ensurePresetId(presetId);
      const metadata = await loadMetadataFile(safeId);
      const presetData = await loadWorkspacePresetData(safeId, metadata);
      const normalized = await saveMetadataFile(safeId, {
        ...(metadata || {}),
        sourceFileName: metadata?.sourceFileName || `${safeId}.json`,
        presetRelativePath: metadata?.presetRelativePath || workspacePresetRelativePath(safeId),
        stats: calculatePresetStats(presetData),
        updatedAt: metadata?.updatedAt || new Date().toISOString()
      });
      return {
        presetId: safeId,
        presetData,
        metadata: normalized,
        coverDataUrl: await readCoverDataUrl(normalized.coverRelativePath)
      };
    },

    async saveWorkspacePreset(presetId, presetData) {
      const safeId = ensurePresetId(presetId);
      const saved = await saveWorkspacePresetData(safeId, presetData || makeDefaultPresetData());
      const existingMetadata = await loadMetadataFile(safeId);
      const metadata = await saveMetadataFile(safeId, {
        ...(existingMetadata || {}),
        presetId: safeId,
        sourceFileName: existingMetadata?.sourceFileName || `${safeId}.json`,
        displayName: existingMetadata?.displayName || displayNameFromSource(existingMetadata?.sourceFileName || `${safeId}.json`),
        presetRelativePath: saved.presetRelativePath,
        stats: saved.stats,
        updatedAt: new Date().toISOString()
      });
      return { presetId: safeId, presetRelativePath: saved.presetRelativePath, stats: saved.stats, metadata };
    },

    async createWorkspacePreset(initialData) {
      const presetId = randomUUID();
      const presetData = cleanPresetObject(initialData || makeDefaultPresetData());
      const saved = await saveWorkspacePresetData(presetId, presetData);
      const now = new Date().toISOString();
      const metadata = await saveMetadataFile(presetId, {
        presetId,
        sourceFileName: `${presetId}.json`,
        presetRelativePath: saved.presetRelativePath,
        displayName: 'Untitled Preset',
        status: 'Draft',
        stats: saved.stats,
        createdAt: now,
        updatedAt: now
      });
      return { presetId, presetData, metadata, coverDataUrl: '' };
    },

    async importWorkspacePreset(payload) {
      const presetId = randomUUID();
      const sourceFileName = safeJsonFileName(payload?.fileName || 'imported-preset.json');
      const presetData = cleanPresetObject(payload?.presetData);
      if (!presetData || typeof presetData !== 'object' || Array.isArray(presetData) || !Array.isArray(presetData.prompts)) {
        throw new Error('Invalid preset data');
      }
      const saved = await saveWorkspacePresetData(presetId, presetData);
      const now = new Date().toISOString();
      const metadata = await saveMetadataFile(presetId, {
        presetId,
        sourceFileName,
        presetRelativePath: saved.presetRelativePath,
        displayName: displayNameFromSource(sourceFileName),
        status: 'Draft',
        stats: saved.stats,
        createdAt: now,
        updatedAt: now
      });
      return { presetId, presetData, metadata, coverDataUrl: '' };
    },

    async removeLibraryMetadata(presetId) {
      const safeId = ensurePresetId(presetId);
      await vfs.remove(metadataPath(safeId));
      return { presetId: safeId, removed: true };
    },

    async exportWorkspacePreset(presetId) {
      const safeId = ensurePresetId(presetId);
      const metadata = await loadMetadataFile(safeId);
      const presetData = cleanPresetObject(await loadWorkspacePresetData(safeId, metadata));
      const fileName = safeJsonFileName(metadata?.sourceFileName || `${metadata?.displayName || safeId}.json`);
      return { presetId: safeId, fileName, content: JSON.stringify(presetData, null, 2) };
    },

    async refreshLibraryStats(presetId) {
      const safeId = ensurePresetId(presetId);
      const metadata = await loadMetadataFile(safeId);
      const presetData = await loadWorkspacePresetData(safeId, metadata);
      const stats = calculatePresetStats(presetData);
      const normalized = await saveMetadataFile(safeId, {
        ...(metadata || {}),
        presetId: safeId,
        sourceFileName: metadata?.sourceFileName || `${safeId}.json`,
        presetRelativePath: metadata?.presetRelativePath || workspacePresetRelativePath(safeId),
        stats,
        updatedAt: new Date().toISOString()
      });
      return { presetId: safeId, stats, metadata: normalized };
    },

    async listLibrarySeries() {
      const items = [];
      for (const series of await listSeriesFiles()) {
        const normalized = normalizeSeries(series.seriesId, series);
        if (normalized.presetIds.length !== series.presetIds.length) {
          await saveSeriesFile(normalized.seriesId, normalized);
        }
        items.push({ series: normalized, coverDataUrl: await readCoverDataUrl(normalized.coverRelativePath) });
      }
      return items;
    },

    async createLibrarySeries() {
      const seriesId = randomUUID();
      const now = new Date().toISOString();
      const series = await saveSeriesFile(seriesId, {
        schemaVersion: 1,
        seriesId,
        name: 'Untitled Series',
        description: '',
        coverRelativePath: null,
        presetIds: [],
        createdAt: now,
        updatedAt: now
      });
      return { series, coverDataUrl: '' };
    },

    async saveLibrarySeries(seriesId, series) {
      const safeId = ensurePresetId(seriesId);
      const existing = await loadSeriesFile(safeId);
      const saved = await saveSeriesFile(safeId, {
        ...(existing || {}),
        ...(series || {}),
        seriesId: safeId,
        presetIds: normalizePresetIdList(series?.presetIds || existing?.presetIds),
        updatedAt: new Date().toISOString()
      });
      return { series: saved, coverDataUrl: await readCoverDataUrl(saved.coverRelativePath) };
    },

    async addPresetsToSeries(seriesId, presetIds) {
      const safeSeriesId = ensurePresetId(seriesId);
      const series = await loadSeriesFile(safeSeriesId);
      if (!series) throw new Error('Series not found');
      const merged = series.presetIds.slice();
      for (const presetId of normalizePresetIdList(presetIds)) {
        await clearPresetFromOtherSeries(presetId, safeSeriesId);
        if (!merged.includes(presetId)) merged.push(presetId);
        await setPresetSeriesMetadata(presetId, safeSeriesId);
      }
      const saved = await saveSeriesFile(safeSeriesId, { ...series, presetIds: merged, updatedAt: new Date().toISOString() });
      return { series: saved };
    },

    async setPresetSeries(presetId, seriesId) {
      const safePresetId = ensurePresetId(presetId);
      const targetSeriesId = seriesId && isPresetId(seriesId) ? seriesId : null;
      await clearPresetFromOtherSeries(safePresetId, targetSeriesId);
      if (targetSeriesId) {
        const series = await loadSeriesFile(targetSeriesId);
        if (!series) throw new Error('Series not found');
        const presetIds = series.presetIds.includes(safePresetId)
          ? series.presetIds
          : [...series.presetIds, safePresetId];
        await saveSeriesFile(targetSeriesId, { ...series, presetIds, updatedAt: new Date().toISOString() });
      }
      const metadata = await setPresetSeriesMetadata(safePresetId, targetSeriesId);
      return { presetId: safePresetId, seriesId: targetSeriesId, metadata };
    },

    async removePresetFromSeries(seriesId, presetId) {
      const safeSeriesId = ensurePresetId(seriesId);
      const safePresetId = ensurePresetId(presetId);
      const series = await loadSeriesFile(safeSeriesId);
      if (!series) throw new Error('Series not found');
      const saved = await saveSeriesFile(safeSeriesId, {
        ...series,
        presetIds: series.presetIds.filter(id => id !== safePresetId),
        updatedAt: new Date().toISOString()
      });
      const metadata = await loadMetadataFile(safePresetId);
      if (metadata?.seriesId === safeSeriesId) {
        await saveMetadataFile(safePresetId, { ...metadata, seriesId: null, updatedAt: new Date().toISOString() });
      }
      return { series: saved };
    },

    async reorderSeriesPreset(seriesId, presetId, direction) {
      const safeSeriesId = ensurePresetId(seriesId);
      const safePresetId = ensurePresetId(presetId);
      const series = await loadSeriesFile(safeSeriesId);
      if (!series) throw new Error('Series not found');
      const index = series.presetIds.indexOf(safePresetId);
      const target = index + (direction === 'down' ? 1 : -1);
      if (index < 0 || target < 0 || target >= series.presetIds.length) return { series };
      const presetIds = series.presetIds.slice();
      [presetIds[index], presetIds[target]] = [presetIds[target], presetIds[index]];
      const saved = await saveSeriesFile(safeSeriesId, { ...series, presetIds, updatedAt: new Date().toISOString() });
      return { series: saved };
    },

    async saveSeriesCoverImage(payload) {
      const safeId = ensurePresetId(payload?.seriesId);
      const dataUrl = String(payload?.dataUrl || '');
      const coverRelativePath = await writeCover(`series-${safeId}`, payload?.fileName, dataUrl);
      const series = await loadSeriesFile(safeId);
      if (series) {
        await saveSeriesFile(safeId, { ...series, coverRelativePath, updatedAt: new Date().toISOString() });
      }
      return { coverRelativePath, coverDataUrl: dataUrl };
    },

    async ensurePresetId(presetId) {
      return ensurePresetId(presetId);
    },

    async loadMetadata(presetId) {
      const safeId = ensurePresetId(presetId);
      const metadata = await loadMetadataFile(safeId);
      if (!metadata) return { metadata: null, coverDataUrl: '' };
      return { metadata, coverDataUrl: await readCoverDataUrl(metadata.coverRelativePath) };
    },

    async saveMetadata(presetId, metadata) {
      return saveMetadataFile(ensurePresetId(presetId), metadata);
    },

    async saveCoverImage(payload) {
      const safeId = ensurePresetId(payload?.presetId);
      const dataUrl = String(payload?.dataUrl || '');
      const coverRelativePath = await writeCover(safeId, payload?.fileName, dataUrl);
      return { coverRelativePath, coverDataUrl: dataUrl };
    }
  };

  if (!window.presetWorkspace && window.indexedDB) {
    window.presetWorkspace = api;
  }
  if (!window.presetApp) {
    window.presetApp = {
      relaunch: async () => {
        setTimeout(() => window.location.reload(), 150);
        return { ok: true };
      }
    };
  }
})();
