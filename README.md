<p align="center">
  <img src="app/src/main/res/drawable-nodpi/zhiyu_icon.png" width="88" height="88" alt="慧铭记账" />
</p>

<h1 align="center">Huiming Ledger · 慧铭记账</h1>

<p align="center"><strong>支付后自动记一笔，漏记时摇一摇。</strong></p>

<p align="center">识别、账本、统计都在手机上完成，无需账号、API Key 或服务器。</p>

<p align="center">Android 11+ · 离线 OCR · 本地账本 · GPL-3.0-only</p>

## 能做什么

- **支付自动记账**：识别受支持的微信、支付宝支付成功页，自动记录支出。
- **摇一摇与截图补记**：打开单笔账单详情摇一摇，或导入已有截图。
- **离线识别与商户图标**：提取金额、商户、时间与订单号，自动裁剪商户 Logo 或头像。
- **退款与账单核对**：记录原付、已退与实付金额，保留截图和识别原文，支持编辑、去重与删除撤销。
- **账本与统计**：手动记收支、自定义分类、搜索账单，查看月度分类占比与每日收支趋势。
- **资产与负债**：管理银行卡、微信、支付宝及现金账户，手动维护余额与净资产。
- **备份与迁移**：完整 ZIP 备份恢复、CSV 导入导出，支持钱迹模板。

## 界面预览

以下为应用在 Android 模拟器中**实际运行的页面截图**，使用示例账单与账户数据；点击图片可放大。

| 首页账本 | 收支统计 | 资产管理 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-ledger.webp" width="250" alt="首页账本，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-statistics.webp" width="250" alt="收支统计，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-assets.webp" width="250" alt="资产管理，Android 应用实际运行，示例数据" /> |

| 手动记账 | 截图智能导入 | 我的 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-manual.webp" width="250" alt="手动记账，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-import.webp" width="250" alt="截图智能导入，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-settings.webp" width="250" alt="我的，Android 应用实际运行，示例数据" /> |

<details>
<summary>展开查看账单、分类、账户与备份页面</summary>

| 账单详情 | 账单搜索 | 每日收支趋势 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-record-detail.webp" width="250" alt="账单详情，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-search.webp" width="250" alt="账单搜索，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-daily-trend.webp" width="250" alt="每日收支趋势，Android 应用实际运行，示例数据" /> |

| 分类账单明细 | 自定义分类 | 月份选择 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-category-detail.webp" width="250" alt="分类账单明细，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-categories.webp" width="250" alt="自定义分类，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-month-picker.webp" width="250" alt="月份选择，Android 应用实际运行，示例数据" /> |

| 账户编辑 | 账单与备份 | CSV 导出 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-account-editor.webp" width="250" alt="账户编辑，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-backup.webp" width="250" alt="账单与备份，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-export.webp" width="250" alt="CSV 导出，Android 应用实际运行，示例数据" /> |

</details>

<details>
<summary>展开查看截图识别、原文和导入预览</summary>

| CSV 导入预览 | 截图识别结果 |
| :---: | :---: |
| <img src=".github/screenshots/ui-csv-import-preview.webp" width="250" alt="CSV 导入预览，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-recognized-bill.webp" width="250" alt="截图识别结果，Android 应用实际运行，示例数据" /> |

| 识别原文 | 原始截图查看 |
| :---: | :---: |
| <img src=".github/screenshots/ui-recognition-text.webp" width="250" alt="识别原文，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-original-screenshot.webp" width="250" alt="原始截图查看，Android 应用实际运行，示例数据" /> |

</details>

<details>
<summary>展开查看授权引导、后台设置与使用帮助</summary>

| 页面识别授权 | 系统受限设置引导 | 电池与后台设置 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-permission.webp" width="250" alt="页面识别授权，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-restricted-settings.webp" width="250" alt="系统受限设置引导，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-background-battery.webp" width="250" alt="电池与后台设置，Android 应用实际运行，示例数据" /> |

| 自启动检查 | 最近任务加锁 | 授权设置汇总 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-background-autostart.webp" width="250" alt="自启动检查，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-background-task-lock.webp" width="250" alt="最近任务加锁，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-permission-summary.webp" width="250" alt="授权设置汇总，Android 应用实际运行，示例数据" /> |

| 设置完成 | 摇一摇运行状态 | 使用帮助 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-permission-done.webp" width="250" alt="设置完成，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-shake-status.webp" width="250" alt="摇一摇运行状态，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-help.webp" width="250" alt="使用帮助，Android 应用实际运行，示例数据" /> |

</details>

<details>
<summary>展开查看隐私、关于、诊断与许可页面</summary>

| 数据与隐私 | 关于慧铭记账 | 调试与诊断 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-privacy.webp" width="250" alt="数据与隐私，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-about.webp" width="250" alt="关于慧铭记账，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-diagnostics.webp" width="250" alt="调试与诊断，Android 应用实际运行，示例数据" /> |

| 许可与第三方声明 | 项目开源协议 | 第三方依赖声明 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-licenses.webp" width="250" alt="许可与第三方声明，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-project-license.webp" width="250" alt="项目开源协议，Android 应用实际运行，示例数据" /> | <img src=".github/screenshots/ui-third-party.webp" width="250" alt="第三方依赖声明，Android 应用实际运行，示例数据" /> |

| 字体与图标声明 |   |   |
| :---: | :---: | :---: |
| <img src=".github/screenshots/ui-font-notices.webp" width="250" alt="字体与图标声明，Android 应用实际运行，示例数据" /> |   |   |

</details>

## 真实识别记录

以下为使用者提供的真机执行记录，隐私信息已打码。

| 微信转账识别 | 账本记录 | 退款账单详情 |
| :---: | :---: | :---: |
| <img src=".github/screenshots/real-wechat-transfer.webp" width="250" alt="真实微信转账记账结果，收款人已打码" /> | <img src=".github/screenshots/real-ledger.webp" width="250" alt="真实手机账本，展示自动记账与摇一摇来源" /> | <img src=".github/screenshots/real-refund-detail.webp" width="250" alt="真实退款详情：原付100元、已退67.23元、实付32.77元" /> |

## 开始使用

1. 安装应用，在「我的」开启微信 / 支付宝自动识别或摇一摇，按引导完成授权。
2. 在支持的支付成功页完成自动记账；漏记时用摇一摇、截图导入或手动补记。
3. 回到账本核对记录；换机或卸载前，从「账单与备份」导出完整备份。

自动识别目前记录支出，覆盖范围取决于支付页面与系统版本。识别和数据保存在本机，资产余额由你手动维护。

[使用说明与常见问题](.github/REFERENCE.md#常见问题) · [源码构建](.github/REFERENCE.md#从源码构建) · [参与贡献](CONTRIBUTING.md) · [安全反馈](SECURITY.md)

项目采用 [GPL-3.0-only](LICENSE)；第三方组件及素材声明见 [NOTICE.md](NOTICE.md)。
