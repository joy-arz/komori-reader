# Source defaults and one-time adult-source confirmation

Baseline bbcbc95/app 0.8.11. Fresh enabled sources: DemonicScans and WeebCentral. MangaDex is non-adult but starts disabled by user request. Dedicated adult sources: HentaiZap, ImHentai, Nhentai, Manhwa18.com and Manhwa18.cc.

Before startup recovery can initialize Room/DataStore, Application records whether the existing settings file or Library database is present. The first source-policy transaction preserves an explicit enabled-source preference, including an empty one. Existing installs without that preference inherit the fixed 0.8.11 source defaults; fresh installs receive only the two requested defaults. Existing enabled adult sources grandfather the confirmation. The marker prevents later starts from replacing choices. No database schema or signing identity changes.

New adult enablement requires an affirmative 18+ response. The DataStore transaction commits both the source toggle and global confirmation together. Dismissal/negative response writes neither. Later adult sources and future re-enablement share the persisted confirmation. MangaDex never triggers this dialog. The view model reads current persisted settings when handling a toggle, avoiding an initial-state race.

Local checks: four JVM policy cases added; real DataStore migration and consent UI cases passed 2/2 in 7.008 seconds on the API 30 ARM emulator. The UI case declines, confirms, enables another source without warning and recreates the view model before another adult toggle. CI and signed delivery remain required.

Existing source integration fixtures now explicitly opt into the full catalog in the androidTest-only runner. Before doing so, the runner checks actual fresh-install defaults when Application reports no previous data, and records the initial state for CI artifacts. Dedicated consent cases use fresh isolated real DataStores and reset/restore the UI preferences. This setup does not ship in either production APK or iOS app.
