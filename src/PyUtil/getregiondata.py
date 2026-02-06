import sys

import requests
import time
import random
import json

# ===================== 配置项 =====================
# SpringBoot批量入库接口地址（需和后端地址一致）
SPRING_BOOT_BATCH_URL = "http://localhost:8080/api/bilibili/regiondata/batch-save"
# 批量入库阈值（每100条提交一次）
BATCH_SIZE = 100
# 请求超时时间
TIMEOUT = 30

# 请求头（强化防缓存 + 修正Referer为鬼畜区）
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Referer": "https://www.bilibili.com/",  # 修正：鬼畜区对应地址
    "Origin": "https://www.bilibili.com",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Cache-Control": "no-cache",
    "Pragma": "no-cache",
    "X-Bili-Trace-ID": str(random.randint(10000000, 99999999))
}

def utf8_print(*args, **kwargs):
    try:
        output = " ".join(map(str, args))
        sys.stdout.buffer.write(output.encode("utf-8") + b"\n")
    except Exception as e:
        print(*args, file=sys.stderr, **kwargs)
print = utf8_print

# 鬼畜分区ID：B站鬼畜分区固定rid=119
REGION_ID = 119

def timestamp_to_datetime_time(timestamp):
    """
    用time模块将Unix时间戳转换为年月日时分秒
    """
    try:
        # 1. 转换为本地时间元组
        time_tuple = time.localtime(timestamp)
        # 若需要UTC时间，替换为：time.gmtime(timestamp)

        # 2. 格式化输出
        format_str = time.strftime("%Y-%m-%d %H:%M:%S", time_tuple)
        return format_str

    except ValueError as e:
        return f"错误：无效的时间戳 - {str(e)}"

def get_bilibili_gc_video(page: int = 1, ps: int = 20,order="pubdate") ->list:
    """
    获取B站鬼畜分区视频数据
    :param page: 页码，从1开始
    :param ps: 单页条数，最大20（B站接口限制）
    :return: 元组(原始video_list数据, 整理后的精简数据)
    """
    # API接口地址
    url = "https://api.bilibili.com/x/web-interface/dynamic/region"
    # 请求参数
    # 1. 未登录态下的正确分页参数
    params = {
        "rid":REGION_ID ,
        "pn": page,  # 分页用pn（代替page）
        "ps": ps,  # 每页条数用ps（代替page_size）
        "order": order,  # 强制最新排序，避免推荐缓存
        "web_location": 131587,
        "wts": int(time.time()),  # 每次请求更新时间戳
    }
    try:
        # 发送GET请求，超时10秒
        response = requests.get(url, headers=HEADERS, params=params, timeout=10)
        # 校验请求是否成功
        response.raise_for_status()
        # 解析JSON数据
        data = response.json()
        if data.get("code") != 0:
            print(f"请求失败，错误信息：{data.get('message')}")
            return []
        # 提取原始核心视频数据（video_list）
        video_list = data.get("data", {}).get("archives", [])

        # 整理需要的精简字段（按指定格式重构）
        result = []
        for video in video_list:
            video_info = {
                # 视频名：对应 Java name
                "name": video.get("title", ""),

                # BV号：对应 Java bvNum（数据库 bv_num）
                "bvNum": video.get("bvid", ""),

                # 二级分区名称：对应 Java tname
                "tname": video.get("tname", ""),

                # UP名：对应 Java upName
                "upName": video.get("owner", {}).get("name", ""),

                # UP主ID：对应 Java upId（Integer 类型，空值设为 0）
                "upId": int(video.get("owner", {}).get("mid", 0)) if video.get("owner", {}).get("mid", "") else 0,

                # 播放量：对应 Java playCount（Long 类型，空值设为 0）
                "playCount": int(video.get("stat", {}).get("view", 0)),

                # 点赞数：对应 Java likeCount（Long 类型，空值设为 0）
                "likeCount": int(video.get("stat", {}).get("like", 0)),

                # 弹幕数：对应 Java danmukuCount（Long 类型，空值设为 0）
                "danmukuCount": int(video.get("stat", {}).get("danmaku", 0)),

                # 评论数：对应 Java replyCount（Long 类型，空值设为 0）
                "replyCount": int(video.get("stat", {}).get("reply", 0)),

                # 收藏数：对应 Java favoriteCount（Long 类型，空值设为 0）
                "favoriteCount": int(video.get("stat", {}).get("favorite", 0)),

                # 投币数：对应 Java coinCount（Long 类型，空值设为 0）
                "coinCount": int(video.get("stat", {}).get("coin", 0)),

                # 分享数：对应 Java shareCount（Long 类型，空值设为 0）
                "shareCount": int(video.get("stat", {}).get("share", 0)),

                # 视频发布时间：对应 Java publishTime（LocalDateTime，格式 yyyy-MM-dd HH:mm:ss）
                "publishTime":  timestamp_to_datetime_time(video.get("pubdate", 0)),

                # 视频时长：对应 Java duration（Integer 类型，原始数据无该字段，设为默认值 0）
                "duration": video.get("duration",0),

                # 分区名称：对应 Java pidNameV2
                "pidNameV2": video.get("pid_name_v2", ""),

                # 分区编号：对应 Java pidV2（Integer 类型，空值设为 0）
                "pidV2": int(video.get("pid_v2", 0)) if video.get("pid_v2", "") else 0,

                # 二级分区编号：对应 Java tidV2（Integer 类型，空值设为 0，原始字段 tidv2 映射为 tidV2）
                "tidV2": int(video.get("tidv2", 0)) if video.get("tidv2", "") else 0
            }
            result.append(video_info)

        return result
    except requests.exceptions.RequestException as e:
        print(f"网络请求异常：{e}")
        return []

