"""
火山引擎 OCR 脚本 - 使用 volcengine SDK（用户验证可工作）
输入(stdin): JSON {"image_base64": "..."}
输出(stdout): JSON {"success": true, "line_texts": [...], "line_rects": [...]}
"""
import sys, json, base64, os
from volcengine.visual.VisualService import VisualService


def main():
    try:
        AK = os.environ.get("VOLCANO_AK")
        SK = os.environ.get("VOLCANO_SK")
        if not AK or not SK:
            print(json.dumps({"success": False, "error": "环境变量 VOLCANO_AK / VOLCANO_SK 未设置"}))
            sys.exit(1)

        raw = sys.stdin.read()
        req_data = json.loads(raw)
        image_base64 = req_data["image_base64"]

        visual_service = VisualService()
        visual_service.set_ak(AK)
        visual_service.set_sk(SK)

        req = {"image_base64": image_base64}
        resp = visual_service.ocr_normal(req)

        if resp is None:
            print(json.dumps({"success": False, "error": "volcengine SDK returned None"}))
            sys.exit(1)

        data = resp.get("data", {})
        if not data:
            print(json.dumps({"success": False, "error": f"volcengine response: {json.dumps(resp)}"}))
            sys.exit(1)
            
        line_texts = data.get("line_texts", [])
        line_rects = data.get("line_rects", [])

        print(json.dumps({
            "success": True,
            "line_texts": line_texts,
            "line_rects": line_rects
        }, ensure_ascii=False))

    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}, ensure_ascii=False))
        sys.exit(1)


if __name__ == "__main__":
    main()
