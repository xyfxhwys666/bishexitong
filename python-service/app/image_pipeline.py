from __future__ import annotations

import base64
import os
import time
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision


STRATEGIES = {"blur", "mosaic", "cover"}
PRIVACY_LEVELS = {"light", "standard", "strict"}


def _default_model_path() -> Path:
    if os.name == "nt":
        root = Path(os.getenv("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
    else:
        root = Path(os.getenv("XDG_CACHE_HOME", Path.home() / ".cache"))
    return root / "FacePrivacy" / "models" / "blaze_face_short_range.tflite"


@dataclass(frozen=True)
class FaceRegion:
    x: int
    y: int
    width: int
    height: int
    score: float


def decode_image(raw: bytes) -> np.ndarray:
    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("uploaded file is not a supported image")
    return image


@lru_cache(maxsize=1)
def _face_detector() -> vision.FaceDetector:
    model_path = Path(os.getenv(
        "FACE_DETECTOR_MODEL",
        _default_model_path(),
    ))
    if not model_path.is_file():
        raise RuntimeError(
            "face detector model is missing; run scripts/download_models.ps1 first"
        )
    options = vision.FaceDetectorOptions(
        base_options=python.BaseOptions(model_asset_path=str(model_path)),
        running_mode=vision.RunningMode.IMAGE,
        min_detection_confidence=0.5,
    )
    return vision.FaceDetector.create_from_options(options)


def _detect_faces_once(image: np.ndarray) -> list[FaceRegion]:
    height, width = image.shape[:2]
    rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    result = _face_detector().detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))

    regions: list[FaceRegion] = []
    for detection in result.detections or []:
        box = detection.bounding_box
        x = max(0, box.origin_x)
        y = max(0, box.origin_y)
        right = min(width, box.origin_x + box.width)
        bottom = min(height, box.origin_y + box.height)
        if right > x and bottom > y:
            score = detection.categories[0].score if detection.categories else 0.0
            regions.append(FaceRegion(x, y, right - x, bottom - y, score))
    return regions


def detect_faces(image: np.ndarray) -> list[FaceRegion]:
    regions = _detect_faces_once(image)
    if regions:
        return regions

    # Short-range BlazeFace is sensitive to very small faces. A bounded upscale
    # keeps ordinary group photos usable without making large images expensive.
    height, width = image.shape[:2]
    scale = min(2.5, 1800 / max(height, width))
    if scale <= 1.0:
        return []
    enlarged = cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    return [
        FaceRegion(
            int(face.x / scale),
            int(face.y / scale),
            max(1, int(face.width / scale)),
            max(1, int(face.height / scale)),
            face.score,
        )
        for face in _detect_faces_once(enlarged)
    ]


def _expanded(
    region: FaceRegion,
    image: np.ndarray,
    privacy_level: str,
) -> tuple[int, int, int, int]:
    height, width = image.shape[:2]
    padding = {
        "light": (0.08, 0.12),
        "standard": (0.12, 0.18),
        "strict": (0.20, 0.28),
    }[privacy_level]
    pad_x = int(region.width * padding[0])
    pad_y = int(region.height * padding[1])
    x1 = max(0, region.x - pad_x)
    y1 = max(0, region.y - pad_y)
    x2 = min(width, region.x + region.width + pad_x)
    y2 = min(height, region.y + region.height + pad_y)
    return x1, y1, x2, y2


def _blur(area: np.ndarray) -> np.ndarray:
    kernel = max(15, int(min(area.shape[:2]) // 3) | 1)
    return cv2.GaussianBlur(area, (kernel, kernel), 0)


def _mosaic(area: np.ndarray) -> np.ndarray:
    block = max(8, min(area.shape[:2]) // 12)
    small_w = max(1, area.shape[1] // block)
    small_h = max(1, area.shape[0] // block)
    small = cv2.resize(area, (small_w, small_h), interpolation=cv2.INTER_LINEAR)
    return cv2.resize(small, (area.shape[1], area.shape[0]), interpolation=cv2.INTER_NEAREST)


def apply_strategy(
    image: np.ndarray,
    faces: list[FaceRegion],
    strategy: str,
    privacy_level: str,
) -> np.ndarray:
    if strategy not in STRATEGIES:
        raise ValueError(f"unsupported strategy: {strategy}")
    if privacy_level not in PRIVACY_LEVELS:
        raise ValueError(f"unsupported privacy level: {privacy_level}")
    output = image.copy()
    for face in faces:
        x1, y1, x2, y2 = _expanded(face, output, privacy_level)
        area = output[y1:y2, x1:x2]
        if strategy == "blur":
            output[y1:y2, x1:x2] = _blur(area)
        elif strategy == "mosaic":
            output[y1:y2, x1:x2] = _mosaic(area)
        else:
            output[y1:y2, x1:x2] = (32, 32, 32)
    return output


def ssim(image_a: np.ndarray, image_b: np.ndarray) -> float:
    gray_a = cv2.cvtColor(image_a, cv2.COLOR_BGR2GRAY).astype(np.float64)
    gray_b = cv2.cvtColor(image_b, cv2.COLOR_BGR2GRAY).astype(np.float64)
    mean_a = cv2.GaussianBlur(gray_a, (11, 11), 1.5)
    mean_b = cv2.GaussianBlur(gray_b, (11, 11), 1.5)
    variance_a = cv2.GaussianBlur(gray_a * gray_a, (11, 11), 1.5) - mean_a * mean_a
    variance_b = cv2.GaussianBlur(gray_b * gray_b, (11, 11), 1.5) - mean_b * mean_b
    covariance = cv2.GaussianBlur(gray_a * gray_b, (11, 11), 1.5) - mean_a * mean_b
    c1 = (0.01 * 255) ** 2
    c2 = (0.03 * 255) ** 2
    score = ((2 * mean_a * mean_b + c1) * (2 * covariance + c2)) / (
        (mean_a * mean_a + mean_b * mean_b + c1)
        * (variance_a + variance_b + c2)
        + 1e-12
    )
    return float(np.clip(score.mean(), -1.0, 1.0))


def process_image(raw: bytes, strategy: str, privacy_level: str) -> dict:
    started = time.perf_counter()
    if strategy not in STRATEGIES:
        raise ValueError(f"unsupported strategy: {strategy}")
    if privacy_level not in PRIVACY_LEVELS:
        raise ValueError(f"unsupported privacy level: {privacy_level}")
    original = decode_image(raw)
    faces = detect_faces(original)
    if not faces:
        raise ValueError("no face detected")
    processed = apply_strategy(original, faces, strategy, privacy_level)
    ok, encoded = cv2.imencode(".jpg", processed, [cv2.IMWRITE_JPEG_QUALITY, 92])
    if not ok:
        raise RuntimeError("failed to encode processed image")
    return {
        "strategy": strategy,
        "privacyLevel": privacy_level,
        "faceCount": len(faces),
        "faces": [face.__dict__ for face in faces],
        "metrics": {
            "psnr": float(cv2.PSNR(original, processed)),
            "ssim": ssim(original, processed),
            "elapsedMs": round((time.perf_counter() - started) * 1000, 2),
        },
        "imageBase64": base64.b64encode(encoded.tobytes()).decode("ascii"),
    }
