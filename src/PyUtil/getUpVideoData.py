import hashlib
import urllib.parse
import requests
import sys
import json
import time
import random

# ===================== 配置项 =====================
# 请求超时时间
TIMEOUT = 30
# 最大重试次数
MAX_RETRIES = 3
# 重试间隔（秒）
RETRY_INTERVAL = 60  # 1分钟
# 基础延迟范围（秒）
MIN_DELAY = 3
MAX_DELAY = 8
# ===================== 工具函数 =====================
# 重定义print函数（UTF-8）
def utf8_print(*args, **kwargs):
    try:
        output = " ".join(map(str, args))
        sys.stdout.buffer.write(output.encode("utf-8") + b"\n")
    except Exception as e:
        print(*args, file=sys.stderr, **kwargs)
print = utf8_print

# 免费代理IP池（这里使用一些公共代理，实际使用时建议购买付费代理）
PROXY_POOL = [
    None,  # 直连
]

def get_random_proxy():
    """获取随机代理"""
    proxy = random.choice(PROXY_POOL)
    if proxy:
        return {"http": proxy, "https": proxy}
    return None

def get_random_user_agent():
    """获取随机User-Agent"""
    user_agents = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
    ]
    return random.choice(user_agents)

def create_session():
    """创建新的会话对象"""
    session = requests.Session()
    headers = {
        "authority": "api.bilibili.com",
        "method": "GET",
        "scheme": "https",
        "accept": "application/json, text/plain, */*",
        "accept-encoding": "gzip, deflate, br",
        "accept-language": "zh-CN,zh;q=0.9,en;q=0.8",
        "cache-control": "no-cache",
        "origin": "https://space.bilibili.com",
        "pragma": "no-cache",
        "referer": "https://space.bilibili.com/",
        "sec-ch-ua": '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": '"Windows"',
        "sec-fetch-dest": "empty",
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-site",
        "user-agent": get_random_user_agent(),
    }
    session.headers.update(headers)
    return session

# 会话对象
session = create_session()

def json_str_to_url_encoded(json_str):
    """将JSON格式的字符串转换为URL百分号编码字符串"""
    url_encoded_str = urllib.parse.quote(json_str, safe='')
    return url_encoded_str

def partial_unescape_url_encoded(json_encoded_str):
    """将全URL编码的JSON字符串转换为「仅{}和""编码、数组内容不编码」的格式"""
    keep_encoded = {'{': '%7B', '}': '%7D', '"': '%22'}
    fully_decoded = urllib.parse.unquote(json_encoded_str)
    result_parts = []
    for char in fully_decoded:
        if char in keep_encoded:
            result_parts.append(keep_encoded[char])
        else:
            result_parts.append(char)
    return ''.join(result_parts)

def dict_to_sorted_list(input_dict):
    """将字典按照键的字母顺序排序，生成"键=值"格式的字符串列表"""
    sorted_items = sorted(input_dict.items(), key=lambda x: x[0])
    result_list = [f"{key}={value}" for key, value in sorted_items]
    return result_list

def getW(y):
    """生成w_rid加密参数"""
    o = 'ea1db124af3c7062474693fa704f4ff8'
    str_mix = y + o
    str_bytes = str_mix.encode('utf-8')
    md5_result = hashlib.md5(str_bytes)
    w_rid = md5_result.hexdigest()
    return w_rid

def send_to_java(mid, page_num, video_list):
    """将视频数据发送到Java后端"""
    if not video_list:
        return False, "视频数据为空", 0, 0, []
    
    try:
        # Java后端接口地址
        java_url = "http://localhost:8080/api/bilibili/upvideo-data/save"
        
        # 构造请求数据
        payload = {
            'mid': mid,
            'page': page_num,
            'videos': video_list
        }
        
        # 设置请求头
        headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }
        
        # 发送POST请求
        response = requests.post(java_url, json=payload, headers=headers, timeout=30)
        
        if response.status_code == 200:
            result = response.json()
            if result.get('code') == 200:
                data = result.get('data', {})
                success_count = data.get('successCount', 0)
                duplicate_count = data.get('duplicateCount', 0)
                duplicate_bv_nums = data.get('duplicateBvNums', [])
                utf8_print(f"✅ 第{page_num}页已发送：成功{success_count}条，重复{duplicate_count}条")
                return True, None, success_count, duplicate_count, duplicate_bv_nums
            else:
                return False, result.get('msg', '未知错误'), 0, 0, []
        else:
            return False, f"HTTP错误：{response.status_code}", 0, 0, []
            
    except requests.exceptions.Timeout:
        return False, "请求超时", 0, 0, []
    except requests.exceptions.ConnectionError:
        return False, "连接失败，请检查Java后端是否启动", 0, 0, []
    except Exception as e:
        return False, f"发送失败：{str(e)}", 0, 0, []

