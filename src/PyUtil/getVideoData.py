import sys

import requests
import re
import json
from datetime import datetime

session = requests.Session()
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://www.bilibili.com/",
    "Accept": "application/json, text/plain, */*"
}
session.headers.update(headers)

def get_video_data(bv_num):
    url = f'https://www.bilibili.com/video/{bv_num}/?spm_id_from=333.337.search-card.all.click&vd_source=e0368fb8ada7717374c5ae56fd4890d8'
    response = requests.get(url=url, headers=headers)

    # 初始化返回字典（默认值适配数据库字段类型）
    request_data = {
        "name": "未获取到标题",
        "BvNum": bv_num,  # 兜底使用传入的BV号，确保非空
        "upName": "未获取到UP名",
        "upId": 0,
        "playCount": 0,
        "danmakuCount": 0,
        "likeCount": 0,
        "coinCount": 0,
        "favoriteCount": 0,
        "shareCount": 0,
        "publishTime": None,  # 标准化为datetime格式，适配数据库
        "duration": 0,  # 视频时长（秒）
        "videoDesc": "",  # 补充视频描述
        "tags": "",  # 补充视频标签
        "coverUrl": "",  # 补充封面URL
    }

    # 核心提取逻辑
    # 1. 提取 window.__INITIAL_STATE__ 中的JSON数据（容错正则）
    initial_state_match = re.search(r'window\.__INITIAL_STATE__\s*=\s*({.*?})(?=\s*;|\s*<)', response.text,
                                    re.DOTALL)
    if not initial_state_match:
        print(f"BV号 {bv_num}：未找到核心数据")
        return request_data  # 返回默认值字典，不终止程序

    # 2. 容错解析JSON（解决格式异常问题）
    def safe_json_loads(json_str):
        json_str = json_str.strip().rstrip(';,')
        try:
            return json.loads(json_str)
        except:
            try:
                return json.loads(json_str[:-1]) if len(json_str) > 1 else {}
            except:
                return {}

    initial_state = safe_json_loads(initial_state_match.group(1))
    video_data = initial_state.get("videoData", {})  # 视频核心数据

    # ========== 填充基础信息（匹配数据库字段） ==========
    # 视频标题
    request_data["name"] = video_data.get("title", "未获取到标题")
    # BV号（优先用页面内的，兜底用传入值）
    request_data["BvNum"] = video_data.get("bvid", bv_num)
    # 视频描述
    request_data["videoDesc"] = video_data.get("desc", "")
    # 封面URL
    request_data["coverUrl"] = video_data.get("pic", "")
    # 视频时长（秒，适配数据库int类型）
    request_data["duration"] = int(video_data.get("duration", 0))

    # ========== 填充UP主信息 ==========
    owner_data = video_data.get("owner", {})
    request_data["upName"] = owner_data.get("name", "未获取到UP名")
    # UP主ID转整数（适配数据库bigint类型）
    request_data["upId"] = int(owner_data.get("mid", 0)) if owner_data.get("mid") and owner_data.get(
        "mid") != "未获取到UP主ID" else 0

    # ========== 填充统计数据 ==========
    stat_data = video_data.get("stat", {})
    request_data["playCount"] = int(stat_data.get("view", 0))
    request_data["danmakuCount"] = int(stat_data.get("danmaku", 0))
    request_data["likeCount"] = int(stat_data.get("like", 0))
    request_data["coinCount"] = int(stat_data.get("coin", 0))
    request_data["favoriteCount"] = int(stat_data.get("favorite", 0))
    request_data["shareCount"] = int(stat_data.get("share", 0))

    # ========== 填充发布时间（标准化为数据库datetime格式） ==========
    publish_time = video_data.get("ctime", 0)
    if publish_time != 0:
        request_data["publishTime"] = datetime.fromtimestamp(publish_time).strftime("%Y-%m-%d %H:%M:%S")
    else:
        request_data["publishTime"] = None

    # ========== 补充：视频标签（逗号分隔） ==========
    tags = initial_state.get("tags", [])
    if tags:
        request_data["tags"] = ",".join([tag.get("tag_name", "") for tag in tags if tag.get("tag_name")])

    # 返回填充好的字典（可直接序列化为JSON供后端接收）
    return request_data


# ------------------- 测试调用示例 -------------------
if __name__ == "__main__":
    if len(sys.argv)<2:
        print(json.dumps({"error":"缺少BV号参数"}))
        sys.exit(1)
    # 调用函数获取数据
    bv_num = sys.argv[1]
    request_data = get_video_data(bv_num)

    # 打印JSON格式结果,方便java解析
    print(json.dumps(request_data, ensure_ascii=False, indent=4))