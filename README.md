# 人脸图像隐私脱敏与效果评估系统

课题：基于 ReAct Agent 的人脸图像隐私脱敏与效果评估系统设计与实现。

当前仓库已实现第一版可运行链路：

```text
Vue 上传图片 -> Spring Boot 接口 -> Python 图像服务
                                     -> MediaPipe 人脸检测
                                     -> 模糊 / 马赛克 / 纯色遮挡
                                     -> PSNR / SSIM 评价
                                     -> H2 任务记录
```

## 目录

- `backend/`：Spring Boot 业务接口和 Python 服务代理
- `frontend/`：Vue 3 + Vite 页面
- `python-service/`：FastAPI 图像处理服务
- `task_plan.md`、`findings.md`、`progress.md`：毕设开发记录

当前已实现功能：

- 单张人脸图片上传与处理
- 高斯模糊、像素马赛克、纯色遮挡三种策略
- 轻度、标准、严格三档隐私区域扩展范围
- MediaPipe 人脸检测，包含小人脸放大兜底
- PSNR、SSIM 和处理耗时计算
- Spring Boot 通过 H2 保存任务状态、策略、指标和脱敏结果
- 最近任务查询、历史结果查看、脱敏图下载
- 单张和批量任务异步排队，支持高/普通/低优先级和失败自动重试
- 本地演示登录、管理员/普通用户角色和任务归属查询
- 管理员操作审计日志，记录登录、任务提交、批量提交和重试等元数据
- `auto` 策略使用受限 ReAct Agent，根据人脸数量、图像清晰度和隐私等级选择脱敏策略，并保存工具调用轨迹
- 单张图片可一键发起三种策略对比实验，页面展示处理结果和自动归纳的实验观察
- 可导出 CSV 实验数据，也可打开处理报告并通过浏览器“打印/保存为 PDF”留存实验记录
- 统计看板展示平均 PSNR、平均 SSIM、策略使用分布和任务运行状态

## 本地启动

### 1. 启动 Python 服务

```powershell
cd python-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
& .\scripts\download_models.ps1
python -m uvicorn app.main:app --reload --port 8001
```

服务使用 MediaPipe Tasks API，需要先下载人脸检测模型。模型默认缓存到 Windows 的 `%LOCALAPPDATA%\FacePrivacy\models`，服务启动后可访问 `http://localhost:8001/health`。

### 2. 启动 Spring Boot

```powershell
cd backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`，健康检查地址为 `http://localhost:8080/api/health`。

### 3. 启动 Vue 前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`。首次进入需要登录：

- 管理员：`admin / admin123`，可以查看历史任务和全局统计；
- 普通用户：`student / student123`，只能查看自己创建的任务。

账号用于本地演示，登录会话保存在服务端内存中，默认 8 小时过期。

## 脱敏策略

- `auto`：受限 ReAct Agent 自动选择策略
- `blur`：高斯模糊
- `mosaic`：像素马赛克
- `cover`：纯色遮挡

Agent 当前采用不依赖外部大模型的受限 ReAct 风格执行器，工具调用顺序和策略范围均由程序控制，便于本地复现和答辩演示。答辩时应准确表述为“可控、可复现的 ReAct 工具编排”，不要声称已经完成外部大模型的自主规划。后续可在工具接口不变的前提下接入 LangChain 模型适配器，并保留当前规则兜底。

PSNR、SSIM 只用于记录图像保真度变化。系统同时记录脱敏后固定人脸检测器的检测抑制率，作为策略对比的辅助指标；该指标不等同于人脸识别准确率，也不能单独证明隐私保护有效。原始上传图片只在 Java 任务处理期间暂存，不写入 H2；数据库保存脱敏结果、任务指标和 Agent 轨迹。

## 答辩演示建议

1. 先使用 `Agent 自动选择 + 严格` 提交一张人脸图片，展示结果、策略、PSNR/SSIM 和 5 步工具轨迹。
2. 再只选择同一张图片，点击“对比三种脱敏策略”，展示三张结果图和自动生成的实验观察。
3. 展示历史任务、策略分布、任务优先级和失败重试入口，说明系统不仅是单一图片处理脚本。
4. 点击“打印/保存报告”，在浏览器打印窗口中选择“另存为 PDF”，作为实验记录材料。

## 当前边界

- 当前 Agent 是受限、确定性的本地执行器，重点展示工具编排、状态记录和可解释轨迹。
- 当前系统没有把原始图片写入数据库，但仍会保存脱敏结果图片；生产环境还应增加访问控制、加密存储和定期清理策略。
- MediaPipe BlazeFace 对极小人脸、严重遮挡和极端角度存在漏检可能，系统会对无脸输入返回可解释的失败信息。
- H2 适合本地演示和论文实验，不作为生产部署数据库。
- 当前登录和角色权限是面向本地演示的轻量实现，会话令牌保存在服务端内存中；生产环境还应接入统一身份认证、持久化会话、操作审计和更严格的权限策略。

## 接口概要

- `POST /api/v1/tasks`：提交单张异步任务，返回 `202` 和任务摘要
- `POST /api/v1/tasks/batch`：提交最多 20 张图片的批量任务
- `GET /api/v1/tasks`：查询最近任务摘要
- `GET /api/v1/tasks/stats`：查询任务总览、平均指标和策略分布
- `GET /api/v1/tasks/{id}`：查询任务状态、结果图和 Agent 轨迹
- `POST /api/v1/tasks/{id}/retry`：重新提交失败任务
- `POST /api/v1/auth/login`：登录并获取演示会话令牌
- `GET /api/v1/auth/me`：查询当前登录用户
- `POST /api/v1/auth/logout`：退出当前会话
- `GET /api/v1/audit-logs`：管理员查询最近操作审计日志
