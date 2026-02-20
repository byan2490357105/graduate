// B站分区数据
const biliZones = [
    { name: "生活", id: "160" },
    { name: "游戏", id: "4" },
    { name: "娱乐", id: "5" },
    { name: "知识", id: "36" },
    { name: "影视", id: "181" },
    { name: "音乐", id: "3" },
    { name: "动画", id: "1" },//动画有点奇怪
    { name: "时尚", id: "155" },//时尚没找到
    { name: "美食", id: "211" },
    { name: "汽车", id: "223" },
    { name: "运动", id: "234" },//对不上
    { name: "科技", id: "188" },//对不上
    { name: "动物圈", id: "217" },
    { name: "舞蹈", id: "129" },
    { name: "国创", id: "167" },//从这里开始，貌似从https://api.bilibili.com/x/web-interface/region/feed/rcmd抓不到保
    { name: "鬼畜", id: "119" },
];

// B站旧分区ID映射（用于查询数据库中的旧ID）
// 旧分区ID通常是1000-1100之间的数字，新版分区ID是较小的数字
const oldBiliZones = {
    "160": 1009,  // 生活
    "4": 1008,    // 游戏
    "5": 1002,    // 娱乐
    "36": 1010,   // 知识
    "181": 1001,  // 影视
    "3": 1003,    // 音乐
    "1": 1005,    // 动画，好像有点奇怪
    "155": 1015,  // 时尚
    "211": 1020,  // 美食
    "223": 1013,  // 汽车
    "234": 1018,  // 运动
    "188": 1019,  // 科技
    "217": 1024,  // 动物圈
    "129": 1004,  // 舞蹈
    "167": 1022,  // 国创，没找到
    "119": 1007,  // 鬼畜（旧ID为1007）
};

// ECharts实例
let chart = null;
let countChart = null;

// 页面加载完成后初始化
window.onload = function() {
    initZoneCheckboxes();
    initChart();
    initCountChart();
    refreshData();
    refreshCountData();
    
    // 监听数据类型下拉框变化
    document.getElementById('dataType').addEventListener('change', function() {
        refreshData();
    });
};

// 初始化分区复选框
function initZoneCheckboxes() {
    const zoneCheckboxesDiv = document.getElementById('zoneCheckboxes');
    
    // 清空现有内容
    zoneCheckboxesDiv.innerHTML = '';
    
    // 为每个分区创建复选框
    biliZones.forEach(zone => {
        const label = document.createElement('label');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.value = zone.id;
        checkbox.checked = true; // 默认全选
        checkbox.id = 'zone_' + zone.id;
        
        // 监听复选框变化
        checkbox.addEventListener('change', function() {
            refreshData();
            refreshCountData();
        });
        
        label.appendChild(checkbox);
        label.appendChild(document.createTextNode(zone.name));
        zoneCheckboxesDiv.appendChild(label);
    });
}

// 初始化ECharts图表
function initChart() {
    const chartDom = document.getElementById('chart');
    chart = echarts.init(chartDom);
    
    const option = {
        title: {
            text: 'B站分区数据统计',
            left: 'center'
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: {
                type: 'shadow'
            }
        },
        xAxis: {
            type: 'category',
            data: [],
            axisLabel: {
                interval: 0,
                rotate: 30
            }
        },
        yAxis: {
            type: 'value',
            name: ''
        },
        series: [{
            name: '',
            type: 'bar',
            data: [],
            itemStyle: {
                color: '#00a1d6'
            }
        }]
    };
    
    chart.setOption(option);
    
    // 监听窗口大小变化，自适应图表
    window.addEventListener('resize', function() {
        chart.resize();
    });
}

// 初始化数据条数图表
function initCountChart() {
    const chartDom = document.getElementById('countChart');
    countChart = echarts.init(chartDom);
    
    const option = {
        title: {
            text: '数据库内数据条数',
            left: 'center'
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: {
                type: 'shadow'
            }
        },
        xAxis: {
            type: 'category',
            data: [],
            axisLabel: {
                interval: 0,
                rotate: 30
            }
        },
        yAxis: {
            type: 'value',
            name: '数据条数'
        },
        series: [{
            name: '数据条数',
            type: 'bar',
            data: [],
            itemStyle: {
                color: '#52c41a'
            }
        }]
    };
    
    countChart.setOption(option);
    
    // 监听窗口大小变化，自适应图表
    window.addEventListener('resize', function() {
        countChart.resize();
    });
}

