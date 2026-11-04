# 花有谱图标

正式采用“账页开花”：账单展开成为花瓣，对应“花钱有谱”。

- `huayoupu-icon.png`：AI 生成的 1024 × 1024 原图，用于 README 等品牌展示。
- `huayoupu-prompt.txt`：生成该图的完整最终提示词；模型为 gpt-image-2.5，通过 toImage 已配置备用服务生成。
- Android 前景：`app/src/main/res/drawable-nodpi/huayoupu_icon.png`，从原图提取真实轮廓，透明留白内最大半径约 300px，适配圆形和其他系统裁切。
- Android 背景：`huayoupu_coral`（`#EE7756`）。
- 单色主题：`huayoupu_monochrome.png`，沿用同一轮廓与留白，由 `ic_ledger.xml` 引用。

桌面显示名称为“花有谱”，完整产品名为“花有谱记账”，主张为“少点手动，收支有谱。”
包名、备份结构、签名与识别服务组件保持兼容。历史图标保存在版本历史中。
