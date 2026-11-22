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

第一版功能：

- 单张人脸图片上传与处理
- 高斯模糊、像素马赛克、纯色遮挡三种策略
- 轻度、标准、严格三档隐私区域扩展范围
- MediaPipe 人脸检测，包含小人脸放大兜底
- PSNR、SSIM 和处理耗时计算
- Spring Boot 通过 H2 保存任务状态、策略、指标和脱敏结果
- 最近任务查询、历史结果查看、脱敏图下载
- 单张和批量任务异步排队，支持高/普通/低优先级和失败自动重试
- `auto` 策略使用受限 ReAct Agent，根据人脸数量、图像清晰度和隐私等级选择脱敏策略，并保存工具调用轨迹

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

前端默认运行在 `http://localhost:5173`。当前原型支持上传单张图片、选择脱敏策略、查看脱敏结果、评价指标和历史任务。

## 脱敏策略

- `auto`：受限 ReAct Agent 自动选择策略
- `blur`：高斯模糊
- `mosaic`：像素马赛克
- `cover`：纯色遮挡

Agent 当前采用不依赖外部大模型的固定工具策略，便于本地复现和答辩演示；后续可在工具接口不变的前提下接入 LangChain 模型适配器。PSNR、SSIM 用于记录图像保真度变化，隐私效果实验另行使用固定的人脸特征模型评价。

## 接口概要

- `POST /api/v1/tasks`：提交单张异步任务，返回 `202` 和任务摘要
- `POST /api/v1/tasks/batch`：提交最多 20 张图片的批量任务
- `GET /api/v1/tasks`：查询最近任务摘要
- `GET /api/v1/tasks/{id}`：查询任务状态、结果图和 Agent 轨迹
- `POST /api/v1/tasks/{id}/retry`：重新提交失败任务
