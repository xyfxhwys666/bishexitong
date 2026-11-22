from __future__ import annotations

from .image_pipeline import (
    PRIVACY_LEVELS,
    _build_result,
    apply_strategy,
    assess_quality,
    decode_image,
    detect_faces,
)


def _choose_strategy(face_count: int, quality_level: str, privacy_level: str) -> tuple[str, str]:
    if privacy_level == "strict":
        return "cover", "strict privacy level requires full local replacement"
    if quality_level == "low":
        return "cover", "low image quality makes reversible-looking blur less reliable"
    if face_count >= 3:
        return "mosaic", "multiple faces need a balanced local obfuscation strategy"
    if privacy_level == "light":
        return "blur", "light privacy level allows a visually smoother strategy"
    return "mosaic", "standard privacy level uses a balanced pixelation strategy"


def process_with_agent(raw: bytes, privacy_level: str, started: float) -> dict:
    if privacy_level not in PRIVACY_LEVELS:
        raise ValueError(f"unsupported privacy level: {privacy_level}")

    trace: list[dict] = []
    original = decode_image(raw)
    faces = detect_faces(original)
    if not faces:
        raise ValueError("no face detected")
    trace.append({
        "step": 1,
        "thought": "I need the protected regions before selecting a strategy.",
        "action": "detect_faces",
        "observation": {"faceCount": len(faces)},
    })

    quality = assess_quality(original)
    trace.append({
        "step": 2,
        "thought": "I will inspect image quality because aggressive processing is safer for poor-quality input.",
        "action": "assess_quality",
        "observation": quality,
    })

    selected, reason = _choose_strategy(len(faces), quality["level"], privacy_level)
    trace.append({
        "step": 3,
        "thought": "I will choose one strategy from the fixed policy set.",
        "action": "choose_strategy",
        "observation": {"strategy": selected, "reason": reason},
    })

    processed = apply_strategy(original, faces, selected, privacy_level)
    trace.append({
        "step": 4,
        "thought": "I will apply the selected strategy only to detected face regions.",
        "action": "apply_strategy",
        "observation": {"strategy": selected, "faceCount": len(faces)},
    })
    result = _build_result(
        original,
        processed,
        faces,
        "auto",
        selected,
        privacy_level,
        started,
        trace,
    )
    trace.append({
        "step": 5,
        "thought": "I will evaluate the output before returning it.",
        "action": "evaluate_metrics",
        "observation": result["metrics"],
    })
    return result
