# 管理端前端

`management-pc` 是本项目的管理端前端，管理员和商家共用同一套 Vue 3 界面，根据账号角色自动切换菜单与页面权限。

## 技术栈

- Vue 3
- Vite
- Element Plus
- Pinia
- Vue Router
- ECharts

## 开发启动

```bash
npm install
npm run dev
```

默认开发地址为 `http://localhost:5173`。

## 比赛模式

管理端当前约定：

- `VITE_COMPETITION_MODE` 未显式设置为 `false` 时，默认按比赛展示模式运行
- 比赛模式下会优先突出推荐预览、用户分群、大数据分析和 AI 导购主线
- 更偏系统说明的内容会被弱化，减少首屏信息噪音

## 生产构建

```bash
npm run build
```

## 目录说明

- `src/layout`：后台整体布局
- `src/router`：路由与角色跳转控制
- `src/store`：登录态与用户信息存储
- `src/views/admin`：管理员页面
- `src/views/merchant`：商家页面
- `src/views/account`：个人中心与账号设置
- `src/api`：接口请求封装
- `src/utils`：请求工具与状态映射

## 使用说明

- 管理员登录后进入管理员工作台
- 商家登录后进入商家工作台
- 页面请求默认走后端 `/api` 接口
- 具体联调方式请结合项目根目录 [README](../README.md) 与 [docs](../docs) 中的文档使用