// 刷新数据
function refreshData() {
    const refreshBtn = document.getElementById('refreshBtn');
    const dataTypeSelect = document.getElementById('dataType');
    const dataType = dataTypeSelect.value;
    
    // 获取选中的分区
    const selectedZoneIds = [];
    biliZones.forEach(zone => {
        const checkbox = document.getElementById('zone_' + zone.id);
        if (checkbox && checkbox.checked) {
            const oldPidV2 = oldBiliZones[zone.id];
            if (oldPidV2) {
                selectedZoneIds.push(oldPidV2);
            }
        }
    });
    
    if (selectedZoneIds.length === 0) {
        showError('请至少选择一个分区');
        updateChart([], [], '');
        return;
    }
    
    // 禁用刷新按钮
    refreshBtn.disabled = true;
    refreshBtn.textContent = '刷新中...';
    
    // 向后端发送查询请求
    fetch('/api/bilibili/regiondata/query-all-statistics', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            pidV2List: selectedZoneIds
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.code === 200) {
            const statisticsList = data.data;
            if (statisticsList && statisticsList.length > 0) {
                // 构建图表数据
                const zoneNames = [];
                const values = [];
                
                // 根据旧分区ID找到对应的分区名称
                statisticsList.forEach(statistics => {
                    const pidV2 = statistics.pidV2;
                    // 找到对应的分区名称
                    for (const [newId, oldId] of Object.entries(oldBiliZones)) {
                        if (oldId === pidV2) {
                            const zone = biliZones.find(z => z.id === newId);
                            if (zone) {
                                zoneNames.push(zone.name);
                                values.push(statistics[dataType]);
                            }
                            break;
                        }
                    }
                });
                
                // 根据数据类型设置Y轴名称
                let yAxisName = '';
                let seriesName = '';
                switch (dataType) {
                    case 'avgPlayCount':
                        yAxisName = '平均播放量';
                        seriesName = '平均播放量';
                        break;
                    case 'avgLikeCount':
                        yAxisName = '平均点赞数';
                        seriesName = '平均点赞数';
                        break;
                    case 'avgDanmukuCount':
                        yAxisName = '平均弹幕数';
                        seriesName = '平均弹幕数';
                        break;
                }
                
                updateChart(zoneNames, values, yAxisName, seriesName);
                hideError();
            } else {
                showError('未找到相关数据');
                updateChart([], [], '');
            }
        } else {
            showError('查询失败：' + data.msg);
            updateChart([], [], '');
        }
    })
    .catch(error => {
        showError('查询失败：' + error.message);
        updateChart([], [], '');
    })
    .finally(() => {
        // 恢复刷新按钮状态
        refreshBtn.disabled = false;
        refreshBtn.textContent = '刷新数据';
    });
}

// 刷新数据条数
function refreshCountData() {
    // 获取选中的分区
    const selectedZoneIds = [];
    biliZones.forEach(zone => {
        const checkbox = document.getElementById('zone_' + zone.id);
        if (checkbox && checkbox.checked) {
            const oldPidV2 = oldBiliZones[zone.id];
            if (oldPidV2) {
                selectedZoneIds.push(oldPidV2);
            }
        }
    });
    
    if (selectedZoneIds.length === 0) {
        updateCountChart([], []);
        return;
    }
    
    // 向后端发送查询请求
    fetch('/api/bilibili/regiondata/query-data-count', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            pidV2List: selectedZoneIds
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.code === 200) {
            const countList = data.data;
            if (countList && countList.length > 0) {
                // 构建图表数据
                const zoneNames = [];
                const counts = [];
                
                // 根据旧分区ID找到对应的分区名称
                countList.forEach(countData => {
                    const pidV2 = countData.pidV2;
                    // 找到对应的分区名称
                    for (const [newId, oldId] of Object.entries(oldBiliZones)) {
                        if (oldId === pidV2) {
                            const zone = biliZones.find(z => z.id === newId);
                            if (zone) {
                                zoneNames.push(zone.name);
                                counts.push(countData.count);
                            }
                            break;
                        }
                    }
                });
                
                updateCountChart(zoneNames, counts);
            } else {
                updateCountChart([], []);
            }
        } else {
            updateCountChart([], []);
        }
    })
    .catch(error => {
        console.error('查询数据条数失败：', error);
        updateCountChart([], []);
    });
}

// 更新图表
function updateChart(zoneNames, values, yAxisName, seriesName) {
    const option = {
        title: {
            text: 'B站分区数据统计',
            left: 'center'
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: {
                type: 'shadow'
            }
        },
        xAxis: {
            type: 'category',
            data: zoneNames,
            axisLabel: {
                interval: 0,
                rotate: 30
            }
        },
        yAxis: {
            type: 'value',
            name: yAxisName
        },
        series: [{
            name: seriesName,
            type: 'bar',
            data: values,
            itemStyle: {
                color: '#00a1d6'
            }
        }]
    };
    
    chart.setOption(option);
}

// 更新数据条数图表
function updateCountChart(zoneNames, counts) {
    const option = {
        title: {
            text: '数据库内数据条数',
            left: 'center'
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: {
                type: 'shadow'
            }
        },
        xAxis: {
            type: 'category',
            data: zoneNames,
            axisLabel: {
                interval: 0,
                rotate: 30
            }
        },
        yAxis: {
            type: 'value',
            name: '数据条数'
        },
        series: [{
            name: '数据条数',
            type: 'bar',
            data: counts,
            itemStyle: {
                color: '#52c41a'
            }
        }]
    };
    
    countChart.setOption(option);
}

// 显示错误信息
function showError(message) {
    const errorDiv = document.getElementById('error');
    errorDiv.textContent = message;
    errorDiv.style.display = 'block';
}

// 隐藏错误信息
function hideError() {
    const errorDiv = document.getElementById('error');
    errorDiv.style.display = 'none';
}
