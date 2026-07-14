# Changelog

All notable changes to the EasyAPI plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.1.7] - 2026-07-14

### Added
- feat(ai): enrich PSI perception tools with name resolution, type FQNs, and method bodies (#734)
- feat(ai): add loop-safety and chat-retry to rule authoring agent (#733)
- feat(ai): namespace per-app env vars across exporters and agent
- feat(ai): teach rule agent cross-endpoint workflow patterns (#732)
- feat(curl): add variable rendering, output options, pre-scripts, and reusable cURL builder (#731)

### Fixed
-  inherit mapping annotations from bounded-generic interfaces

### Improved
- chore: drop spec reference from LangChain4j build comment
- docs(readme): sync Architecture section with v3.0 channel EP and add YAML field conversion (#730)

---

## [3.1.6] - 2026-07-05

### Added
-  refactor BodyView to carry ObjectModel with render helpers (#726)
-  free-form Markdown export templates with i18n and remote source support (#1411)
-  support `###include <path-or-url>` directive for loading config from local files and remote URLs (#1410)

### Fixed
-  YAML export drops @ConfigurationProperties prefix (#728)
-  FieldsTo* resolves caret class instead of first class in file (#727)
-  respect method-level selection when exporting APIs

### Changed
-  restructure settings modules, rename Intelligent tab, group panel sections (#725)
-  consolidate exporters into self-contained channel folders with modular settings (#724)

### Improved
- docs: add DeepWiki badge to READMEs

---

## [3.1.5] - 2026-06-28

### Added
- feat(rules): add folder-based rule management with AI assistant
- feat(logging): hide EasyAPI console tool window when log level is SILENT (#1396)
- feat(logging): enforce logging channel discipline with SILENT default console (#1394)
- feat: add Postman environment sync with dashboard integration (#722)
- feat(logging): make the plugin self-explanatory when it fails

### Fixed
- fix: settings/AI-panel UI interactions (modality, stale proposals, unsaved edits)
- fix(test): add AwaitUtils and stabilize flaky ApiIndexManagerTest
- fix(dashboard): prevent header duplication when switching APIs in ApiDashboard (#1397)

### Improved
- build(skill): sync shared knowledge base to skill
- style: replace tool window icons for EasyAPI and API Dashboard (#1395)

---

## [3.1.4] - 2026-06-21

### Added
-  add YAML field format support with dynamic extension architecture
-  implement enum resolution spec with @JsonValue/@EnumValue and @see support

### Fixed
-  fix annotation expression bugs and add comprehensive integration tests for all extensions
-  preserve dashboard edits when switching between APIs
-  @Api ignored for class folder names
-  correct plugin id and extension point name typo (#720)

### Improved
- amend: extract ObjectModelVisitTracker to centralize visit-count logic
- docs: add git-commit skill, AGENTS.md, and fix skill accuracy issues

---

## [3.1.3] - 2026-06-15

### Added
-  add Hoppscotch export channel as beta feature (#715)
-  add toJson/toJson5 to script class context and define context interfaces
-  introduce LoggingHttpClient for request/response logging (#714)

### Fixed
-  replace fragile delay with waitForClass to fix flaky test
-  add delay after loadFile in ProjectClassAvailabilityServiceTest to prevent flaky failures
-  make ApiIndex.invalidate() atomic to prevent race condition
-  channel export actions missing from Keymap settings
-  invalidate cache on settings/branch change to prevent occasional test failures
-  annotation array attributes are stringified instead of expanded

### Improved
- test: replace jacoco with kotlinx kover for coverage reporting (#718)
- test: add variety of new unit tests across codebase (#717)
- test: add variety of new unit tests across codebase (#716)
- docs: remove contributing section and contributor image from readmes
- ci: fix pr-release workflow checkout and script injection
- build: fix github workflow permissions and add missing configs
- fix : method.return support generic canonical types
- chore: upgrade codecov-action from v5 to v7

---

## [3.1.2] - 2026-06-07

### Fixed
-  api.tag=#tag captures all @tag values instead of only the first
-  avoid double-zip when downloading PR artifact (#712)
-  properly handle array/collection return values from groovy scripts
- fix(source helper): fix source file resolution for library classes (#711)
-  Fix binary incompatibility with IntelliJ 2026.2 by removing Groovy runtime dependencies (#710)

### Improved
- chore: use org.jetbrains.changelog plugin for proper HTML rendering in marketplace

---

## [3.1.1] - 2026-06-01

### Added
-  implement pluggable exporter architecture with extension points (#707)
-  add setting to enable/disable API method gutter icon

### Fixed
-  handle IndexNotReadyException during IDEA Dumb Mode export
-  resolve inherited method/field handling in ClassType
-  wrap PsiAnnotation.owner access in read action in JaxRsClassExporter
-  resolve IntelliJ IDEA 2026.1 (IU-261) compatibility issues
-  resolve ClassCastException on double-shift Search Everywhere

### Changed
-  reorganize project structure with feature-based package layout (#708)

---

## [3.1.0] - 2026-05-11

### Fixed
-  DocMetadataResolver now correctly reads settings.pathMulti dynamically (#704)
-  remove duplicated comments when combining doc comments with rule-based docs (#703)

---

## [3.0.9] - 2026-05-08

### Fixed
-  support extracting comments from JAR classes and fix gRPC runtime Guava resolution (#701)
-  correct log level filtering semantic inversion (#700)

---

## [3.0.8] - 2026-04-28

### Added
-  show response JSON demo when viewing API endpoint (#696)
-  add depth and element count limits to prevent OOM in object model building (#694)

### Fixed
-  fix config parsing and extension sources loading issues
-  keep api dashboard in bottom tool window (#697)

### Improved
- test: add generic resolution coverage (#695)

---

## [3.0.7] - 2026-04-26

### Added
-  add Postman-compatible script execution with PmScriptExecutor as project service (#691)
-  support Scala/Kotlin/Groovy language adapters for PSI integration (#690)
-  improve settings panel usability (#688)
-  extract ClassNameConstants and InheritanceHelper with cached inheritance checks (#687)

### Fixed
-  pass Disposable to addDocumentListener to resolve deprecation warning (#692)

### Changed
-  extract shared EndpointBuilder from ClassExporters (#689)

### Improved
- docs: update readme with comprehensive project details
- test: add unit tests for ScriptSupport, EventBus, RequestPersistence, RepositoryService, MavenHelper, ModuleHelper (#686)
- test: add unit tests for IDE actions, settings, and utilities (#685)

---

## [3.0.6] - 2026-04-19

### Added
-  add variable resolution support in ApiDashboard (#684)
-  handle properties.prefix rule in FieldsToPropertiesAction (#682)
-  enhance script PSI context with class introspection methods and fix Swing dispatcher modality (#676)
-  remember export dialog options for better UX (#674)
-  add concurrent API scanning option for better performance (#673)
-  add rule-based configuration support with cache invalidation (#672)

### Improved
- chore: remove unused module rule (RuleKeys.MODULE, resolveModule, module.config) (#681)
- chore: remove unused MarkdownRender and related code (#680)
- test: add missing unit tests and fix test failures (#679)
- test: improve test coverage across multiple packages (#678)
- perf: optimize rule engine with Flow-based lazy evaluation (#677)
- perf: optimize exporter selection with framework availability caching (#675)

---

## [3.0.5] - 2026-04-15

### Added
-  support URL paste in API Search Everywhere with path variable matching (#659)

### Fixed
-  Postman workspace and collections stuck on loading in modal dialog (#662)
-  add missing same-package imports in test resources (#661)
-  resolve JacksonConfigIntegrationTest failures for @JsonUnwrapped and @JsonView (#660)
-  implement proper enum.use.custom resolution with unified enum handling (#656)
-  ensure fieldContext is always available for field rule evaluation (#655)
-  inject fieldContext correctly into Groovy rule engine scripts (#653)
-  resolve EDT threading violations in API dashboard navigation (#652)

### Changed
- refactor(ExtensionConfigRegistry): update list of known extensions

### Improved
- chore: remove YApi-specific features and deprecated configurations (#663)
- chore: cleanup unused test resource files (#658)
- amend: remove Recommend settings from plugin configuration (#657)
- amend: simplify SettingBinder and RuleEngine APIs (#654)

---

## [3.0.4] - 2026-04-13

### Added
-  add API endpoint selection panel to ExportDialog (#644)
-  add PsiType-aware rule evaluation for json.rule.convert (#643)
-  replace recommend config system with extension-based system (#642)
-  add ConfigSyncService with coroutine-based debounce for config reload (#640)
-  add on-demand Swagger config loading and API lifecycle events (#639)

### Fixed
-  resolve generic types in API method params (#1302) (#649)
-  export multipart and file-like params as FILE type (#648)
-  NegationParser should return null for null input (#647)
-  respect ExportDialog output path and handle user cancellation properly (#645)
-  resolve IDE freeze on startup (issue #1299) and improve script engine management (#638)
-  prevent OOM from circular ObjectModel references in markdown formatter (#637)
-  resolve export silent failure and threading issues (#636)

### Changed
-  refactor event system and remove deprecated ActionContext (#650)

### Improved
- perf: use fine-grained ReadAction scoping in API exporters (#646)
- chore: remove unused SPI infrastructure and MethodFilter (#641)
- chore: remove redundant documentation

---

## [3.0.3] - 2026-04-08

### Fixed
-  support inherited method annotations and dashboard navigation (#634)
-  support inherited API mappings and correct Feign metadata access (#633)

---

## [3.0.2] - 2026-04-06

### Added
-  add gRPC support (#629)
-  support file-type form params in API dashboard (#628)

### Fixed
-  correct version extraction in release workflow
-  catch CancellationException in ReadActionDispatcher to prevent unhandled coroutine exception (#627)

### Improved
- amend: improve HTTP client export and add format filtering (#630)

---

## [3.0.1] - 2026-04-02

### Added
-  add toString() methods to ScriptPsi contexts (#622)

### Fixed
-  expire setting binder cache after timeout (#625)
-  improve API scan performance and add auto-scan toggle (#624)
-  inherited controller export — superMethod perf, generic param scoping, resolver early-exit (#623)

---

## [3.0.0] - TBD

### Added
- Complete rewrite with modern Kotlin architecture
- Kotlin coroutines for all async operations
- Structured concurrency with custom IdeDispatchers
- Type-safe API models using sealed classes
- Hybrid dependency injection (IntelliJ services + OperationScope)
- Improved PSI threading with self-contained read/write actions
- Enhanced type resolution system with generic context support
- Modern event bus implementation using Kotlin Flow

### Changed
- Migrated from Java/Guice to Kotlin/coroutines
- Replaced ThreadPool with CoroutineScope
- Updated minimum IDE version to 2023.1.3
- Updated minimum JDK version to 17
- Kotlin version updated to 2.1.0

### Improved
- Better error handling with Result types
- More maintainable code structure
- Improved performance with structured concurrency
- Enhanced language adapter system (Java, Kotlin, Scala, Groovy)

### Migration Notes
- This is a major version with breaking changes in internal APIs
- Plugin ID remains the same for seamless user migration
- All user-facing features from v2.x are preserved
- Configuration and settings are compatible with previous versions

---

## [2.8.4] - Previous Release

For changes in version 2.x and earlier, please refer to the [easy-api repository](https://github.com/tangcent/easy-api).
