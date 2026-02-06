// B站分区数据
const biliZones = [
    { name: "生活", id: "160" },
    { name: "游戏", id: "4" },
    { name: "娱乐", id: "5" },
    { name: "知识", id: "36" },
    { name: "影视", id: "181" },
    { name: "音乐", id: "3" },
    { name: "动画", id: "1" },
    { name: "时尚", id: "155" },
    { name: "美食", id: "211" },
    { name: "汽车", id: "223" },
    { name: "运动", id: "234" },
    { name: "科技", id: "188" },
    { name: "动物圈", id: "217" },
    { name: "舞蹈", id: "129" },
    { name: "国创", id: "167" },
    { name: "鬼畜", id: "119" },
    { name: "纪录片", id: "177" },
    { name: "番剧", id: "13" },
    { name: "电视剧", id: "11" },
    { name: "电影", id: "23" }
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
    const durationSelect = document.getElementById('duration');
    const duration = durationSelect.value;
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
    
    const selectedZone = biliZones.find(zone => zone.id === selectedId);
    if (!selectedZone) {
        showError('选择的分区无效');
        return;
    }
    
    const zoneData = {
        "分区名": selectedZone.name,
        "ID": selectedZone.id,
        "duration": duration
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