def get_video_data(mid, page_num):
    """获取单页视频数据"""
    retries = 0
    
    while retries < MAX_RETRIES:
        try:
            # 随机延迟
            delay = random.uniform(MIN_DELAY, MAX_DELAY)
            time.sleep(delay)
            
            # 构造参数
            wts = str(int(time.time()))
            dm_img_list = '[]'
            dm_img_inter = '{"ds":[],"wh":[2269,1263,67],"of":[507,1014,507]}'

            param = {
                'pn': str(page_num),
                'ps': '40',
                'tid': '0',
                'special_type': '',
                'order': 'pubdate',
                'mid': str(mid),
                'index': '0',
                'keyword': '',
                'order_avoided': 'true',
                'platform': 'web',
                'web_location': '333.1387',
                'dm_img_list': dm_img_list,
                'dm_img_str': 'V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ',
                'dm_cover_img_str': 'QU5HTEUgKEludGVsLCBJbnRlbChSKSBVSEQgR3JhcGhpY3MgNjIwICgweDAwMDAzRUEwKSBEaXJlY3QzRDExIHZzXzVfMCBwc181XzAsIEQzRDExKUdvb2dsZSBJbmMuIChJbnRlbC',
                'dm_img_inter': dm_img_inter,
                'wts': wts
            }
            
            # URL编码
            param['dm_img_list'] = json_str_to_url_encoded(param['dm_img_list'])
            param['dm_img_inter'] = json_str_to_url_encoded(param['dm_img_inter'])
            
            # 生成w_rid
            u = dict_to_sorted_list(param)
            y = '&'.join(u)
            w_rid = getW(y)
            param['w_rid'] = w_rid
            
            # 部分解码
            param['dm_img_list'] = partial_unescape_url_encoded(param['dm_img_list'])
            param['dm_img_inter'] = partial_unescape_url_encoded(param['dm_img_inter'])
            
            # 构造URL
            param_order = [
                'pn', 'ps', 'tid', 'special_type', 'order', 'mid', 'index', 'keyword',
                'order_avoided', 'platform', 'web_location', 'dm_img_list', 'dm_img_str',
                'dm_cover_img_str', 'dm_img_inter', 'w_rid', 'wts'
            ]
            param_str = '&'.join([f"{k}={param[k]}" for k in param_order if k in param])
            final_url = f'https://api.bilibili.com/x/space/wbi/arc/search?{param_str}'
            
            # 更新User-Agent
            session.headers.update({"user-agent": get_random_user_agent()})
            
            # 获取代理
            proxy = get_random_proxy()
            
            # 发送请求
            response = session.get(final_url, proxies=proxy, timeout=TIMEOUT)
            json_data = response.json()
            
            # 校验响应
            if json_data.get("code") != 0:
                msg = json_data.get('message', '未知错误')
                if '风控' in msg or '频繁' in msg or '限制' in msg:
                    utf8_print(f"⚠️ 第{page_num}页触发风控：{msg}")
                    retries += 1
                    if retries < MAX_RETRIES:
                        wait_time = RETRY_INTERVAL * retries
                        utf8_print(f"🔄 {wait_time}秒后重试...")
                        time.sleep(wait_time)
                        continue
                return False, 0, []
            
            data = json_data.get('data', {})
            vlist = data.get('list', {}).get('vlist', [])
            
            if not vlist:
                return False, 0, []
            
            # 收集视频数据
            video_list = []
            for video in vlist:
                video_info = {
                    'comment': video.get('comment', 0),
                    'typeid': video.get('typeid', 0),
                    'play': video.get('play', 0),
                    'title': video.get('title', ''),
                    'author': video.get('author', ''),
                    'mid': video.get('mid', 0),
                    'aid': video.get('aid', 0),
                    'bvNum': video.get('bvid', ''),
                    'created': video.get('created', 0),
                    'length': video.get('length', '')
                }
                video_list.append(video_info)
            
            # 获取总视频数
            page_info = data.get('page', {})
            total_count = page_info.get('count', 0)
            page_size = page_info.get('ps', 40)
            total_pages = (total_count + page_size - 1) // page_size
            
            return True, total_pages, video_list
            
        except Exception as e:
            utf8_print(f"❌ 获取数据异常：{str(e)}")
            retries += 1
            if retries < MAX_RETRIES:
                time.sleep(RETRY_INTERVAL)
            else:
                return False, 0, []
    
    return False, 0, []

