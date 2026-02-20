import sys
import requests
import time
import random

# ===================== 配置项 =====================
SPRING_BOOT_BATCH_URL = "http://localhost:8080/api/bilibili/regiondata/batch-save"
BATCH_SIZE = 50  # 减小批量阈值，加快提交频率
TIMEOUT = 30
# 优化请求头（更贴近浏览器真实请求）
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Referer": "https://www.bilibili.com/",  # 精准鬼畜区Referer
    "Origin": "https://www.bilibili.com",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Cache-Control": "no-cache",
    "Pragma": "no-cache",
    "X-Bili-Trace-ID": str(random.randint(10000000, 99999999))
}

# 全局去重集合（记录已爬取的BV号）
EXISTED_BV = set()

def utf8_print(*args, **kwargs):
    try:
        output = " ".join(map(str, args))
        sys.stdout.buffer.write(output.encode("utf-8") + b"\n")
    except Exception as e:
        print(*args, file=sys.stderr, **kwargs)
print = utf8_print

def timestamp_to_datetime_time(timestamp):
    """时间戳转格式化时间"""
    try:
        time_tuple = time.localtime(timestamp)
        return time.strftime("%Y-%m-%d %H:%M:%S", time_tuple)
    except ValueError as e:
        return f"错误：无效的时间戳 - {str(e)}"

def get_bilibili_gc_video(page: int = 1, ps: int = 20, order="pubdate") -> list:
    """
    获取B站指定分区视频数据（增加失败重试）
    """
    url = "https://api.bilibili.com/x/web-interface/dynamic/region"
    params = {
        "rid": int(REGION_ID),  # 修复类型转换问题
        "pn": page,
        "ps": ps,
        "order": order,
        "web_location": 131587,
        "wts": int(time.time()),
    }
    # 失败重试机制（最多3次）
    for retry in range(3):
        try:
            response = requests.get(
                url=url,
                headers=HEADERS,
                params=params,
                timeout=10
            )
            response.raise_for_status()
            data = response.json()

            if data.get("code") != 0:
                print(f"第{retry+1}次请求失败，错误：{data.get('message')}")
                time.sleep(2)
                continue

            video_list = data.get("data", {}).get("archives", [])
            if not video_list:
                return []

            # 核心：去重逻辑（基于BV号）
            result = []
            for video in video_list:
                bv_num = video.get("bvid", "")
                if bv_num and bv_num not in EXISTED_BV:
                    EXISTED_BV.add(bv_num)  # 加入去重集合
                    video_info = {
                        "name": video.get("title", ""),
                        "bvNum": bv_num,
                        "tname": video.get("tname", ""),
                        "upName": video.get("owner", {}).get("name", ""),
                        "upId": int(video.get("owner", {}).get("mid", 0)) if video.get("owner", {}).get("mid") else 0,
                        "playCount": int(video.get("stat", {}).get("view", 0)),
                        "likeCount": int(video.get("stat", {}).get("like", 0)),
                        "danmukuCount": int(video.get("stat", {}).get("danmaku", 0)),
                        "replyCount": int(video.get("stat", {}).get("reply", 0)),
                        "favoriteCount": int(video.get("stat", {}).get("favorite", 0)),
                        "coinCount": int(video.get("stat", {}).get("coin", 0)),
                        "shareCount": int(video.get("stat", {}).get("share", 0)),
                        "publishTime": timestamp_to_datetime_time(video.get("pubdate", 0)),
                        "duration": video.get("duration", 0),
                        "pidNameV2": video.get("pid_name_v2", ""),
                        "pidV2": int(video.get("pid_v2", 0)) if video.get("pid_v2") else 0,
                        "tidV2": int(video.get("tidv2", 0)) if video.get("tidv2") else 0
                    }
                    result.append(video_info)
            return result

        except requests.exceptions.RequestException as e:
            print(f"网络异常（第{retry+1}次）：{e}")
            time.sleep(2)
            continue
    return []

def batch_save_to_springboot(regiondata_list):
    """批量提交到SpringBoot入库（优化日志）"""
    if not regiondata_list:
        utf8_print("⚠️ 空数据列表，跳过提交")
        return 0
    try:
        response = requests.post(
            url=SPRING_BOOT_BATCH_URL,
            json=regiondata_list,
            headers={"Content-Type": "application/json"},
            timeout=TIMEOUT
        )
        result = response.json()
        if result.get("code") == 200:
            success_count = result.get("successCount", 0)
            utf8_print(f"✅ 批量提交{len(regiondata_list)}条，成功入库{success_count}条")
            return success_count
        else:
            utf8_print(f"❌ 批量提交失败：{result.get('msg')}")
            return 0
    except Exception as e:
        utf8_print(f"❌ 提交异常：{str(e)}")
        return 0

def runGetRegionData(START_PAGE, END_PAGE, all_video_data):
    success = 0
    # 优化：每页爬取后立即检查批量阈值，无需等循环结束
    for page in range(START_PAGE, END_PAGE + 1):
        # 动态更新Trace-ID
        HEADERS["X-Bili-Trace-ID"] = str(random.randint(10000000, 99999999))
        print(f"📄 正在爬取第{page}页数据（已去重BV数：{len(EXISTED_BV)}）")

        video_data = get_bilibili_gc_video(page=page, ps=20)  # 强制单页20条（B站上限）

        if video_data:
            all_video_data.extend(video_data)
            print(f"📥 第{page}页新增有效数据：{len(video_data)}条")

        # 达到批量阈值立即提交，减少内存占用+加快入库
        if len(all_video_data) >= BATCH_SIZE:
            success += batch_save_to_springboot(all_video_data)
            all_video_data = []

        # 优化延迟：未登录态2-4秒（合规且效率更高）
        time.sleep(random.uniform(2, 4))

    # 提交剩余数据
    if all_video_data:
        success += batch_save_to_springboot(all_video_data)

    return success

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("参数格式错误")
        print("参考格式:python getregiondata.py <regionid> <起始页号> <结束页号>")
        print("如:python getregiondata.py 119 1 20")
        sys.exit(1)

    # 修复：REGION_ID转为int类型
    try:
        REGION_ID = int(sys.argv[1])
    except ValueError:
        print("❌ 分区ID必须为整数")
        sys.exit(1)

    START_PAGE = int(sys.argv[2])
    END_PAGE = int(sys.argv[3])
    all_video_data = []

    # 记录开始时间，统计效率
    start_time = time.time()
    total_success = runGetRegionData(START_PAGE, END_PAGE, all_video_data)
    end_time = time.time()
    cost_time = end_time - start_time

    # 输出效率统计
    if total_success > 0:
        utf8_print(f"\n🎉 任务完成！")
        utf8_print(f"⏱️  耗时：{cost_time:.2f}秒（约{cost_time/60:.1f}分钟）")
        utf8_print(f"📊 成功入库：{total_success}条，平均速度：{total_success/(cost_time/60):.1f}条/分钟")
        sys.exit(0)
    else:
        utf8_print("❌ 任务完成，但未成功入库任何数据")
        sys.exit(1)
