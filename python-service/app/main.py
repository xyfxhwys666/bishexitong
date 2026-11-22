from fastapi import FastAPI, File, Form, HTTPException, UploadFile
import logging

from .image_pipeline import process_image

app = FastAPI(title="Face Privacy Image Service", version="0.1.0")
logger = logging.getLogger(__name__)


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "service": "python-image-service"}


@app.post("/api/v1/process")
async def process(
    file: UploadFile = File(...),
    strategy: str = Form("mosaic"),
    privacy_level: str = Form("standard"),
) -> dict:
    if file.content_type and not file.content_type.startswith("image/"):
        raise HTTPException(status_code=415, detail="only image uploads are supported")
    try:
        raw = await file.read()
        if len(raw) > 10 * 1024 * 1024:
            raise ValueError("image size must be at most 10 MB")
        if not raw:
            raise ValueError("image file is empty")
        return process_image(raw, strategy, privacy_level)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("image processing failed")
        raise HTTPException(status_code=500, detail="image processing failed") from exc
