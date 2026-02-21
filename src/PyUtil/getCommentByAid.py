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

def GetContent2(offset, aid, comment_batch):
    """爬取单页评论，添加到批量列表"""
    url = "https://api.bilibili.com/x/v2/reply/wbi/main"
    pagination_str = '{"offset":%s}' % offset
    wts = int(time.time())
    w_rid = GetW(wts=wts, NextPage=pagination_str, oid=aid)
    data = {
        'oid': f'{aid}',
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

def runGetComment(BVNum,aid, startPageNum, endPageNum):
    """核心爬取函数"""
    total_success = 0  # 总入库成功数
    comment_batch = []  # 批量评论列表

    offset = '""'

    utf8_print(f"📌 开始爬取BV号[{BVNum}]，页码范围：{startPageNum}-{endPageNum}")

    for page in range(startPageNum, endPageNum + 1):
        utf8_print(f"🔍 爬取第{page}页...")
        offset, have_next = GetContent2(offset, aid, comment_batch)

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
    # 参数格式：python getCommentByAid.py <bv1> <aid1> <bv2> <aid2> ...
    if len(sys.argv) < 3:
        utf8_print("❌ 参数错误！正确用法：")
        utf8_print("python getCommentByAid.py <bv1> <aid1> <bv2> <aid2> ...")
        utf8_print("示例：python getCommentByAid.py BV1xx411c7mD 123456789 BV1yy411c7mE 987654321")
        sys.exit(1)

    try:
        # 2. 固定页码参数
        startPageNum = 1
        endPageNum = 100
        
        # 3. 解析bv和aid成对参数
        bv_aid_pairs = []
        i = 1
        while i < len(sys.argv):
            if i + 1 < len(sys.argv):
                bv_num = sys.argv[i]
                aid = sys.argv[i + 1]
                bv_aid_pairs.append((bv_num, aid))
                i += 2
            else:
                utf8_print(f"⚠️ 参数不完整，跳过bv：{sys.argv[i]}")
                i += 1
        
        if not bv_aid_pairs:
            utf8_print("❌ 没有有效的bv和aid参数")
            sys.exit(1)
        
        utf8_print(f"📋 共{len(bv_aid_pairs)}个bv-aid对需要爬取")
        
        # 4. 遍历每个bv-aid对执行爬取
        total_success_all = 0
        for index, (bv_num, aid) in enumerate(bv_aid_pairs, 1):
            utf8_print(f"🚀 开始处理第{index}/{len(bv_aid_pairs)}个bv-aid对：{bv_num} - {aid}")
            total_success = runGetComment(bv_num, aid, startPageNum, endPageNum)
            total_success_all += total_success
            utf8_print(f"✅ bv-aid对[{bv_num} - {aid}]处理完成，入库{total_success}条评论")
        
        # 5. 输出总结果
        utf8_print(f"🎉 全部任务完成！共处理{len(bv_aid_pairs)}个bv-aid对，成功入库{total_success_all}条评论")
        sys.exit(0)

    except ValueError:
        utf8_print("❌ 页码必须是整数！示例：1、2、3")
        sys.exit(1)
    except Exception as e:
        utf8_print(f"❌ 程序执行异常：{str(e)}")
        sys.exit(1)