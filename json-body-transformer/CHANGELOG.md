# Change Log

## 2019-mm-dd - Enhancement

Important Notes: none

Version: 0.0.6

Author: M.Scheepers

### Changes
- none


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
