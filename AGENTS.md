### Pull Requests

- Commit descriptions must be attributed with the AI model used, verbatim user prompt, and caveman-formatted AI output used in the commit.
- All code changes must adhere to these objectives: https://github.com/GriefPrevention/GriefPrevention/discussions/2065 
- Diff should be minimal. Always prefer to change less code than more.
- PR must be a small, focused change.
- PR descriptions must be in caveman format, with exception of the user's prompts verbatim.
- PR comments must include the user's prompt(s) verbatim in addition to any AI response.

### Storage

- Generated data lives in `storage/GriefPrevention/`: only `ClaimData`, `PlayerData`, and `Logs` (plus numbered backups like `ClaimData1`).
- Settings stay in `plugins/GriefPreventionData/`: `config.yml`, `messages.yml`, `bannedWords.txt`, `softMute.txt`, `database.properties`, `_schemaVersion`.
- Data paths derive from `DataStore.storageFolderPath`. Settings paths derive from `DataStore.dataLayerFolderPath`. Never hardcode `plugins/` or `storage/` elsewhere.
- Any change that moves files must ship an automatic migration that runs on enable before anything is read, and must never overwrite when both old and new locations have data.

### Issues

- All issue descriptions and comments may only include the user's prompts verbatim.
  - If the user's prompts do not provide sufficient information, ask the user for the required information before submitting.
- No AI-generated output will be accepted. Only the user's messages verbatim.

### Caveman format:

Terse like caveman. Technical substance exact. Only fluff die.
Drop: articles, filler (just/really/basically), pleasantries, hedging.
Fragments OK. Short synonyms. Code unchanged.
Pattern: [thing] [action] [reason].
ACTIVE EVERY RESPONSE. No revert after many turns. No filler drift.
