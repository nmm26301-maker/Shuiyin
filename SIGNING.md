# 原始开发签名备份

本文件与 tooling/development.jks 一起备份，沿用水印工坊 0.6.6 及此前版本的开发签名。

| 项目 | 值 |
| --- | --- |
| 签名文件 | `tooling/development.jks` |
| Keystore 类型 | PKCS12（文件名保留 development.jks） |
| 密钥别名（keyAlias） | `androiddebugkey` |
| Keystore 密码（storePassword） | `android` |
| 私钥密码（keyPassword） | `android` |
| 证书 SHA-256 | `7836a7e960d91ec268d48048c0505e84fbda3bdeafe2648c5f24bc6aef8b0bdb` |

这是原项目已有的开发签名；该备份没有生成或替换密钥。Gradle 的 development signingConfig 和 tooling/build-offline.py 使用此文件与密码。

签名文件 SHA-256：`4cc644c8f203347c4b063a3c0798afc9353fc1e24391b618200db871a927d439`。

核对命令：

```bash
keytool -list -v -keystore tooling/development.jks -storepass android -alias androiddebugkey
```

完整构建方法见 README.md；版本与安装包核验记录见 RELEASE-0.6.6.json。
