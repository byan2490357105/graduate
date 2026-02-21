import sys
import requests
import time
import random
import hashlib

SPRING_BOOT_BATCH_URL = "http://localhost:8080/api/bilibili/regiondata/batch-save"
BATCH_SIZE = 300
TIMEOUT = 30

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://www.bilibili.com/",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Cache-Control": "no-cache",
    "Accept": "application/json, text/plain, */*",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Site": "same-site",
    "Pragma": "no-cache",
    "cookie": "buvid3=E6556CB0-91D4-A8B8-768E-B0E99A85DF0714444infoc; b_nut=1769148714; _uuid=34BABDEA-5812-F735-9B10C-F8F104103993AE15439infoc; CURRENT_FNVAL=4048; CURRENT_QUALITY=0; buvid4=68D8A95B-859B-F79F-CE84-DF1E7EFEDE2B16751-026012314-UJVXYJRvQ1PnCX3wOLCsag%3D%3D; buvid_fp=5ef30d920da9fea53175567e959f7310; rpdid=|(JYl)Rm~)m~0J'u~YmuR)~u~; bili_ticket=eyJhbGciOiJIUzI1NiIsImtpZCI6InMwMyIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NzE3NTI2MDYsImlhdCI6MTc3MTQ5MzM0NiwicGx0IjotMX0.7AxKjRchAbDorH7vNflgY8knLdlukJXsaag5zGdrDUY; bili_ticket_expires=1771752546"
}

def utf8_print(*args, **kwargs):
    try:
        output = " ".join(map(str, args))
        sys.stdout.buffer.write(output.encode("utf-8") + b"\n")
    except Exception as e:
        print(*args, file=sys.stderr, **kwargs)
print = utf8_print

def timestamp_to_datetime_time(timestamp):
    try:
        time_tuple = time.localtime(timestamp)
        return time.strftime("%Y-%m-%d %H:%M:%S", time_tuple)
    except ValueError as e:
        return f"错误：无效的时间戳 - {str(e)}"

def getW(y):
    i = 'ea1db124af3c7062474693fa704f4ff8'
    str_val = y + i
    str_bytes = str_val.encode('utf-8')
    md5_result = hashlib.md5(str_bytes)
    w_rid = md5_result.hexdigest()
    return w_rid

def generate_bilibili_region_feed_url(display_id="1", from_region='1029'):
    base_url = "https://api.bilibili.com/x/web-interface/region/feed/rcmd"
    wts = int(time.time())
    u = [
        "device=web",
        f"display_id={display_id}",
        f"from_region={from_region}",
        "plat=30",
        "request_cnt=15",
        "web_location=333.40138",
        f"wts={wts}"
    ]
    y = '&'.join(u)
    w_rid = getW(y)
    c = f'display_id={display_id}&request_cnt=15&from_region={from_region}&device=web&plat=30&web_location=333.40138&w_rid={w_rid}&wts={wts}'
    final_url = base_url + '?' + c
    return final_url

def get_bilibili_gc_video_new(display_id, from_region,pid_v2):
    for retry in range(3):
        try:
            url = generate_bilibili_region_feed_url(display_id, from_region)
            HEADERS["X-Bili-Trace-ID"] = str(random.randint(10000000, 99999999))
            
            response = requests.get(url, headers=HEADERS, timeout=10)
            response.raise_for_status()
            data = response.json()

            if data.get("code") != 0:
                print(f"第{retry+1}次请求失败，错误：{data.get('message')}")
                time.sleep(2)
                continue

            video_list = data.get("data", {}).get("archives", [])
            if not video_list:
                return []

            result = []
            for video in video_list:
                bv_num = video.get("bvid", "")
                video_info = {
                    "name": video.get("title", ""),
                    "bvNum": bv_num,
                    "upName": video.get("author", {}).get("name", ""),
                    "upId": int(video.get("author", {}).get("mid", 0)) if video.get("author", {}).get("mid") else 0,
                    "aid":video.get("aid",0),
                    "playCount": int(video.get("stat", {}).get("view", 0)),
                    "likeCount": int(video.get("stat", {}).get("like", 0)),
                    "danmukuCount": int(video.get("stat", {}).get("danmaku", 0)),
                    "publishTime": timestamp_to_datetime_time(video.get("pubdate", 0)),
                    "duration": video.get("duration", 0),
                    "pidNameV2": video.get("pid_name_v2", ""),
                    "pidV2": pid_v2
                }
                result.append(video_info)
            return result

        except requests.exceptions.RequestException as e:
            print(f"网络异常（第{retry+1}次）：{e}")
            time.sleep(2)
            continue
    return []

def batch_save_to_springboot(regiondata_list):
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

def runGetRegionDataNew(START_PAGE, END_PAGE, pid_v2,all_video_data):
    success = 0
    for i in range(START_PAGE, END_PAGE + 1):
        print(f"📄 正在爬取第{i}页数据")

        video_data = get_bilibili_gc_video_new(str(i), REGION_ID,pid_v2)

        if video_data:
            all_video_data.extend(video_data)
            print(f"📥 第{i}页新增有效数据：{len(video_data)}条")

        if len(all_video_data) >= BATCH_SIZE:
            success += batch_save_to_springboot(all_video_data)
            all_video_data = []

        time.sleep(random.uniform(1, 3))

    if all_video_data:
        success += batch_save_to_springboot(all_video_data)

    return success

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("参数格式错误")
        print("参考格式:python getregiondata3.py <分区ID> <起始页号> <结束页号>")
        print("如:python getregiondata3.py 119 1 10")
        sys.exit(1)

    try:
        REGION_ID = str(sys.argv[1])
    except ValueError:
        print("❌ 分区ID必须为整数或字符串")
        sys.exit(1)

    START_PAGE = int(sys.argv[2])
    END_PAGE = int(sys.argv[3])
    all_video_data = []

    start_time = time.time()
    total_success = runGetRegionDataNew(START_PAGE, END_PAGE, REGION_ID,all_video_data)
    end_time = time.time()
    cost_time = end_time - start_time

    if total_success > 0:
        utf8_print(f"\n🎉 任务完成！")
        utf8_print(f"⏱️  耗时：{cost_time:.2f}秒（约{cost_time/60:.1f}分钟）")
        utf8_print(f"📊 成功入库：{total_success}条")
        sys.exit(0)
    else:
        utf8_print("❌ 任务完成，但未成功入库任何数据")
        sys.exit(1)
