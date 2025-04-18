# easy-api

[![CI](https://github.com/tangcent/easy-api/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-api/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-api/branch/master/graph/badge.svg?token=4DPGLAWL3Q)](https://codecov.io/gh/tangcent/easy-api)
[![](https://img.shields.io/jetbrains/plugin/v/12211?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12211)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/tangcent/easy-api.svg)](http://isitmaintained.com/project/tangcent/easy-api "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/tangcent/easy-api.svg)](http://isitmaintained.com/project/tangcent/easy-api "Percentage of issues still open")

English | [中文](README_CN.md)

## Feature

- [Export API Doc To`Postman`](https://easyapi.itangcent.com/documents/export2postman.html)
- [Export API Doc To`Markdown`](https://easyapi.itangcent.com/documents/export2markdown.html)

|            | Support                                                                                                                                                                                                                                                                                                                            | Extended Support                  |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| language   | java, kotlin                                                                                                                                                                                                                                                                                                                       | scala                             |
| web        | [spring](https://spring.io/), [feign](https://spring.io/projects/spring-cloud-openfeign), [jaxrs](https://www.oracle.com/technical-resources/articles/java/jax-rs.html) ([quarkus](https://quarkus.io/) or [jersey](https://eclipse-ee4j.github.io/jersey/))                                                                       | [dubbo](https://dubbo.apache.org) |
| channels   | [Postman](https://easyapi.itangcent.com/documents/export2postman.html), [Markdown](https://easyapi.itangcent.com/documents/export2markdown.html) , [Curl](https://curl.se/)                                                                                 , [HttpClient](https://plugins.jetbrains.com/plugin/13121-http-client) | -                                 |
| frameworks | javax.validation, Jackson, Gson                                                                                                                                                                                                                                                                                                    | [swagger](https://swagger.io/)    |

## AI Powered (Beta)

EasyAPI includes powerful AI capabilities to enhance your API documentation workflow:

### Features

- **API Translation**: Automatically translate your API documentation to different languages, preserving technical terms
  while providing natural language translations.
- **Method Return Type Inference**: Use AI to analyze method code and more accurately infer complex return types,
  improving API documentation accuracy.

### Configuration

- **Support for Multiple AI Providers**: Configure your preferred AI provider (OpenAI, DeepSeek, etc.) and models (
  GPT-4, DeepSeek-V3, etc.).
- **API Response Caching**: Optimize performance by caching AI responses for identical requests.

To enable these features, configure your AI provider and API token in the EasyAPI settings dialog.

## Navigation

* [Guide](https://easyapi.itangcent.com/documents/index.html)
* [Installation](https://easyapi.itangcent.com/documents/installation.html)
* [Quick Start](https://easyapi.itangcent.com/documents/use.html)
* [Config](https://easyapi.itangcent.com/setting/index.html)
* [Demo](https://easyapi.itangcent.com/demo/index.html)

## Run application

- `./gradlew :idea-plugin:runIde` will runs an IDEA instance with the EasyApi installed.
- `./gradlew clean test` will run all test case.

## Requirements

- IDE: Intellij Idea Ultimate / Intellij Idea Community 2021.2.1 or higher
- JDK: Version 11 or higher

## Compatibility

| JDK | IDE      | status |
|-----|----------|--------|
| 11  | 2021.2.1 | ✓      |
| 15  | 2022.2.3 | ✓      |
| 17  | 2023.1.3 | ✓      |

## Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)

## KDoc

- [kotlin-doc](https://kotlinlang.org/docs/reference/kotlin-doc.html)

## Contributing

You can propose a feature request opening an issue or a pull request.

Here is a list of contributors:

<a href="https://github.com/tangcent/easy-api/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=tangcent/easy-api" />
</a>
