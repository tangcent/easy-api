# easy-api

[![CI](https://github.com/tangcent/easy-api/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-api/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-api/branch/master/graph/badge.svg?token=4DPGLAWL3Q)](https://codecov.io/gh/tangcent/easy-api)
[![](https://img.shields.io/jetbrains/plugin/v/12211?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12211)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/tangcent/easy-api.svg)](http://isitmaintained.com/project/tangcent/easy-api "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/tangcent/easy-api.svg)](http://isitmaintained.com/project/tangcent/easy-api "Percentage of issues still open")

[English](README.md) | 中文

## 功能特点

- [导出API文档到`Postman`](https://easyapi.itangcent.com/documents/export2postman.html)
- [导出API文档到`Markdown`](https://easyapi.itangcent.com/documents/export2markdown.html)

|       | 支持                                                                                                                                                                                                                                                          | 扩展支持                              |
|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| 语言    | java, kotlin                                                                                                                                                                                                                                                | scala                             |
| Web框架 | [spring](https://spring.io/), [feign](https://spring.io/projects/spring-cloud-openfeign), [jaxrs](https://www.oracle.com/technical-resources/articles/java/jax-rs.html) ([quarkus](https://quarkus.io/) 或 [jersey](https://eclipse-ee4j.github.io/jersey/)) | [dubbo](https://dubbo.apache.org) |
| 导出渠道  | [Postman](https://easyapi.itangcent.com/documents/export2postman.html), [Markdown](https://easyapi.itangcent.com/documents/export2markdown.html) , [Curl](https://curl.se/) , [HttpClient](https://plugins.jetbrains.com/plugin/13121-http-client)          | -                                 |
| 支持的框架 | javax.validation, Jackson, Gson                                                                                                                                                                                                                             | [swagger](https://swagger.io/)    |

## AI增强 (Beta)

EasyAPI集成了强大的AI功能来增强您的API文档工作流程：

### 特性

- **API翻译**：自动将API文档翻译成不同语言，保留技术术语的同时提供自然语言翻译。
- **方法返回类型推断**：使用AI分析方法代码并更准确地推断复杂返回类型，提高API文档的准确性。

### 配置

- **支持多种AI提供商**：可以配置您偏好的AI提供商（OpenAI, DeepSeek 等）和模型（GPT-4, DeepSeek-V3 等）。
- **API响应缓存**：通过缓存相同请求的AI响应来优化性能。

要启用这些功能，请在EasyAPI设置对话框中配置您的AI提供商和API令牌。

## 导航

* [指南](https://easyapi.itangcent.com/documents/index.html)
* [安装](https://easyapi.itangcent.com/documents/installation.html)
* [快速开始](https://easyapi.itangcent.com/documents/use.html)
* [设置](https://easyapi.itangcent.com/setting/index.html)
* [示例](https://easyapi.itangcent.com/demo/index.html)

## 运行应用

- `./gradlew :idea-plugin:runIde` 将运行一个安装了 EasyApi 的 IDEA 实例。
- `./gradlew clean test` 将运行所有测试用例。

## 环境要求

- IDE: Intellij Idea Ultimate / Intellij Idea Community 2021.2.1 或更高版本
- JDK: 11 或更高版本

## 兼容性

| JDK | IDE      | 状态 |
|-----|----------|----|
| 11  | 2021.2.1 | ✓  |
| 15  | 2022.2.3 | ✓  |
| 17  | 2023.1.3 | ✓  |

## Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)
- [百科](https://baike.baidu.com/item/javadoc)

## KDoc

- [kotlin-doc](https://kotlinlang.org/docs/reference/kotlin-doc.html)

## 贡献

您可以通过提出 issue 或提交 pull request 来提出功能请求。

以下是贡献者列表：

<a href="https://github.com/tangcent/easy-api/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=tangcent/easy-api" />
</a> 