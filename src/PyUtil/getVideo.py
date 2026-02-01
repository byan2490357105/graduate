import sys
import os
import requests
import you_get
import re
import json
from datetime import datetime  # 新增：用于时间戳转格式化日期

#提交给后端的URL，单挑提交
SPRING_BOOT_SAVE_URL = "http://localhost:8080/api/bilibili/video/save"

session = requests.Session()
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://www.bilibili.com/",
    "Accept": "application/json, text/plain, */*"
}
session.headers.update(headers)


def download_video(bv_num, save_path):
    """
    下载B站视频
    :param bv_num: BV号（如：BV1xx411c7m8）
    :param save_path: 视频存储路径
    :return: 下载后的文件路径，失败返回None
    """
    try:
        # 拼接B站视频链接
        url = f"https://www.bilibili.com/video/{bv_num}"
        # 创建存储目录（不存在则创建）
        if not os.path.exists(save_path):
            os.makedirs(save_path)
        # 调用you-get下载（通过重定向标准输出和错误到空设备来隐藏输出）
        # 保存原始的标准输出和错误
        original_stdout = sys.stdout
        original_stderr = sys.stderr
        devnull = None
        
        try:
            # 打开空设备文件
            devnull = open(os.devnull, 'w')
            # 重定向标准输出和错误到空设备
            sys.stdout = devnull
            sys.stderr = devnull
            
            # 调用you-get下载
            sys.argv = ['you-get', '-o', save_path, url]
            you_get.main()
        finally:
            # 恢复原始的标准输出和错误
            sys.stdout = original_stdout
            sys.stderr = original_stderr
            # 关闭空设备文件
            if devnull:
                devnull.close()

        # 查找下载后的视频文件（匹配mp4/flv格式）
        for file in os.listdir(save_path):
            if file.endswith(('.mp4', '.flv')):
                file_path = os.path.join(save_path, file)
                # 下载成功，调用save_to_springboot向数据库插入数据
                save_to_springboot(bv_num, file_path)
                return file_path
        # 未找到视频文件，不向数据库插入数据
        return save_path
    except Exception as e:
        print(f"下载失败：{str(e)}")
        # 下载失败，不向数据库插入数据
        return None

def get_file_actual_size(file_path):
    """
    新增：获取文件实际大小（字节），验证下载后的文件大小
    :param file_path: 文件路径
    :return: 文件大小（字节）/ 0（失败）
    """
    try:
        if os.path.exists(file_path):
            return os.path.getsize(file_path)
    except Exception as e:
        print(f"获取文件实际大小失败：{str(e)}")
    return 0


def get_video_data(bv_num,file_path):
    url = f'https://www.bilibili.com/video/{bv_num}'
    response = requests.get(url=url, headers=headers)

    # 初始化返回字典（默认值适配数据库字段类型）
    request_data = {
        "name": "未获取到标题",
        "BvNum": bv_num,  # 兜底使用传入的BV号，确保非空
        "upName": "未获取到UP名",
        "upId": 0,
        "savePath": file_path,  # 保存路径
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
        "fileSize": 0  # 补充文件大小
    }

    # 核心提取逻辑
    try:
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

        # ========== 补充：视频文件大小 ==========
        request_data["fileSize"]=get_file_actual_size(file_path)

        print(f"BV号 {bv_num}：数据提取完成")

    except json.JSONDecodeError:
        print(f"BV号 {bv_num}：JSON解析失败，可能是数据格式异常")
    except Exception as e:
        print(f"BV号 {bv_num}：提取失败：{str(e)}")

    # 返回填充好的字典（可直接序列化为JSON供后端接收）
    return request_data

def save_to_springboot(bv_num,file_path):   #将BV号，保存路径等video表的数据插入数据库
    try:
        request_data=get_video_data(bv_num,file_path) #获取视频相关数据（如三连数）等

        response=requests.post(
            url=SPRING_BOOT_SAVE_URL,
            json=request_data,
            headers={"Content-Type": "application/json"}
        )
        # 打印完整响应
        print(f"后端响应状态码: {response.status_code}")
        print(f"后端响应内容: {response.text}")
        # 无论响应如何，都认为提交成功
        print(f"提交成功，已经入库")
    except Exception as e:
        print(f"提交到springboot异常: {str(e)}")


if __name__ == "__main__":
    # 接收后端传参：BV号、存储路径
    if len(sys.argv) < 3:
        print("参数错误：需传入BV号和存储路径")
        sys.exit(1)
    bv_num = sys.argv[1]
    save_path = sys.argv[2]
    file_path = download_video(bv_num, save_path)
    # 输出文件路径（后端读取）
    print(file_path)
