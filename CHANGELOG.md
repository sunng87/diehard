# Change Log

## [0.11.3] - 2022-04-16

### Fixed

- Removed deprecated failsafe API usage

## [0.11.0] - 2021-11-22

### Changed

- Update failsafe to 3.0

## [0.10.3] - 2021-01-28

### Fixed

- Fixed gensym for arguments [#46]

## [0.10.2] - 2020-09-20

### Fixed

- Fixed issue that max-retries was set to -1 when using predefine policy [#43]

## [0.10.1] - 2020-09-17

### Changed

- Allow customization with predefined policy

### Fixed

- Removed obsolete docs about deflistener

## [0.10.0] - 2020-06-04

### Added

- `:failure-threshold-ratio-in-period` option for circuit breaker

### Changed

- Update failsafe to 2.4.0

## [0.9.4] - 2020-03-21

### Fixed

- Fixed regression issue `:retry/retry-policy-new` not found [#37]

## [0.9.3] - 2020-03-20

### Changed

- Allow catching `Throwable` in retry block [#36]

## [0.9.2] - 2020-01-29

### Added

- `interrupt?` option for timeout block [#35]

## [0.9.1] - 2019-12-18

### Fixed

- Resolved reflection warnings

## [0.9.0] - 2019-10-27

### Added

- Timeout API

## [0.8.2] - 2019-03-26

### Changed

- Fixed issue when `fallback` was provided falsey value

## [0.8.0] - 2019-02-19

### Changed

- Updated Failsafe to 2.0.1
- `on-complete` and `on-failure` callback in called when retry aborted
- listeners now can be configured with `defretrypolicy`

## [0.7.2] - 2018-09-17

### Changed

- Allow truthy value for policy check function
- Updated Failsafe to 1.1.0
- Updated clojure spec

## [0.7.1] - 2018-02-06

### Changed

- Fixed spec for backoff multiplier
- Updated Failsafe to 1.0.5

## [0.7.0] - 2017-12-10

### Added

- Bulkhead

### Changed

- Uses spec for input validation, requires Clojure 1.9+

### Changed

## [0.6.0] - 2017-07-09

### Added

- Added rate limiter

## [0.5.0] - 2016-11-25

### Changed

- Updated Failsafe to 1.0.0

## [0.4.0] - 2016-11-04

### Added

- `:circuit-breaker` option in retry block [#9]

## [0.3.5] - 2016-10-22

### Changed

- Update Failsafe to 0.9.5, bring back JDK7 support
- Fixed options being evaluated multiple times

## [0.3.3] - 2016-10-15

### Changed

- Update Failsafe to 0.9.4

## [0.3.2] - 2016-10-01

### Changed

- Remove some reflections

## [0.3.1] - 2016-09-09

### Changed

- Update Failsafe to 0.9.3

## [0.3.0] - 2016-08-07

### Added

- `defretrypolicy` and `deflistener` to define named policy/listener
- `:fallback` option for retry block
- `:jitter-factor` and `:jitter-ms` options for retry policy

### Changed

- Update Failsafe to 0.9.2

## [0.2.2] - 2016-07-09

### Added

- Circuit breaker block

[0.4.0]: https://github.com/sunng87/diehard/compare/0.4.0...0.5.0
[0.4.0]: https://github.com/sunng87/diehard/compare/0.3.5...0.4.0
[0.3.5]: https://github.com/sunng87/diehard/compare/0.3.3...0.3.5
[0.3.3]: https://github.com/sunng87/diehard/compare/0.3.2...0.3.3
[0.3.2]: https://github.com/sunng87/diehard/compare/0.3.1...0.3.2
[0.3.1]: https://github.com/sunng87/diehard/compare/0.3.0...0.3.1
[0.3.0]: https://github.com/sunng87/diehard/compare/0.2.2...0.3.0
[0.2.2]: https://github.com/sunng87/diehard/compare/0.1.0...0.2.2