def crawl_single_page(mid, page_num):
    """爬取单个页面"""
    utf8_print(f"📌 开始爬取UP主[{mid}]的第{page_num}页")
    start_time = time.time()
    
    success, total_pages, video_list = get_video_data(mid, page_num)
    
    if success:
        send_success, error_msg, success_count, duplicate_count, duplicate_bv_nums = send_to_java(mid, page_num, video_list)
        elapsed = time.time() - start_time
        
        if send_success:
            utf8_print(f"✅ 成功！共{len(video_list)}条数据，总页数{total_pages}，用时{elapsed:.1f}秒")
            # 返回成功信息、总页数、成功数、重复数、重复的BV号
            return True, total_pages, success_count, duplicate_count, duplicate_bv_nums
        else:
            utf8_print(f"❌ 发送数据到Java后端失败：{error_msg}")
            return False, 0, 0, 0, []
    else:
        utf8_print(f"❌ 第{page_num}页获取失败")
        return False, 0, 0, 0, []

# ===================== 入口函数 =====================
if __name__ == '__main__':
    """
    用法：python getUpVideoData.py <mid> <page>
    示例：python getUpVideoData.py 15385187 1
    """
    if len(sys.argv) != 3:
        utf8_print("❌ 参数错误！正确用法：")
        utf8_print("python getUpVideoData.py <mid> <page>")
        utf8_print("示例：python getUpVideoData.py 15385187 1")
        sys.exit(1)
    
    try:
        mid = int(sys.argv[1])
        page = int(sys.argv[2])
        
        if mid <= 0:
            utf8_print("❌ MID必须大于0")
            sys.exit(1)
        
        if page <= 0:
            utf8_print("❌ 页码必须大于0")
            sys.exit(1)
        
        utf8_print(f"🚀 开始爬取UP主[{mid}]的第{page}页数据")
        utf8_print(f"📌 Java后端地址：http://localhost:8080/api/bilibili/upvideo-data/save")
        
        # 爬取单个页面
        success, total_pages, success_count, duplicate_count, duplicate_bv_nums = crawl_single_page(mid, page)
        
        if success:
            # 输出结果，包含总页数
            utf8_print(f"\n{'='*50}")
            utf8_print(f"🎉 爬取完成！")
            utf8_print(f"UP主ID：{mid}")
            utf8_print(f"当前页码：{page}")
            utf8_print(f"总页数：{total_pages}")
            utf8_print(f"成功数：{success_count}")
            utf8_print(f"重复数：{duplicate_count}")
            utf8_print(f"{'='*50}")
            
            # 输出总页数，方便Java后端计算
            utf8_print(f"TOTAL_PAGES:{total_pages}")
            sys.exit(0)
        else:
            utf8_print(f"\n❌ 爬取失败")
            sys.exit(1)
            
    except ValueError:
        utf8_print("❌ 参数格式错误！mid和page必须是数字")
        sys.exit(1)
    except Exception as e:
        utf8_print(f"❌ 程序异常：{str(e)}")
        sys.exit(1)
