import hashlib
import json
import time
import requests
import sys
from urllib.parse import quote

# ===================== 配置项 =====================
# SpringBoot批量入库接口地址（需和后端地址一致）
SPRING_BOOT_BATCH_URL = "http://localhost:8080/api/bilibili/comment/batch-save"
# 批量入库阈值（每100条提交一次）
BATCH_SIZE = 100
# 请求超时时间
TIMEOUT = 30
# ===================== 工具函数 =====================
# 重定义print函数（UTF-8）
def utf8_print(*args, **kwargs):
    try:
        output = " ".join(map(str, args))
        sys.stdout.buffer.write(output.encode("utf-8") + b"\n")
    except Exception as e:
        print(*args, file=sys.stderr, **kwargs)
print = utf8_print

# ===================== 爬虫核心函数 =====================
# 1. 创建会话对象
session = requests.Session()
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://www.bilibili.com/",
    "Accept": "application/json, text/plain, */*"
}
session.headers.update(headers)

def get_oid_by_bv(bv_num):
    """通过BV号获取oid"""
    url = f"https://api.bilibili.com/x/web-interface/view?bvid={bv_num}"
    response = session.get(url, timeout=TIMEOUT)
    if response.status_code == 200:
        data = response.json()
        if data.get("code") == 0:
            video_data = data.get("data", {})
            return {
                "bv_num": bv_num,
                "oid": video_data.get("aid"),
                "title": video_data.get("title"),
            }
    raise Exception(f"获取BV号[{bv_num}]的oid失败，响应：{response.text}")

def GetW(wts, NextPage, oid):
    """生成w_rid加密参数"""
    pagination_str = quote(NextPage)
    l = [
        'mode=3',
        f'oid={oid}',
        f'pagination_str={pagination_str}',
        'plat=1',
        'seek_rpid=',
        'type=1',
        'web_location=1315875',
        f'wts={wts}'
    ]
    y = '&'.join(l)
    string = y + 'ea1db124af3c7062474693fa704f4ff8'
    MD5 = hashlib.md5()
    MD5.update(string.encode('utf-8'))
    return MD5.hexdigest()

def batch_save_to_springboot(bv_num, comment_list):
    """批量提交到SpringBoot入库"""
    if not comment_list:
        utf8_print("⚠️ 空评论列表，跳过提交")
        return 0
    try:
        request_data = {
            "bvNum": bv_num,
            "commentList": comment_list
        }
        response = requests.post(
            url=SPRING_BOOT_BATCH_URL,
            json=request_data,
            headers={"Content-Type": "application/json"},
            timeout=TIMEOUT
        )
        result = response.json()
        if result.get("code") == 200:
            success_count = result.get("successCount", 0)
            utf8_print(f"✅ 批量提交{len(comment_list)}条，成功入库{success_count}条")
            return success_count
        else:
            utf8_print(f"❌ 批量提交失败：{result.get('msg')}")
            return 0
    except Exception as e:
        utf8_print(f"❌ 提交到SpringBoot异常：{str(e)}")
        return 0

def GetContent2(offset, video_info, comment_batch):
    """爬取单页评论，添加到批量列表"""
    url = "https://api.bilibili.com/x/v2/reply/wbi/main"
    pagination_str = '{"offset":%s}' % offset
    wts = int(time.time())
    w_rid = GetW(wts=wts, NextPage=pagination_str, oid=video_info['oid'])
    data = {
        'oid': f'{video_info["oid"]}',
        'type': '1',
        'mode': '3',
        'pagination_str': pagination_str,
        'plat': '1',
        'seek_rpid': '',
        'web_location': '1315875',
        'w_rid': w_rid,
        'wts': wts
    }
    response = session.get(url=url, params=data, timeout=TIMEOUT)
    json_data = response.json()

    # 校验响应是否正常
    if json_data.get("code") != 0 or not json_data.get("data", {}).get("replies"):
        utf8_print(f"⚠️ 无评论数据，响应：{json_data}")
        return "", False

    replies = json_data['data']['replies']
    # 收集评论到批量列表
    for index in replies:
        comment = {
            '昵称': index['member']['uname'],
            '性别': index['member']['sex'],
            '地区': index.get('reply_control', {}).get('location', ''),
            '评论': index['content']['message'],
            'rpid':str(index['rpid'])
        }
        comment_batch.append(comment)

    # 处理下一页偏移量
    next_offset = ""
    have_next = False
    try:
        raw_next_offset = json_data['data']['cursor']['pagination_reply']['next_offset']
        if raw_next_offset:
            next_offset = json.dumps(raw_next_offset)
            have_next = True
    except (KeyError, TypeError):
        pass
    return next_offset, have_next

def runGetComment(BVNum, startPageNum, endPageNum):
    """核心爬取函数"""
    total_success = 0  # 总入库成功数
    comment_batch = []  # 批量评论列表
    video_info = get_oid_by_bv(BVNum)
    offset = '""'

    utf8_print(f"📌 开始爬取BV号[{BVNum}]，页码范围：{startPageNum}-{endPageNum}")

    for page in range(startPageNum, endPageNum + 1):
        utf8_print(f"🔍 爬取第{page}页...")
        offset, have_next = GetContent2(offset, video_info, comment_batch)

        # 达到批量阈值，提交入库
        if len(comment_batch) >= BATCH_SIZE:
            total_success += batch_save_to_springboot(BVNum, comment_batch)
            comment_batch = []

        # 无下一页则终止
        if not have_next:
            utf8_print("⚠️ 已爬取到最后一页，终止爬取")
            break

    # 提交剩余的评论
    if comment_batch:
        total_success += batch_save_to_springboot(BVNum, comment_batch)

    utf8_print(f"✅ 爬取完成！总入库成功数：{total_success}")
    return total_success

# ===================== 入口函数 =====================
if __name__ == '__main__':
    # 1. 校验命令行参数
    if len(sys.argv) != 4:
        utf8_print("❌ 参数错误！正确用法：")
        utf8_print("python getComment.py <BV号> <起始页号> <结束页号>")
        utf8_print("示例：python getComment.py BV1bPvQBLEUD 1 2")
        sys.exit(1)

    try:
        # 2. 解析参数
        BVNum = sys.argv[1]
        startPageNum = int(sys.argv[2])
        endPageNum = int(sys.argv[3])

        # 3. 校验参数合法性
        if startPageNum < 1 or endPageNum < startPageNum:
            utf8_print("❌ 页码错误！起始页必须≥1，且结束页≥起始页")
            sys.exit(1)

        # 4. 执行爬取
        total_success = runGetComment(BVNum, startPageNum, endPageNum)
        if total_success > 0:
            utf8_print(f"🎉 任务完成！共成功入库{total_success}条评论")
            sys.exit(0)
        else:
            utf8_print("❌ 任务完成，但未成功入库任何评论")
            sys.exit(1)

    except ValueError:
        utf8_print("❌ 页码必须是整数！示例：1、2、3")
        sys.exit(1)
    except Exception as e:
        utf8_print(f"❌ 程序执行异常：{str(e)}")
        sys.exit(1)