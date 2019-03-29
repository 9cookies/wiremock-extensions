
This readme contains additional recommendations regarding the Eclipse setup.

# Formattings and styles

In addition to the Eclipse configurations in this directory the following settings should be applied
via global preferences:

- Java / Editor
	- Content Assist / Favorites
		- New Type
			- org.assertj.core.api.Assertions
			- org.testng.Assert
	- Save actions
		- Format source code + format edited lines.
		- Additional actions / Configure
			- Code Organizing
				- Remove all trailing white space
					- All lines
			- Unnecessary Code
				- Remove unused imports
- XML
	- XML Files
		- Editor
			- Line Width: 120


# Eclipse Plugins

## Static code analysis

It is recommended to add the code analysis plugins for checkstyle/PMD/findbugs to Eclipse to recognize violations
early. Simply install the m2e-code-quality plugin (https://github.com/m2e-code-quality/m2e-code-quality) that supports
installing and configuring the plugins based on the same settings as the maven plugins are using.

Use the following Eclipse update site for that: http://m2e-code-quality.github.io/m2e-code-quality/site/latest/
