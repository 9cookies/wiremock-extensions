# Change Log

## 2021-mm-dd - <changes>

Important Notes: - none

Version: 0.2.1

Author: M.Scheepers

### Features
- none

### Improvements
- Support for multiple placeholders and keywords in SQS message callback queue names.

### Fixes
- none


## 2021-03-19 - Features - Improvements - Fixes

Important Notes: - none

Version: 0.2.0

Author: M.Scheepers

### Features
- The callback-simulator post serve extension now supports AWS SQS message publishing.

### Improvements
- Callback URLs may now contain multiple placeholders.
- Support for string embedded keyword usage.
- Introduced new keyword `ENV` to provide access to environment variables.
    - Callback authentication properties may contain keywords.
- Support `max` and optional `min` parameters for `Random` keyword.

### Fixes
- Bug during callback handling where exceptions where swallowed without logging.

### Dependencies
- bumped jackson-databind to 2.10.5.1

## 2020-05-19 - Improvements

Important Notes: - none

Version: 0.1.1

Author: M.Scheepers

### Improvements
- Introduced retry handling to the `CallbackSimulator`
- Docker image respects `JAVA_OPTS` and exposes additional port `7091`


## 2020-04-08 - Features - Improvements

Important Notes: - none

Version: 0.1.0

Author: M.Scheepers

### Features
- added `$(!OffsetDateTime)` keyword with similar semantics as `$(!Instant)` keyword.
- added `RequestTimeMatcher` extension that provides regular expression matching against UTC request time.

### Improvements
- added support for custom `X-Rps-TraceId` header to callback definitions.
- callback-simulator now persists callback definitions in the file system to reduce memory footprint of scheduled callbacks for load test scenarios to avoid OOM errors.   
- added ability to configure callback-simulator's thread pool size using `SCHEDULED_THREAD_POOL_SIZE` environment variable so that it doesn't become a bottle neck in load test scenarios.  


## 2019-07-31 - Enhancement

Important Notes: none

Version: 0.0.6

Author: M.Scheepers

### Changes
- introduced a callback simulator post serve action that will perform configurable requests to arbitrary services
- bumped jackson version to 2.9.9.1 due to [security issue](https://nvd.nist.gov/vuln/detail/CVE-2019-12814) 


## 2019-03-01 - Enhancement

Important Notes: none

Version: 0.0.5

Author: M.Scheepers

### Changes
- implemented response transformation for requests without content (e.g. `GET`) and also with content other than `application/json`.
	- Note: in either case replacement patterns will yield to `null` except for generated values.


## 2017-03-17 - Enhancement

Important Notes: none

Version: 0.0.4

Author: M.Scheepers

### Changes
- implemented support for string embedded JSON path replacements
- updated to Wire Mock dependency to version 2.5.1


## 2017-01-31 - Enhancement

Important Notes: none

Version: 0.0.3

Author: M.Scheepers

### Changes
- implemented support for computed time stamp generation in Unix format (epoch milliseconds)
- implemented support for multiple UUID and Random values
- verified support for JSON array handling


## 2016-11-25 - Enhancement

Important Notes: none

Version: 0.0.2

Author: M.Scheepers

### Changes

- implemented support for computed time stamp generation
- moved to repository to github company account


## 2016-11-23 - Release of initial version

Important Notes: none

Version: 0.0.1

Author: M.Scheepers

### Changes

- implemented JsonBodyTransformer
- added support for current time stamp generation
- added support for UUID generation
