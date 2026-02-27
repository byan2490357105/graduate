// B站分区数据（使用新分区ID）
const biliZones = [
    { name: "动画", id: "1005" },
    { name: "鬼畜", id: "1007" },
    { name: "舞蹈", id: "1004" },
    { name: "娱乐", id: "1002" },
    { name: "科技数码", id: "1012" },
    { name: "美食", id: "1020" },
    { name: "游戏", id: "1008" },
    { name: "音乐", id: "1003" },
    { name: "影视", id: "1001" },
    { name: "知识", id: "1010" },
    { name: "资讯", id: "1009" },
    { name: "小剧场", id: "1021" },
    { name: "动物", id: "1024" },
    { name: "家装房产", id: "1015" },
    { name: "旅游出行", id: "1022" },
    { name: "情感", id: "1027" },
    { name: "汽车", id: "1013" },
    { name: "vlog", id: "1029" },
    { name: "户外潮流", id: "1016" },
    { name: "三农", id: "1023" },
    { name: "生活兴趣", id: "1030" },
    { name: "时尚美妆", id: "1014" },
    { name: "绘画", id: "1006" },
    { name: "健身", id: "1017" },
    { name: "亲子", id: "1025" },
    { name: "生活经验", id: "1031" },
    { name: "体育运动", id: "1018" },
    { name: "人工智能", id: "1011" },
    { name: "手工", id: "1019" },
    { name: "健康", id: "1026" },
    //分区：番剧，国创，综艺，电影，电视剧，纪录片，公益接口不同故不在此处

];

// 爬虫状态检查定时器ID
let crawlerStatusIntervalId = null;

// 页面加载完成后初始化
window.onload = function() {
    initZoneSelect();
};

// 初始化分区下拉框
function initZoneSelect() {
    const zoneSelect = document.getElementById('zone');
    
    // 清空现有选项（保留第一个提示选项）
    while (zoneSelect.options.length > 1) {
        zoneSelect.remove(1);
    }
    
    // 添加分区选项
    biliZones.forEach(zone => {
        const option = document.createElement('option');
        option.value = zone.id;
        option.text = zone.name;
        zoneSelect.appendChild(option);
    });
}

// 提交分区数据
function submitZoneData() {
    const zoneSelect = document.getElementById('zone');
    const selectedId = zoneSelect.value;
    const startPageInput = document.getElementById('startPage');
    const endPageInput = document.getElementById('endPage');
    const startPage = startPageInput.value;
    const endPage = endPageInput.value;
    const submitBtn = document.getElementById('submitBtn');
    const stopBtn = document.getElementById('stopBtn');
    
    // 检查是否已经有爬虫在运行
    if (crawlerStatusIntervalId !== null) {
        showError('爬虫正在运行中，请等待完成');
        return;
    }
    
    if (!selectedId) {
        showError('请选择一个分区');
        return;
    }
    
    if (!startPage || !endPage) {
        showError('请输入起始页和结束页');
        return;
    }
    
    const startPageNum = parseInt(startPage);
    const endPageNum = parseInt(endPage);
    
    if (startPageNum < 1 || endPageNum < 1) {
        showError('页码必须大于0');
        return;
    }
    
    if (startPageNum > endPageNum) {
        showError('起始页不能大于结束页');
        return;
    }
    
    const selectedZone = biliZones.find(zone => zone.id === selectedId);
    if (!selectedZone) {
        showError('选择的分区无效');
        return;
    }
    
    const zoneData = {
        "爬取分区ID": selectedId,
        "爬取起始页": startPage,
        "爬取结束页": endPage
    };
    
    // 禁用按钮并修改文本
    submitBtn.disabled = true;
    submitBtn.textContent = '正在获取数据中';
    stopBtn.disabled = false;
    
    // 显示提交的数据
    showResult('正在提交数据...');
    
    // 向后端提交数据
    fetch('/getBZoneRegion', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(zoneData)
    })
    .then(response => response.json())
    .then(data => {
        showResult('提交成功！\n' + JSON.stringify(data, null, 2));
        // 延迟2秒后开始定时查询爬虫状态，确保爬虫已经开始执行
        setTimeout(() => {
            startCrawlerStatusCheck();
        }, 2000);
    })
    .catch(error => {
        showError('提交失败：' + error.message);
        // 恢复按钮状态
        submitBtn.disabled = false;
        submitBtn.textContent = '获取数据';
        stopBtn.disabled = true;
    });
}

// 提前结束爬虫
function stopCrawler() {
    const submitBtn = document.getElementById('submitBtn');
    const stopBtn = document.getElementById('stopBtn');
    
    // 禁用停止按钮
    stopBtn.disabled = true;
    
    // 显示提交的数据
    showResult('正在停止爬虫...');
    
    // 向后端提交停止请求
    fetch('/stopCrawler', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({})
    })
    .then(response => response.json())
    .then(data => {
        showResult('停止请求已发送！\n' + JSON.stringify(data, null, 2));
        // 清除定时器
        if (crawlerStatusIntervalId !== null) {
            clearInterval(crawlerStatusIntervalId);
            crawlerStatusIntervalId = null;
        }
        // 恢复按钮状态
        submitBtn.disabled = false;
        submitBtn.textContent = '获取数据';
        stopBtn.disabled = true;
    })
    .catch(error => {
        showError('停止失败：' + error.message);
        // 恢复按钮状态
        submitBtn.disabled = false;
        submitBtn.textContent = '获取数据';
        stopBtn.disabled = true;
    });
}

