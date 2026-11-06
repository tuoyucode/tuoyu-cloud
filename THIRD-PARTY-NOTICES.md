# Third-Party Notices

本项目（tuoyu-cloud）整体以 MIT 协议发布，见 [LICENSE](LICENSE)。本项目中包含的部分第三方代码和文件，仍然适用其原有协议，具体如下。

通过 Maven 引入的依赖不随本仓库分发，它们的协议见各自的发布包。

---

## 1. 包含的第三方源代码

### RuoYi-Cloud-Plus

本项目包含源自以下项目的代码：

- 项目：RuoYi-Cloud-Plus
- 协议：MIT License

```
MIT License

Copyright (c) 2022 RuoYi-Cloud-Plus

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### 带有原始协议头的源文件

以下文件包含源自其他开源项目的代码，**文件头中的原始版权与协议声明必须保留**：

| 文件 | 来源 | 协议 |
|---|---|---|
| `tuoyu-framework/tuoyu-common-redis/src/main/java/org/tuoyu/common/redis/manager/PlusSpringCacheManager.java` | Redisson | Apache License 2.0 |
| `tuoyu-framework/tuoyu-common-seata/src/main/java/com/alibaba/cloud/seata/rest/SeataRestTemplateInterceptor.java` | Spring Cloud Alibaba | Apache License 2.0 |
| `tuoyu-framework/tuoyu-common-seata/src/main/java/com/alibaba/cloud/seata/web/SeataHandlerInterceptor.java` | Spring Cloud Alibaba | Apache License 2.0 |

---

## 2. 随仓库附带的第三方文件

| 文件 | 来源 | 协议 |
|---|---|---|
| `tuoyu-framework/tuoyu-common-web/src/main/resources/ip2region_v4.xdb` | ip2region（离线 IP 地址库） | Apache License 2.0 |
| `deploy/docker/rabbitmq/rabbitmq_delayed_message_exchange-3.13.0.ez` | RabbitMQ Delayed Message Plugin（可选组件） | Mozilla Public License 2.0 |

> 以上协议信息以各项目官方发布的协议为准。
>
> ShardingSphere-Proxy 所需的 MySQL 驱动（GPL-2.0）不随仓库分发，需要部署时自行下载，见 `deploy/docker/shardingproxy/ext-lib/README.md`。

---

## 3. 使用 LGPL 协议的 Maven 依赖

以下依赖不随本仓库分发，但会被打进可执行 jar 与镜像。它们未经修改，只作为独立的库使用，本项目自身的代码不受 LGPL 约束；使用者可以按 LGPL 替换这些库。

| 依赖 | 版本 | 协议 | 使用位置 | 源码 |
|---|---|---|---|---|
| `com.googlecode.aviator:aviator` | 5.4.3 | GNU LGPL | SnailJob 服务端（`tuoyu-snailjob-server`）的工作流条件表达式 | https://github.com/killme2008/aviator |
| `com.xkcoding.http:simple-http` | 1.0.5 | GNU LGPL v3.0 | JustAuth 的 HTTP 请求层，第三方登录（`tuoyu-common-social`）使用 | https://github.com/xkcoding/simple-http |
