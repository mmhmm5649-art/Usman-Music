# USMAN Music Player - Universal Declarative Extension Schema (v1.0)

This document defines the stable universal declarative extension schema used by USMAN Music Player to safely import, validate, and execute REST-based music providers without executing arbitrary binary code (DCL).

## 1. File Formats Supported
- **`.json`**: Standalone declarative manifest file.
- **`.epk` / `.eapk` (Packaged Archive)**: ZIP archive containing `epk.json` (or `manifest.json`) at the root and an optional `entryPoint` configuration (e.g., `endpoints.json`).

---

## 2. Manifest Schema (`epk.json` / `manifest.json`)

```json
{
  "manifest": {
    "id": "com.example.provider.music",
    "name": "Example Music Provider",
    "version": "1.0.0",
    "author": "Community Developer",
    "description": "Secure declarative REST music provider for USMAN",
    "license": "Open Source",
    "permissions": ["INTERNET", "STREAMING"],
    "supportedLanguages": ["Telugu", "Hindi", "English"],
    "playbackType": "FULL",
    "type": "CONTENT_RESOLVER",
    "entryPoint": "endpoints.json"
  },
  "searchConfig": {
    "urlTemplate": "https://archive.org/advancedsearch.php?q={query}&output=json",
    "httpMethod": "GET",
    "headers": {
      "User-Agent": "USMAN-Music-Extension/1.0"
    },
    "tracksJsonPath": "response.docs",
    "idField": "identifier",
    "titleField": "title",
    "artistField": "creator",
    "albumField": "collection",
    "durationField": "length",
    "durationUnit": "seconds",
    "streamUrlTemplate": "https://archive.org/download/{id}/{id}.mp3",
    "artworkUrlField": "identifier"
  }
}
```

---

## 3. Field Specifications

### Manifest Fields (`manifest`)
- `id` (String, Required): Unique reverse-DNS identifier (e.g., `usman.ext.archiveorg`).
- `name` (String, Required): Human-readable display name.
- `version` (String, Optional): Semantic version string (default: `"1.0.0"`).
- `author` (String, Optional): Creator or maintainer name.
- `description` (String, Optional): Brief overview of the provider catalog.
- `license` (String, Optional): Licensing terms.
- `permissions` (Array of Strings): Required capabilities (must include `"INTERNET"` and `"STREAMING"`).
- `playbackType` (String): `"FULL"` (full-length audio streaming), `"EXTERNAL_SERVICE"` (official app handoff), or `"PREVIEW"`.
- `entryPoint` (String, Optional): Path to a secondary JSON configuration file inside a packaged `.epk` archive.

### Search & Stream Configuration Fields (`searchConfig`)
- `urlTemplate` (String, Required): HTTPS search URL containing `{query}` and `{limit}` placeholders. **Must use HTTPS**.
- `httpMethod` (String): HTTP method (`"GET"` or `"POST"`).
- `headers` (Object): Custom HTTP headers to inject into search requests.
- `tracksJsonPath` (String): Dot-separated JSON path to locate the array of track objects in the response (e.g., `response.docs` or `data.tracks`).
- `idField`, `titleField`, `artistField`, `albumField`, `durationField` (Strings): JSON keys mapping to track metadata.
- `durationUnit` (String): `"seconds"` or `"milliseconds"`.
- `streamUrlField` / `streamUrlTemplate` (String): Direct field or URL template (`https://cdn.example.com/{id}.mp3`) to resolve authorized full-length audio streams.

---

## 4. GitHub Repository Index Schema (`usman_extension_repository.json` / `repo.json`)

A GitHub extension repository provides a central JSON catalog listing available declarative music extensions for discovery, installation, and automatic updates.

```json
{
  "name": "USMAN Community Extension Hub",
  "description": "Official community-maintained declarative REST music providers for USMAN",
  "version": "1.0.0",
  "extensions": [
    {
      "id": "usman.ext.telugu.archive",
      "name": "Telugu Audio Archive",
      "version": "1.1.0",
      "author": "Open Audio Community",
      "description": "Telugu classical audio streaming and broadcasts",
      "license": "Public Domain",
      "iconUrl": "https://archive.org/images/logo.png",
      "downloadUrl": "https://raw.githubusercontent.com/usman-music/extensions/main/sample_telugu_archive.epk",
      "supportedLanguages": ["Telugu", "Hindi", "English"],
      "playbackType": "FULL",
      "type": "CONTENT_RESOLVER"
    }
  ]
}
```

### Repository Field Specifications
- `name` (String, Required): Display name of the extension repository.
- `description` (String, Optional): Summary of extensions offered.
- `version` (String, Optional): Repository catalog version string.
- `extensions` (Array of Objects): Array of extension package descriptors available for installation/update.
  - `downloadUrl` (String, Required): HTTPS URL pointing to the raw downloadable `.epk` package or `.json` manifest. **Must use HTTPS**.