// 定时查询爬虫状态
function startCrawlerStatusCheck() {
    // 先检查一次爬虫状态，确保爬虫已经开始执行
    fetch('/isCrawlerRunning', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.running) {
            // 爬虫正在执行，开始定时查询
            crawlerStatusIntervalId = setInterval(() => {
                fetch('/isCrawlerRunning', {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                })
                .then(response => response.json())
                .then(data => {
                    if (!data.running) {
                        // 爬虫已停止，恢复按钮状态
                        clearInterval(crawlerStatusIntervalId);
                        crawlerStatusIntervalId = null;
                        const submitBtn = document.getElementById('submitBtn');
                        const stopBtn = document.getElementById('stopBtn');
                        submitBtn.disabled = false;
                        submitBtn.textContent = '获取数据';
                        stopBtn.disabled = true;
                        showResult('爬虫执行完成！');
                    }
                })
                .catch(error => {
                    console.error('查询爬虫状态失败：', error);
                });
            }, 3000); // 每3秒查询一次
        } else {
            // 爬虫未开始执行，延迟后再次检查
            setTimeout(() => {
                startCrawlerStatusCheck();
            }, 2000);
        }
    })
    .catch(error => {
        console.error('查询爬虫状态失败：', error);
        // 查询失败，延迟后再次检查
        setTimeout(() => {
            startCrawlerStatusCheck();
        }, 2000);
    });
}

// 显示结果
function showResult(content) {
    const resultDiv = document.getElementById('result');
    const resultContent = document.getElementById('resultContent');
    const errorDiv = document.getElementById('error');
    
    resultContent.textContent = content;
    resultDiv.style.display = 'block';
    errorDiv.style.display = 'none';
}

// 显示错误
function showError(content) {
    const errorDiv = document.getElementById('error');
    const errorContent = document.getElementById('errorContent');
    const resultDiv = document.getElementById('result');
    
    errorContent.textContent = content;
    errorDiv.style.display = 'block';
    resultDiv.style.display = 'none';
}

// 收集分区评论
function collectZoneComment() {
    const zoneSelect = document.getElementById('zone');
    const selectedId = zoneSelect.value;
    const collectCommentBtn = document.getElementById('collectCommentBtn');
    
    // 检查是否已经有爬虫在运行
    if (crawlerStatusIntervalId !== null) {
        showError('爬虫正在运行中，请等待完成');
        return;
    }
    
    if (!selectedId) {
        showError('请选择一个分区');
        return;
    }
    
    const selectedZone = biliZones.find(zone => zone.id === selectedId);
    if (!selectedZone) {
        showError('选择的分区无效');
        return;
    }
    
    const requestData = {
        "分区名称": selectedZone.name,
        "分区ID": selectedId
    };
    
    // 禁用按钮并修改文本
    collectCommentBtn.disabled = true;
    collectCommentBtn.textContent = '正在收集评论中';
    
    // 显示提交的数据
    showResult('正在提交数据...');
    
    // 向后端提交数据
    fetch('/batchGetZoneComment', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestData)
    })
    .then(response => response.json())
    .then(data => {
        showResult('提交成功！\n' + JSON.stringify(data, null, 2));
        // 延迟2秒后开始定时查询爬虫状态，确保爬虫已经开始执行
        setTimeout(() => {
            startCrawlerStatusCheck();
        }, 2000);
    })
    .catch(error => {
        showError('提交失败：' + error.message);
        // 恢复按钮状态
        collectCommentBtn.disabled = false;
        collectCommentBtn.textContent = '将收集分区评论';
    });
}

// 收集分区视频数据（录入tag）
function collectZoneVideoData() {
    const zoneSelect = document.getElementById('zone');
    const selectedId = zoneSelect.value;
    const collectVideoDataBtn = document.getElementById('collectVideoDataBtn');
    
    // 检查是否已经有爬虫在运行
    if (crawlerStatusIntervalId !== null) {
        showError('爬虫正在运行中，请等待完成');
        return;
    }
    
    if (!selectedId) {
        showError('请选择一个分区');
        return;
    }
    
    const selectedZone = biliZones.find(zone => zone.id === selectedId);
    if (!selectedZone) {
        showError('选择的分区无效');
        return;
    }
    
    // 禁用按钮并修改文本
    collectVideoDataBtn.disabled = true;
    collectVideoDataBtn.textContent = '正在收集视频数据中';
    
    // 显示提交的数据
    showResult('正在提交数据...');
    
    // 向后端提交数据
    fetch('/api/bilibili/video/batch-get-video-data?pidV2=' + selectedId, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        showResult('提交成功！\n' + JSON.stringify(data, null, 2));
        // 延迟2秒后开始定时查询爬虫状态，确保爬虫已经开始执行
        setTimeout(() => {
            startCrawlerStatusCheck();
        }, 2000);
    })
    .catch(error => {
        showError('提交失败：' + error.message);
        // 恢复按钮状态
        collectVideoDataBtn.disabled = false;
        collectVideoDataBtn.textContent = '保留分区视频参数（录入tag）';
    });
}