def batch_save_to_springboot(regiondata_list):
    """批量提交到SpringBoot入库"""
    if not regiondata_list:
        utf8_print("⚠️ 空评论列表，跳过提交")
        return 0
    try:
        response = requests.post(
            url=SPRING_BOOT_BATCH_URL,
            json=regiondata_list,
            headers={"Content-Type": "application/json"},
            timeout=TIMEOUT
        )
        result = response.json()
        print(result)
        if result.get("code") == 200:
            success_count = result.get("successCount", 0)
            utf8_print(f"✅ 批量提交{len(regiondata_list)}条，成功入库{success_count}条")
            return success_count
        else:
            utf8_print(f"❌ 批量提交失败：{result.get('msg')}")
            return 0
    except Exception as e:
        utf8_print(f"❌ 提交到SpringBoot异常：{str(e)}")
        return 0

def runGetRegionData(START_PAGE,END_PAGE,all_video_data):
    success=0
    # 循环爬取多页数据
    for page in range(START_PAGE, END_PAGE + 1):
        # 每次请求更新X-Bili-Trace-ID（增强防缓存）
        HEADERS["X-Bili-Trace-ID"] = str(random.randint(10000000, 99999999))
        #print(f"正在爬取第{page}页数据...")
        video_data = get_bilibili_gc_video(page=page)

        if video_data:
            all_video_data.extend(video_data)  # 收集精简数据

        if len(all_video_data) >= BATCH_SIZE:
            success += batch_save_to_springboot(all_video_data)
            all_video_data = []
            # 每页间隔1秒，遵守B站接口调用规范
        # 未登录态延迟，避免风控
        time.sleep(random.uniform(5, 8))

    if all_video_data:
        success += batch_save_to_springboot(all_video_data)

    return success

if __name__ == "__main__":
    if len(sys.argv)<4:
        print("参数格式错误")
        print("参考格式:python getregiondata <regionid> <起始页号> <结束页号>")
        print("如:python getregiondata 119 1 3")
        sys.exit(1)
    REGION_ID=sys.argv[1]

    # 配置爬取参数：起始页、结束页（建议单次不超过10页，避免高频请求）
    START_PAGE = int(sys.argv[2])
    END_PAGE = int(sys.argv[3])
    # 存储所有爬取精简数据
    all_video_data = []

    total_success=runGetRegionData(START_PAGE,END_PAGE,all_video_data)


    if total_success > 0:
        utf8_print(f"🎉 任务完成！共成功入库{total_success}条评论")
        sys.exit(0)
    else:
        utf8_print("❌ 任务完成，但未成功入库任何评论")
        sys.exit(1)