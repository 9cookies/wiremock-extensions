# Release process

## Prepare and perform a release

Given that master contains all changes to be deployed and current directory is wiremock-extensions root folder call the following commands to prepare the release locally:

```
git checkout master
git pull
mvn versions:set -DremoveSnapshot
```
> :warning: if the change log contains changes / notes in the Feature section and the current version is e.g. `0.0.3-SNAPSHOT` then instead of `mvn versions:set -DremoveSnapshot` the new version must be specified when invoking maven like so `mvn versions:set -DnewVersion=0.1.0` (see [versions-maven-plugin](https://www.mojohaus.org/versions-maven-plugin/set-mojo.html#newVersion)).
A newly introduced feature should always increase the minor version, bug fixes and improvements only increase the incremental version. If there are breaking changes (like in the API), the major version has to be incremented.
> :bulb: in this case most likely also the version in the `wiremock-extensions/CHANGELOG.md` has to be updated.

Adapt the `wiremock-extensions/CHANGELOG.md`.
- adapt the date by replacing `mm-dd` by month and day
- adapt the title by replacing `<changes>` with titles of all sections which contain change notes
- remove empty sections e.g.

```
## Fixes
- none
```
- double check with commits since last deploy that all relevant changes are mentioned in the change log.

Deploy the artifacts and push the docker image like so:

```
mvn clean deploy
git commit -am "release of [current project version]"
git push
```
If everything is fine move over to [github/wiremock-extensions](https://github.com/9cookies/wiremock-extensions/releases) and create a new release:
- create a tag with current project version
- enter release title and description according to CHANGELOG.md
- click `Publish release` button to conclude the release

## Prepare next version

In order to prepare the next development version call the following commands locally:

```
git checkout dev
git pull
git merge --no-commit master
mvn versions:set -DnextSnapshot
```
> :bulb: next snapshot version can be entered manually when running `mvn versions:set -DnextSnapshot=true`

Update the `wiremock-extensions/CHANGELOG.md` and add a new "empty" next version paragraph by pasting the following template:

```
## 2021-mm-dd - <changes>

Important Notes: - none

Version: [new project version]

Author: - none

### Features
- none

### Improvements
- none

### Fixes
- none
```
> :warning: the version in the change log should never contain `-SNAPSHOT` suffix!

Finally call

```
git commit -am "bumped version to [new project snapshot version]"
git push
```
Now everything is set for the next development cycle :raised_hands:
