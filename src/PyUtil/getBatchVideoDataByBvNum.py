import sys
import json
import time
import random
from getVideoData import get_video_data

def utf8_print(*args, **kwargs):
    try:
        output = " ".join(map(str, args))
        sys.stdout.buffer.write(output.encode("utf-8") + b"\n")
    except Exception as e:
        print(*args, file=sys.stderr, **kwargs)
print = utf8_print

def batch_get_video_data(bv_numbers):
    """
    批量获取视频数据
    :param bv_numbers: bv号列表
    :return: 视频数据列表
    """
    result = []
    total = len(bv_numbers)
    
    for i, bv_num in enumerate(bv_numbers, 1):
        try:
            print(f"[{i}/{total}] 开始获取 BV号: {bv_num} 的视频数据")
            
            # 获取视频数据
            video_data = get_video_data(bv_num)
            
            # 添加到结果列表
            result.append(video_data)
            
            # 每处理一个视频，添加随机延迟，避免请求过于频繁
            time.sleep(random.uniform(0.5, 1.5))
            
        except Exception as e:
            print(f"获取 BV号: {bv_num} 的视频数据失败: {str(e)}")
            # 即使失败，也继续处理下一个
            continue
    
    return result

if __name__ == "__main__":
    # 检查命令行参数
    if len(sys.argv) < 2:
        print("参数错误！正确用法：")
        print("python getBatchVideoDataByBvNum.py <bv1> <bv2> ...")
        print("示例：python getBatchVideoDataByBvNum.py BV1xx411c7mD BV1yy411c7mE")
        sys.exit(1)
    
    try:
        # 获取bv号列表
        bv_numbers = sys.argv[1:]
        print(f"共接收到 {len(bv_numbers)} 个 BV号")
        
        # 批量获取视频数据
        start_time = time.time()
        video_data_list = batch_get_video_data(bv_numbers)
        end_time = time.time()
        
        # 打印结果
        print(f"\n批量获取完成！")
        print(f"总处理 BV号: {len(bv_numbers)}")
        print(f"成功获取: {len(video_data_list)}")
        print(f"耗时: {end_time - start_time:.2f} 秒")
        
        # 输出JSON格式的结果，方便Java代码解析
        print("\n--- JSON结果开始 ---")
        print(json.dumps(video_data_list, ensure_ascii=False, indent=2))
        print("--- JSON结果结束 ---")
        
        sys.exit(0)
        
    except Exception as e:
        print(f"执行失败: {str(e)}")
        sys.exit(1)
